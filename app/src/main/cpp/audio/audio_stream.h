#ifndef AUDIO_STREAM_H__
#define AUDIO_STREAM_H__

#include <stdint.h>
#include <stddef.h>
#include <vector>
#include <mutex>
#include <algorithm>

class AudioRingBuffer {
public:
    static AudioRingBuffer& getInstance() {
        static AudioRingBuffer instance;
        return instance;
    }

    void init(size_t capacityFrames, double sampleRate) {
        std::lock_guard<std::mutex> lock(mMutex);
        mCapacitySamples = capacityFrames * 2; // stereo
        mBuffer.resize(mCapacitySamples, 0);
        mHead = 0;
        mTail = 0;
        mAvailableSamples = 0;
        mSampleRate = sampleRate;
        mUnderrunCount = 0;
        mOverflowCount = 0;
    }

    void writeStereoSamples(int16_t left, int16_t right) {
        std::lock_guard<std::mutex> lock(mMutex);
        if (mCapacitySamples == 0) return;

        if (mAvailableSamples + 2 > mCapacitySamples) {
            // Buffer overflow: drop oldest samples
            mTail = (mTail + 2) % mCapacitySamples;
            mAvailableSamples -= 2;
            mOverflowCount++;
        }

        mBuffer[mHead] = left;
        mBuffer[(mHead + 1) % mCapacitySamples] = right;
        mHead = (mHead + 2) % mCapacitySamples;
        mAvailableSamples += 2;
    }

    size_t writeBatch(const int16_t* data, size_t frames) {
        if (!data || frames == 0) return 0;
        std::lock_guard<std::mutex> lock(mMutex);
        if (mCapacitySamples == 0) return 0;

        size_t samplesToWrite = frames * 2;
        for (size_t i = 0; i < samplesToWrite; i += 2) {
            if (mAvailableSamples + 2 > mCapacitySamples) {
                mTail = (mTail + 2) % mCapacitySamples;
                mAvailableSamples -= 2;
                mOverflowCount++;
            }
            mBuffer[mHead] = data[i];
            mBuffer[(mHead + 1) % mCapacitySamples] = data[i + 1];
            mHead = (mHead + 2) % mCapacitySamples;
            mAvailableSamples += 2;
        }
        return frames;
    }

    size_t read(int16_t* outData, size_t maxSamples) {
        if (!outData || maxSamples == 0) return 0;
        std::lock_guard<std::mutex> lock(mMutex);
        if (mAvailableSamples == 0) {
            mUnderrunCount++;
            return 0;
        }

        size_t samplesToRead = std::min(maxSamples, mAvailableSamples);
        for (size_t i = 0; i < samplesToRead; ++i) {
            outData[i] = mBuffer[mTail];
            mTail = (mTail + 1) % mCapacitySamples;
        }
        mAvailableSamples -= samplesToRead;
        return samplesToRead;
    }

    double getSampleRate() const { return mSampleRate; }
    size_t getUnderruns() const { return mUnderrunCount; }
    size_t getOverflows() const { return mOverflowCount; }

private:
    AudioRingBuffer() : mCapacitySamples(0), mHead(0), mTail(0), mAvailableSamples(0), mSampleRate(44100.0), mUnderrunCount(0), mOverflowCount(0) {}
    std::mutex mMutex;
    std::vector<int16_t> mBuffer;
    size_t mCapacitySamples;
    size_t mHead;
    size_t mTail;
    size_t mAvailableSamples;
    double mSampleRate;
    size_t mUnderrunCount;
    size_t mOverflowCount;
};

#endif
