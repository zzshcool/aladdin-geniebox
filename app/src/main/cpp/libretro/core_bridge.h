#ifndef CORE_BRIDGE_H__
#define CORE_BRIDGE_H__

#include <string>
#include <vector>
#include <mutex>
#include <memory>
#include "libretro.h"

struct CoreCapabilities {
    std::string libraryName;
    std::string libraryVersion;
    std::string validExtensions;
    bool needFullPath = false;
    bool supportsSaveState = false;
    size_t stateSize = 0;
    double fps = 60.0;
    double sampleRate = 44100.0;
    unsigned baseWidth = 320;
    unsigned baseHeight = 240;
    float aspectRatio = 4.0f / 3.0f;
};

class CoreBridge {
public:
    static CoreBridge& getInstance() {
        static CoreBridge instance;
        return instance;
    }

    bool loadCore(const std::string& corePath);
    void unloadCore();
    bool isCoreLoaded() const { return mCoreHandle != nullptr; }

    bool loadGame(const std::string& romPath, const std::vector<uint8_t>& romBuffer);
    void unloadGame();
    bool isGameLoaded() const { return mGameLoaded; }

    void runFrame();
    void reset();

    bool serializeState(std::vector<uint8_t>& outBuffer);
    bool unserializeState(const std::vector<uint8_t>& inBuffer);

    bool getSramData(std::vector<uint8_t>& outSram);
    bool setSramData(const std::vector<uint8_t>& inSram);

    // Directories
    void setSystemDirectory(const std::string& path) { mSystemDir = path; }
    void setSaveDirectory(const std::string& path) { mSaveDir = path; }
    void setCoreAssetsDirectory(const std::string& path) { mCoreAssetsDir = path; }

    const CoreCapabilities& getCapabilities() const { return mCapabilities; }

    // Controller port configuration (Multitap / Four Score)
    bool setControllerPortDevice(unsigned port, unsigned device);

    // Disk control for PS1 multi-disc
    bool setDiskIndex(unsigned index);
    unsigned getDiskCount() const;

private:
    CoreBridge();
    ~CoreBridge();

    static bool envCallback(unsigned cmd, void* data);
    static void videoCallback(const void* data, unsigned width, unsigned height, size_t pitch);
    static void audioSampleCallback(int16_t left, int16_t right);
    static size_t audioSampleBatchCallback(const int16_t* data, size_t frames);
    static void inputPollCallback();
    static int16_t inputStateCallback(unsigned port, unsigned device, unsigned index, unsigned id);

    void* mCoreHandle = nullptr;
    bool mGameLoaded = false;
    std::mutex mSessionMutex;

    std::string mSystemDir;
    std::string mSaveDir;
    std::string mCoreAssetsDir;

    retro_disk_control_callback mDiskControl{};
    retro_disk_control_ext_callback mDiskControlExt{};
    bool mHasDiskControl = false;
    bool mHasDiskControlExt = false;

    CoreCapabilities mCapabilities;

    // Core symbol pointers
    retro_init_t m_retro_init = nullptr;
    retro_deinit_t m_retro_deinit = nullptr;
    retro_api_version_t m_retro_api_version = nullptr;
    retro_get_system_info_t m_retro_get_system_info = nullptr;
    retro_get_system_av_info_t m_retro_get_system_av_info = nullptr;
    retro_set_environment_t m_retro_set_environment = nullptr;
    retro_set_video_refresh_t m_retro_set_video_refresh = nullptr;
    retro_set_audio_sample_t m_retro_set_audio_sample = nullptr;
    retro_set_audio_sample_batch_t m_retro_set_audio_sample_batch = nullptr;
    retro_set_input_poll_t m_retro_set_input_poll = nullptr;
    retro_set_input_state_t m_retro_set_input_state = nullptr;
    retro_set_controller_port_device_t m_retro_set_controller_port_device = nullptr;
    retro_reset_t m_retro_reset = nullptr;
    retro_run_t m_retro_run = nullptr;
    retro_serialize_size_t m_retro_serialize_size = nullptr;
    retro_serialize_t m_retro_serialize = nullptr;
    retro_unserialize_t m_retro_unserialize = nullptr;
    retro_load_game_t m_retro_load_game = nullptr;
    retro_unload_game_t m_retro_unload_game = nullptr;
    retro_get_memory_data_t m_retro_get_memory_data = nullptr;
    retro_get_memory_size_t m_retro_get_memory_size = nullptr;
};

#endif
