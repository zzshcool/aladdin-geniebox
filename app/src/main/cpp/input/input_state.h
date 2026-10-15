#ifndef INPUT_STATE_H__
#define INPUT_STATE_H__

#include <stdint.h>
#include <mutex>
#include <array>
#include "../libretro/libretro.h"

struct PlayerInputSnapshot {
    uint16_t digitalButtons = 0;
    int16_t analogLeftX = 0;
    int16_t analogLeftY = 0;
    int16_t analogRightX = 0;
    int16_t analogRightY = 0;
    bool connected = false;
};

class InputManagerNative {
public:
    static InputManagerNative& getInstance() {
        static InputManagerNative instance;
        return instance;
    }

    void updatePlayerInput(unsigned port, uint16_t buttons, int16_t lx, int16_t ly, int16_t rx, int16_t ry, bool connected) {
        if (port >= 4) return;
        std::lock_guard<std::mutex> lock(mMutex);
        mPendingInputs[port].digitalButtons = buttons;
        mPendingInputs[port].analogLeftX = lx;
        mPendingInputs[port].analogLeftY = ly;
        mPendingInputs[port].analogRightX = rx;
        mPendingInputs[port].analogRightY = ry;
        mPendingInputs[port].connected = connected;
    }

    void pollInputs() {
        std::lock_guard<std::mutex> lock(mMutex);
        mSampledInputs = mPendingInputs;
    }

    int16_t getInputState(unsigned port, unsigned device, unsigned index, unsigned id) {
        if (port >= 4) return 0;
        const auto& p = mSampledInputs[port];
        if (!p.connected) return 0;

        if (device == RETRO_DEVICE_JOYPAD) {
            if (id <= RETRO_DEVICE_ID_JOYPAD_R3) {
                return (p.digitalButtons & (1 << id)) ? 1 : 0;
            }
        } else if (device == RETRO_DEVICE_ANALOG) {
            if (index == RETRO_DEVICE_INDEX_ANALOG_LEFT) {
                return (id == RETRO_DEVICE_ID_ANALOG_X) ? p.analogLeftX : p.analogLeftY;
            } else if (index == RETRO_DEVICE_INDEX_ANALOG_RIGHT) {
                return (id == RETRO_DEVICE_ID_ANALOG_X) ? p.analogRightX : p.analogRightY;
            }
        }
        return 0;
    }

    void releaseAllButtons() {
        std::lock_guard<std::mutex> lock(mMutex);
        for (auto& p : mPendingInputs) {
            p.digitalButtons = 0;
            p.analogLeftX = 0;
            p.analogLeftY = 0;
            p.analogRightX = 0;
            p.analogRightY = 0;
        }
        mSampledInputs = mPendingInputs;
    }

private:
    InputManagerNative() = default;
    std::mutex mMutex;
    std::array<PlayerInputSnapshot, 4> mPendingInputs;
    std::array<PlayerInputSnapshot, 4> mSampledInputs;
};

#endif
