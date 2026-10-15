#include "core_bridge.h"
#include "../video/gl_renderer.h"
#include "../audio/audio_stream.h"
#include "../input/input_state.h"
#include <dlfcn.h>
#include <android/log.h>
#include <cstring>

#define LOG_TAG "GenieCoreBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static void coreLogPrintf(enum retro_log_level level, const char *fmt, ...) {
    va_list va;
    va_start(va, fmt);
    int priority = ANDROID_LOG_INFO;
    switch (level) {
        case RETRO_LOG_DEBUG: priority = ANDROID_LOG_DEBUG; break;
        case RETRO_LOG_INFO:  priority = ANDROID_LOG_INFO; break;
        case RETRO_LOG_WARN:  priority = ANDROID_LOG_WARN; break;
        case RETRO_LOG_ERROR: priority = ANDROID_LOG_ERROR; break;
        default: break;
    }
    __android_log_vprint(priority, "LibretroCore", fmt, va);
    va_end(va);
}

CoreBridge::CoreBridge() = default;

CoreBridge::~CoreBridge() {
    unloadGame();
    unloadCore();
}

bool CoreBridge::loadCore(const std::string& corePath) {
    std::lock_guard<std::mutex> lock(mSessionMutex);
    unloadCore();

    mCoreHandle = dlopen(corePath.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (!mCoreHandle) {
        LOGE("Failed to dlopen core library: %s, error: %s", corePath.c_str(), dlerror());
        return false;
    }

#define LOAD_SYM(name) \
    m_##name = reinterpret_cast<name##_t>(dlsym(mCoreHandle, #name)); \
    if (!m_##name) { \
        LOGE("Missing required symbol: %s", #name); \
        unloadCore(); \
        return false; \
    }

    LOAD_SYM(retro_init)
    LOAD_SYM(retro_deinit)
    LOAD_SYM(retro_api_version)
    LOAD_SYM(retro_get_system_info)
    LOAD_SYM(retro_get_system_av_info)
    LOAD_SYM(retro_set_environment)
    LOAD_SYM(retro_set_video_refresh)
    LOAD_SYM(retro_set_audio_sample)
    LOAD_SYM(retro_set_audio_sample_batch)
    LOAD_SYM(retro_set_input_poll)
    LOAD_SYM(retro_set_input_state)
    LOAD_SYM(retro_set_controller_port_device)
    LOAD_SYM(retro_reset)
    LOAD_SYM(retro_run)
    LOAD_SYM(retro_serialize_size)
    LOAD_SYM(retro_serialize)
    LOAD_SYM(retro_unserialize)
    LOAD_SYM(retro_load_game)
    LOAD_SYM(retro_unload_game)
    LOAD_SYM(retro_get_memory_data)
    LOAD_SYM(retro_get_memory_size)
#undef LOAD_SYM

    unsigned apiVer = m_retro_api_version();
    LOGI("Libretro core loaded: %s (API v%u)", corePath.c_str(), apiVer);

    m_retro_set_environment(envCallback);
    m_retro_set_video_refresh(videoCallback);
    m_retro_set_audio_sample(audioSampleCallback);
    m_retro_set_audio_sample_batch(audioSampleBatchCallback);
    m_retro_set_input_poll(inputPollCallback);
    m_retro_set_input_state(inputStateCallback);

    m_retro_init();

    struct retro_system_info sysInfo{};
    m_retro_get_system_info(&sysInfo);
    mCapabilities.libraryName = sysInfo.library_name ? sysInfo.library_name : "Unknown";
    mCapabilities.libraryVersion = sysInfo.library_version ? sysInfo.library_version : "0.0";
    mCapabilities.validExtensions = sysInfo.valid_extensions ? sysInfo.valid_extensions : "";
    mCapabilities.needFullPath = sysInfo.need_fullpath;

    LOGI("Core Info: name=%s, ver=%s, ext=%s, need_fullpath=%d",
         mCapabilities.libraryName.c_str(), mCapabilities.libraryVersion.c_str(),
         mCapabilities.validExtensions.c_str(), mCapabilities.needFullPath);

    return true;
}

void CoreBridge::unloadCore() {
    if (mCoreHandle) {
        if (mGameLoaded) {
            unloadGame();
        }
        if (m_retro_deinit) {
            m_retro_deinit();
        }
        dlclose(mCoreHandle);
        mCoreHandle = nullptr;
    }
}

bool CoreBridge::loadGame(const std::string& romPath, const std::vector<uint8_t>& romBuffer) {
    std::lock_guard<std::mutex> lock(mSessionMutex);
    if (!mCoreHandle || !m_retro_load_game) return false;

    struct retro_game_info gameInfo{};
    gameInfo.path = romPath.c_str();

    if (!mCapabilities.needFullPath && !romBuffer.empty()) {
        gameInfo.data = romBuffer.data();
        gameInfo.size = romBuffer.size();
    } else {
        gameInfo.data = nullptr;
        gameInfo.size = 0;
    }

    if (!m_retro_load_game(&gameInfo)) {
        LOGE("retro_load_game failed for path: %s", romPath.c_str());
        return false;
    }

    struct retro_system_av_info avInfo{};
    m_retro_get_system_av_info(&avInfo);

    mCapabilities.fps = (avInfo.timing.fps > 0.0) ? avInfo.timing.fps : 60.0;
    mCapabilities.sampleRate = (avInfo.timing.sample_rate > 0.0) ? avInfo.timing.sample_rate : 44100.0;
    mCapabilities.baseWidth = avInfo.geometry.base_width;
    mCapabilities.baseHeight = avInfo.geometry.base_height;
    mCapabilities.aspectRatio = (avInfo.geometry.aspect_ratio > 0.0f) ? avInfo.geometry.aspect_ratio :
                                ((float)avInfo.geometry.base_width / (float)avInfo.geometry.base_height);

    GLRenderer::getInstance().setGeometry(avInfo.geometry.base_width, avInfo.geometry.base_height,
                                          avInfo.geometry.max_width, avInfo.geometry.max_height,
                                          mCapabilities.aspectRatio);
    GLRenderer::getInstance().setTargetFps(mCapabilities.fps);

    AudioRingBuffer::getInstance().init(4096, mCapabilities.sampleRate);

    mCapabilities.stateSize = m_retro_serialize_size();
    mCapabilities.supportsSaveState = (mCapabilities.stateSize > 0);

    mGameLoaded = true;
    LOGI("Game loaded successfully. FPS=%.2f, SampleRate=%.1f, StateSize=%zu",
         mCapabilities.fps, mCapabilities.sampleRate, mCapabilities.stateSize);
    return true;
}

void CoreBridge::unloadGame() {
    std::lock_guard<std::mutex> lock(mSessionMutex);
    if (mGameLoaded) {
        if (m_retro_unload_game) {
            m_retro_unload_game();
        }
        mGameLoaded = false;
        mHasDiskControl = false;
        mHasDiskControlExt = false;
        memset(&mDiskControl, 0, sizeof(mDiskControl));
        memset(&mDiskControlExt, 0, sizeof(mDiskControlExt));
        GLRenderer::getInstance().resetCoreState();
    }
}

void CoreBridge::runFrame() {
    std::lock_guard<std::mutex> lock(mSessionMutex);
    if (mGameLoaded && m_retro_run) {
        m_retro_run();
    }
}

void CoreBridge::reset() {
    std::lock_guard<std::mutex> lock(mSessionMutex);
    if (mGameLoaded && m_retro_reset) {
        m_retro_reset();
    }
}

bool CoreBridge::serializeState(std::vector<uint8_t>& outBuffer) {
    std::lock_guard<std::mutex> lock(mSessionMutex);
    if (!mGameLoaded || !m_retro_serialize || !m_retro_serialize_size) return false;
    size_t size = m_retro_serialize_size();
    if (size == 0) return false;

    outBuffer.resize(size);
    bool ok = m_retro_serialize(outBuffer.data(), size);
    if (!ok) {
        outBuffer.clear();
        LOGE("retro_serialize failed");
    }
    return ok;
}

bool CoreBridge::unserializeState(const std::vector<uint8_t>& inBuffer) {
    std::lock_guard<std::mutex> lock(mSessionMutex);
    if (!mGameLoaded || !m_retro_unserialize || inBuffer.empty()) return false;
    return m_retro_unserialize(inBuffer.data(), inBuffer.size());
}

bool CoreBridge::getSramData(std::vector<uint8_t>& outSram) {
    std::lock_guard<std::mutex> lock(mSessionMutex);
    if (!mGameLoaded || !m_retro_get_memory_data || !m_retro_get_memory_size) return false;
    size_t size = m_retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    void* data = m_retro_get_memory_data(RETRO_MEMORY_SAVE_RAM);
    if (size == 0 || !data) return false;

    outSram.resize(size);
    memcpy(outSram.data(), data, size);
    return true;
}

bool CoreBridge::setSramData(const std::vector<uint8_t>& inSram) {
    std::lock_guard<std::mutex> lock(mSessionMutex);
    if (!mGameLoaded || !m_retro_get_memory_data || !m_retro_get_memory_size) return false;
    size_t size = m_retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    void* data = m_retro_get_memory_data(RETRO_MEMORY_SAVE_RAM);
    if (size == 0 || !data || inSram.size() > size) return false;

    memcpy(data, inSram.data(), inSram.size());
    return true;
}

bool CoreBridge::envCallback(unsigned cmd, void* data) {
    auto& cb = CoreBridge::getInstance();
    switch (cmd) {
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
            if (!data) return false;
            auto fmt = *static_cast<const enum retro_pixel_format*>(data);
            if (fmt == RETRO_PIXEL_FORMAT_RGB565 ||
                fmt == RETRO_PIXEL_FORMAT_XRGB8888 ||
                fmt == RETRO_PIXEL_FORMAT_0RGB1555) {
                GLRenderer::getInstance().setPixelFormat(fmt);
                return true;
            }
            return false;
        }
        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY: {
            if (!data) return false;
            *static_cast<const char**>(data) = cb.mSystemDir.c_str();
            return true;
        }
        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY: {
            if (!data) return false;
            *static_cast<const char**>(data) = cb.mSaveDir.c_str();
            return true;
        }
        case RETRO_ENVIRONMENT_GET_CORE_ASSETS_DIRECTORY: {
            if (!data) return false;
            *static_cast<const char**>(data) = cb.mCoreAssetsDir.c_str();
            return true;
        }
        case RETRO_ENVIRONMENT_GET_CAN_DUPE: {
            if (!data) return false;
            *static_cast<bool*>(data) = true;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE: {
            if (!data) return false;
            auto* cbStruct = static_cast<struct retro_log_callback*>(data);
            cbStruct->log = coreLogPrintf;
            return true;
        }
        case RETRO_ENVIRONMENT_SET_HW_RENDER: {
            if (!data) return false;
            auto* hw = static_cast<struct retro_hw_render_callback*>(data);
            hw->get_current_framebuffer = []() -> uintptr_t {
                return GLRenderer::getInstance().getCurrentFramebuffer();
            };
            hw->get_proc_address = (retro_hw_get_proc_address_t)eglGetProcAddress;
            GLRenderer::getInstance().setHwRenderCallback(*hw);
            return true;
        }
        case RETRO_ENVIRONMENT_SET_GEOMETRY: {
            if (!data) return false;
            auto* geom = static_cast<const struct retro_game_geometry*>(data);
            GLRenderer::getInstance().setGeometry(geom->base_width, geom->base_height,
                                                  geom->max_width, geom->max_height,
                                                  geom->aspect_ratio);
            return true;
        }
        case RETRO_ENVIRONMENT_GET_VARIABLE: {
            if (!data) return false;
            auto* var = static_cast<struct retro_variable*>(data);
            if (!var->key) return false;

            // 支援各平台核心之四人 Multitap / Four Score 擴充選項
            if (strcmp(var->key, "pcsx_rearmed_multitap") == 0) {
                var->value = "port 1";
                return true;
            }
            if (strcmp(var->key, "snes9x_multitap") == 0) {
                var->value = "port 2";
                return true;
            }
            if (strcmp(var->key, "fceumm_fourscore") == 0) {
                var->value = "enabled";
                return true;
            }
            return false;
        }
        case RETRO_ENVIRONMENT_SET_CONTROLLER_INFO:
        case RETRO_ENVIRONMENT_SET_VARIABLES:
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS:
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_INTL: {
            return true;
        }
        case RETRO_ENVIRONMENT_SET_DISK_CONTROL_INTERFACE: {
            if (!data) return false;
            cb.mDiskControl = *static_cast<const struct retro_disk_control_callback*>(data);
            cb.mHasDiskControl = true;
            LOGI("Core registered disk control interface");
            return true;
        }
        case RETRO_ENVIRONMENT_SET_DISK_CONTROL_EXT_INTERFACE: {
            if (!data) return false;
            cb.mDiskControlExt = *static_cast<const struct retro_disk_control_ext_callback*>(data);
            cb.mHasDiskControlExt = true;
            LOGI("Core registered disk control ext interface");
            return true;
        }
        case RETRO_ENVIRONMENT_GET_INPUT_BITMASKS: {
            if (!data) return false;
            *static_cast<bool*>(data) = true;
            return true;
        }
        default:
            return false;
    }
}

void CoreBridge::videoCallback(const void* data, unsigned width, unsigned height, size_t pitch) {
    if (!data) return;
    if (data == RETRO_HW_FRAME_BUFFER_VALID) {
        // 硬體渲染核心通知影格就緒，絕不送入 CPU 軟體像素 memcpy 迴圈
        GLRenderer::getInstance().notifyHwFrameReady(width, height);
        return;
    }
    GLRenderer::getInstance().updateSoftwareFrame(data, width, height, pitch);
}

void CoreBridge::audioSampleCallback(int16_t left, int16_t right) {
    AudioRingBuffer::getInstance().writeStereoSamples(left, right);
}

size_t CoreBridge::audioSampleBatchCallback(const int16_t* data, size_t frames) {
    return AudioRingBuffer::getInstance().writeBatch(data, frames);
}

void CoreBridge::inputPollCallback() {
    InputManagerNative::getInstance().pollInputs();
}

int16_t CoreBridge::inputStateCallback(unsigned port, unsigned device, unsigned index, unsigned id) {
    return InputManagerNative::getInstance().getInputState(port, device, index, id);
}

bool CoreBridge::setControllerPortDevice(unsigned port, unsigned device) {
    std::lock_guard<std::mutex> lock(mSessionMutex);
    if (!mCoreHandle || !m_retro_set_controller_port_device) return false;
    m_retro_set_controller_port_device(port, device);
    return true;
}

bool CoreBridge::setDiskIndex(unsigned index) {
    std::lock_guard<std::mutex> lock(mSessionMutex);
    if (mHasDiskControlExt) {
        if (mDiskControlExt.get_num_images) {
            unsigned total = mDiskControlExt.get_num_images();
            if (index >= total) return false;
        }
        if (mDiskControlExt.set_eject_state) mDiskControlExt.set_eject_state(true);
        bool ok = mDiskControlExt.set_image_index ? mDiskControlExt.set_image_index(index) : false;
        if (mDiskControlExt.set_eject_state) mDiskControlExt.set_eject_state(false);
        LOGI("Disk switched via Ext interface to index %u (success=%d)", index, ok);
        return ok;
    } else if (mHasDiskControl) {
        if (mDiskControl.get_num_images) {
            unsigned total = mDiskControl.get_num_images();
            if (index >= total) return false;
        }
        if (mDiskControl.set_eject_state) mDiskControl.set_eject_state(true);
        bool ok = mDiskControl.set_image_index ? mDiskControl.set_image_index(index) : false;
        if (mDiskControl.set_eject_state) mDiskControl.set_eject_state(false);
        LOGI("Disk switched via Standard interface to index %u (success=%d)", index, ok);
        return ok;
    }
    LOGW("setDiskIndex called but core did not register disk control interface");
    return false;
}

unsigned CoreBridge::getDiskCount() const {
    if (mHasDiskControlExt && mDiskControlExt.get_num_images) {
        return mDiskControlExt.get_num_images();
    } else if (mHasDiskControl && mDiskControl.get_num_images) {
        return mDiskControl.get_num_images();
    }
    return 0;
}
