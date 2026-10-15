#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include "../libretro/core_bridge.h"
#include "../video/gl_renderer.h"
#include "../audio/audio_stream.h"
#include "../input/input_state.h"

extern "C" {

JNIEXPORT void JNICALL
Java_com_aladdin_geniebox_emulator_NativeEmulator_nativeInit(
        JNIEnv* env, jobject thiz,
        jstring systemDir, jstring saveDir, jstring coreAssetsDir) {
    const char* sDir = env->GetStringUTFChars(systemDir, nullptr);
    const char* svDir = env->GetStringUTFChars(saveDir, nullptr);
    const char* caDir = env->GetStringUTFChars(coreAssetsDir, nullptr);

    CoreBridge::getInstance().setSystemDirectory(sDir ? sDir : "");
    CoreBridge::getInstance().setSaveDirectory(svDir ? svDir : "");
    CoreBridge::getInstance().setCoreAssetsDirectory(caDir ? caDir : "");

    if (sDir) env->ReleaseStringUTFChars(systemDir, sDir);
    if (svDir) env->ReleaseStringUTFChars(saveDir, svDir);
    if (caDir) env->ReleaseStringUTFChars(coreAssetsDir, caDir);
}

JNIEXPORT jboolean JNICALL
Java_com_aladdin_geniebox_emulator_NativeEmulator_nativeLoadCore(
        JNIEnv* env, jobject thiz, jstring corePath) {
    const char* cPath = env->GetStringUTFChars(corePath, nullptr);
    bool ok = CoreBridge::getInstance().loadCore(cPath ? cPath : "");
    if (cPath) env->ReleaseStringUTFChars(corePath, cPath);
    return static_cast<jboolean>(ok);
}

JNIEXPORT void JNICALL
Java_com_aladdin_geniebox_emulator_NativeEmulator_nativeUnloadCore(
        JNIEnv* env, jobject thiz) {
    CoreBridge::getInstance().unloadCore();
}

JNIEXPORT jboolean JNICALL
Java_com_aladdin_geniebox_emulator_NativeEmulator_nativeLoadGame(
        JNIEnv* env, jobject thiz, jstring romPath, jbyteArray romBuffer) {
    const char* rPath = env->GetStringUTFChars(romPath, nullptr);
    std::vector<uint8_t> buffer;

    if (romBuffer != nullptr) {
        jsize len = env->GetArrayLength(romBuffer);
        if (len > 0) {
            buffer.resize(len);
            env->GetByteArrayRegion(romBuffer, 0, len, reinterpret_cast<jbyte*>(buffer.data()));
        }
    }

    bool ok = CoreBridge::getInstance().loadGame(rPath ? rPath : "", buffer);
    if (rPath) env->ReleaseStringUTFChars(romPath, rPath);
    return static_cast<jboolean>(ok);
}

JNIEXPORT void JNICALL
Java_com_aladdin_geniebox_emulator_NativeEmulator_nativeUnloadGame(
        JNIEnv* env, jobject thiz) {
    CoreBridge::getInstance().unloadGame();
}

JNIEXPORT void JNICALL
Java_com_aladdin_geniebox_emulator_NativeEmulator_nativeRunFrame(
        JNIEnv* env, jobject thiz) {
    // 確保首幀 retro_run 之前 EGL Context 與硬體核心 context_reset 就緒 (R3-01)
    if (GLRenderer::getInstance().isHwRender()) {
        if (!GLRenderer::getInstance().ensureContextReady()) {
            // Surface 尚未就緒時暫停執行硬體核心，避免無 EGL Context 時呼叫 GLES 指令崩潰
            return;
        }
    } else {
        GLRenderer::getInstance().ensureContextReady();
    }

    CoreBridge::getInstance().runFrame();
    GLRenderer::getInstance().render();
}

JNIEXPORT void JNICALL
Java_com_aladdin_geniebox_emulator_NativeEmulator_nativeReset(
        JNIEnv* env, jobject thiz) {
    CoreBridge::getInstance().reset();
}

JNIEXPORT jbyteArray JNICALL
Java_com_aladdin_geniebox_emulator_NativeEmulator_nativeSerializeState(
        JNIEnv* env, jobject thiz) {
    std::vector<uint8_t> state;
    if (!CoreBridge::getInstance().serializeState(state) || state.empty()) {
        return nullptr;
    }
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(state.size()));
    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(state.size()),
                            reinterpret_cast<const jbyte*>(state.data()));
    return arr;
}

JNIEXPORT jboolean JNICALL
Java_com_aladdin_geniebox_emulator_NativeEmulator_nativeUnserializeState(
        JNIEnv* env, jobject thiz, jbyteArray stateData) {
    if (stateData == nullptr) return JNI_FALSE;
    jsize len = env->GetArrayLength(stateData);
    if (len <= 0) return JNI_FALSE;

    std::vector<uint8_t> state(len);
    env->GetByteArrayRegion(stateData, 0, len, reinterpret_cast<jbyte*>(state.data()));
    bool ok = CoreBridge::getInstance().unserializeState(state);
    return static_cast<jboolean>(ok);
}

JNIEXPORT jbyteArray JNICALL
Java_com_aladdin_geniebox_emulator_NativeEmulator_nativeGetSramData(
        JNIEnv* env, jobject thiz) {
    std::vector<uint8_t> sram;
    if (!CoreBridge::getInstance().getSramData(sram) || sram.empty()) {
        return nullptr;
    }
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(sram.size()));
    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(sram.size()),
                            reinterpret_cast<const jbyte*>(sram.data()));
    return arr;
}

JNIEXPORT jboolean JNICALL
Java_com_aladdin_geniebox_emulator_NativeEmulator_nativeSetSramData(
        JNIEnv* env, jobject thiz, jbyteArray sramData) {
    if (sramData == nullptr) return JNI_FALSE;
    jsize len = env->GetArrayLength(sramData);
    if (len <= 0) return JNI_FALSE;

    std::vector<uint8_t> sram(len);
    env->GetByteArrayRegion(sramData, 0, len, reinterpret_cast<jbyte*>(sram.data()));
    bool ok = CoreBridge::getInstance().setSramData(sram);
    return static_cast<jboolean>(ok);
}

JNIEXPORT void JNICALL
Java_com_aladdin_geniebox_emulator_NativeEmulator_nativeSetSurface(
        JNIEnv* env, jobject thiz, jobject surface) {
    if (surface == nullptr) {
        GLRenderer::getInstance().setNativeWindow(nullptr);
        return;
    }
    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    GLRenderer::getInstance().setNativeWindow(window);
}

JNIEXPORT void JNICALL
Java_com_aladdin_geniebox_emulator_NativeEmulator_nativeSurfaceDestroyed(
        JNIEnv* env, jobject thiz) {
    GLRenderer::getInstance().setNativeWindow(nullptr);
}

JNIEXPORT jboolean JNICALL
Java_com_aladdin_geniebox_emulator_NativeEmulator_nativeSetControllerPortDevice(
        JNIEnv* env, jobject thiz, jint port, jint device) {
    return static_cast<jboolean>(CoreBridge::getInstance().setControllerPortDevice(
            static_cast<unsigned>(port), static_cast<unsigned>(device)));
}

JNIEXPORT jboolean JNICALL
Java_com_aladdin_geniebox_emulator_NativeEmulator_nativeSetDiskIndex(
        JNIEnv* env, jobject thiz, jint index) {
    return static_cast<jboolean>(CoreBridge::getInstance().setDiskIndex(
            static_cast<unsigned>(index)));
}

JNIEXPORT jint JNICALL
Java_com_aladdin_geniebox_emulator_NativeEmulator_nativeGetDiskCount(
        JNIEnv* env, jobject thiz) {
    return static_cast<jint>(CoreBridge::getInstance().getDiskCount());
}

JNIEXPORT void JNICALL
Java_com_aladdin_geniebox_emulator_NativeEmulator_nativeSetFilterMode(
        JNIEnv* env, jobject thiz, jboolean bilinear) {
    GLRenderer::getInstance().setFilterMode(bilinear);
}

JNIEXPORT void JNICALL
Java_com_aladdin_geniebox_emulator_NativeEmulator_nativeSetAspectRatioMode(
        JNIEnv* env, jobject thiz, jint mode) {
    GLRenderer::getInstance().setAspectRatioMode(static_cast<AspectRatioMode>(mode));
}

JNIEXPORT void JNICALL
Java_com_aladdin_geniebox_emulator_NativeEmulator_nativeUpdateInput(
        JNIEnv* env, jobject thiz,
        jint port, jint buttons, jshort lx, jshort ly, jshort rx, jshort ry, jboolean connected) {
    InputManagerNative::getInstance().updatePlayerInput(
            static_cast<unsigned>(port),
            static_cast<uint16_t>(buttons),
            lx, ly, rx, ry, connected);
}

JNIEXPORT void JNICALL
Java_com_aladdin_geniebox_emulator_NativeEmulator_nativeReleaseAllInputs(
        JNIEnv* env, jobject thiz) {
    InputManagerNative::getInstance().releaseAllButtons();
}

JNIEXPORT jint JNICALL
Java_com_aladdin_geniebox_emulator_NativeEmulator_nativeReadAudioSamples(
        JNIEnv* env, jobject thiz, jshortArray outBuffer) {
    if (outBuffer == nullptr) return 0;
    jsize len = env->GetArrayLength(outBuffer);
    if (len <= 0) return 0;

    std::vector<int16_t> temp(len);
    size_t readCount = AudioRingBuffer::getInstance().read(temp.data(), len);
    if (readCount > 0) {
        env->SetShortArrayRegion(outBuffer, 0, static_cast<jsize>(readCount),
                                 reinterpret_cast<const jshort*>(temp.data()));
    }
    return static_cast<jint>(readCount);
}

JNIEXPORT jfloat JNICALL
Java_com_aladdin_geniebox_emulator_NativeEmulator_nativeGetTargetFps(
        JNIEnv* env, jobject thiz) {
    return GLRenderer::getInstance().getTargetFps();
}

JNIEXPORT jdouble JNICALL
Java_com_aladdin_geniebox_emulator_NativeEmulator_nativeGetSampleRate(
        JNIEnv* env, jobject thiz) {
    return AudioRingBuffer::getInstance().getSampleRate();
}

JNIEXPORT jlong JNICALL
Java_com_aladdin_geniebox_emulator_NativeEmulator_nativeGetAudioUnderruns(
        JNIEnv* env, jobject thiz) {
    return static_cast<jlong>(AudioRingBuffer::getInstance().getUnderruns());
}

} // extern "C"
