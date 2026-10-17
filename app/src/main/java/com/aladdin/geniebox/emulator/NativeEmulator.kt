package com.aladdin.geniebox.emulator

import android.view.Surface

class NativeEmulator {

    companion object {
        init {
            try {
                System.loadLibrary("aladdin_emulator")
            } catch (e: UnsatisfiedLinkError) {
                e.printStackTrace()
            }
        }
    }

    external fun nativeInit(systemDir: String, saveDir: String, coreAssetsDir: String)
    external fun nativeLoadCore(corePath: String): Boolean
    external fun nativeUnloadCore()
    external fun nativeLoadGame(romPath: String, romBuffer: ByteArray?): Boolean
    external fun nativeUnloadGame()
    external fun nativeRunFrame(presentFrame: Boolean = true)
    external fun nativeReset()
    external fun nativeSetNesOverclocking(enabled: Boolean)
    external fun nativeSerializeState(): ByteArray?
    external fun nativeUnserializeState(stateData: ByteArray): Boolean
    external fun nativeGetSramData(): ByteArray?
    external fun nativeSetSramData(sramData: ByteArray): Boolean
    external fun nativeSetSurface(surface: Surface?)
    external fun nativeSurfaceDestroyed()
    external fun nativeSetFilterMode(bilinear: Boolean)
    external fun nativeSetAspectRatioMode(mode: Int)
    external fun nativeUpdateInput(port: Int, buttons: Int, lx: Short, ly: Short, rx: Short, ry: Short, connected: Boolean)
    external fun nativeSetControllerPortDevice(port: Int, device: Int): Boolean
    external fun nativeSetDiskIndex(index: Int): Boolean
    external fun nativeGetDiskCount(): Int
    external fun nativeReleaseAllInputs()
    external fun nativeReadAudioSamples(outBuffer: ShortArray): Int
    external fun nativeGetTargetFps(): Float
    external fun nativeGetSampleRate(): Double
    external fun nativeGetAudioUnderruns(): Long
}
