#ifndef GL_RENDERER_H__
#define GL_RENDERER_H__

#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <android/native_window.h>
#include <stdint.h>
#include <stddef.h>
#include <vector>
#include <cstring>
#include <mutex>
#include "../libretro/libretro.h"

enum class AspectRatioMode {
    Original = 0,
    IntegerScale = 1,
    Stretch = 2
};

class GLRenderer {
public:
    static GLRenderer& getInstance() {
        static GLRenderer instance;
        return instance;
    }

    void setNativeWindow(ANativeWindow* window);
    bool initEGLOnCurrentThread();
    void destroyEGLOnCurrentThread();

    void setPixelFormat(retro_pixel_format fmt);
    void setGeometry(unsigned baseW, unsigned baseH, unsigned maxW, unsigned maxH, float aspect);
    void setFilterMode(bool bilinear);
    void setAspectRatioMode(AspectRatioMode mode);

    void updateSoftwareFrame(const void* data, unsigned width, unsigned height, size_t pitch);
    void notifyHwFrameReady(unsigned width, unsigned height);
    void render();

    // Hardware render support
    void setHwRenderCallback(const retro_hw_render_callback& cb);
    uintptr_t getCurrentFramebuffer();
    void resetCoreState();
    bool isHwRender() const { return mIsHwRender; }
    bool ensureContextReady();

    unsigned getFrameWidth() const { return mGameWidth; }
    unsigned getFrameHeight() const { return mGameHeight; }
    float getTargetFps() const { return mTargetFps; }
    void setTargetFps(double fps) { mTargetFps = (float)fps; }

private:
    GLRenderer();
    ~GLRenderer();

    bool initEGLInternal();
    void initGL();
    void calculateViewport(int winW, int winH, int& outX, int& outY, int& outW, int& outH);

    std::mutex mMutex;
    ANativeWindow* mWindow = nullptr;
    EGLDisplay mDisplay = EGL_NO_DISPLAY;
    EGLSurface mSurface = EGL_NO_SURFACE;
    EGLContext mContext = EGL_NO_CONTEXT;

    GLuint mProgram = 0;
    GLuint mTextureId = 0;
    unsigned mTextureWidth = 0;
    unsigned mTextureHeight = 0;
    GLenum mTextureFormat = 0;
    GLenum mTextureType = 0;
    GLuint mPositionHandle = 0;
    GLuint mTexCoordHandle = 0;
    GLuint mSamplerHandle = 0;

    // Hardware FBO
    GLuint mHwFbo = 0;
    GLuint mHwRboDepth = 0;
    GLuint mHwTex = 0;
    bool mIsHwRender = false;
    retro_hw_render_callback mHwRenderCb{};

    retro_pixel_format mPixelFormat = RETRO_PIXEL_FORMAT_RGB565;
    unsigned mGameWidth = 320;
    unsigned mGameHeight = 240;
    float mGameAspect = 4.0f / 3.0f;
    float mTargetFps = 60.0f;

    bool mBilinear = false;
    AspectRatioMode mAspectMode = AspectRatioMode::Original;

    // Staging pixel buffer for conversion / pitch handling
    std::vector<uint8_t> mPixelStaging;
    bool mWindowNeedsInit = false;
    bool mHwFrameReady = false;
    bool mCoreContextResetDone = false;
};

#endif
