#include "gl_renderer.h"
#include <android/log.h>
#include <algorithm>
#include <cmath>
#include <cstring>
#include <vector>

#define LOG_TAG "GenieGLRenderer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static const char* VERTEX_SHADER =
    "attribute vec4 aPosition;\n"
    "attribute vec2 aTexCoord;\n"
    "varying vec2 vTexCoord;\n"
    "void main() {\n"
    "    gl_Position = aPosition;\n"
    "    vTexCoord = aTexCoord;\n"
    "}\n";

static const char* FRAGMENT_SHADER =
    "precision mediump float;\n"
    "varying vec2 vTexCoord;\n"
    "uniform sampler2D uSampler;\n"
    "void main() {\n"
    "    gl_FragColor = texture2D(uSampler, vTexCoord);\n"
    "}\n";

static GLuint compileShader(GLenum type, const char* source) {
    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);
    GLint compiled = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (!compiled) {
        GLint infoLen = 0;
        glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &infoLen);
        if (infoLen > 1) {
            std::vector<char> infoLog(infoLen);
            glGetShaderInfoLog(shader, infoLen, nullptr, infoLog.data());
            LOGE("Shader compilation error: %s", infoLog.data());
        }
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

GLRenderer::GLRenderer() = default;

GLRenderer::~GLRenderer() {
    destroyEGLOnCurrentThread();
}

void GLRenderer::setNativeWindow(ANativeWindow* window) {
    std::lock_guard<std::mutex> lock(mMutex);
    mWindow = window;
    mWindowNeedsInit = (window != nullptr);
}

bool GLRenderer::initEGLInternal() {
    if (!mWindow) return false;

    if (mDisplay == EGL_NO_DISPLAY) {
        mDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
        if (mDisplay == EGL_NO_DISPLAY) {
            LOGE("eglGetDisplay failed");
            return false;
        }

        EGLint major, minor;
        if (!eglInitialize(mDisplay, &major, &minor)) {
            LOGE("eglInitialize failed");
            return false;
        }
    }

    const EGLint attribs[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_BLUE_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_RED_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, 16,
        EGL_NONE
    };

    EGLConfig config;
    EGLint numConfigs;
    if (!eglChooseConfig(mDisplay, attribs, &config, 1, &numConfigs) || numConfigs <= 0) {
        LOGE("eglChooseConfig failed");
        return false;
    }

    if (mSurface != EGL_NO_SURFACE) {
        eglMakeCurrent(mDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        eglDestroySurface(mDisplay, mSurface);
        mSurface = EGL_NO_SURFACE;
    }

    mSurface = eglCreateWindowSurface(mDisplay, config, mWindow, nullptr);
    if (mSurface == EGL_NO_SURFACE) {
        LOGE("eglCreateWindowSurface failed");
        return false;
    }

    if (mContext == EGL_NO_CONTEXT) {
        const EGLint contextAttribs[] = {
            EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL_NONE
        };
        mContext = eglCreateContext(mDisplay, config, EGL_NO_CONTEXT, contextAttribs);
        if (mContext == EGL_NO_CONTEXT) {
            LOGE("eglCreateContext failed");
            return false;
        }
    }

    if (!eglMakeCurrent(mDisplay, mSurface, mSurface, mContext)) {
        LOGE("eglMakeCurrent failed on current worker thread");
        return false;
    }

    if (mProgram == 0) {
        initGL();
    }

    mWindowNeedsInit = false;
    LOGI("EGL successfully initialized on emulation thread. GLES Version: %s", glGetString(GL_VERSION));
    return true;
}

bool GLRenderer::initEGLOnCurrentThread() {
    std::lock_guard<std::mutex> lock(mMutex);
    return initEGLInternal();
}

bool GLRenderer::ensureContextReady() {
    std::lock_guard<std::mutex> lock(mMutex);
    if (!mWindow) return false;

    if (mWindowNeedsInit || mDisplay == EGL_NO_DISPLAY || mSurface == EGL_NO_SURFACE || mContext == EGL_NO_CONTEXT) {
        if (!initEGLInternal()) {
            return false;
        }
    }

    // 核心硬體 context_reset 獨立於 mProgram，確保每次載入硬體核心時均能在首幀前獲得 reset (R3-01)
    if (mIsHwRender && !mCoreContextResetDone && mHwRenderCb.context_reset) {
        LOGI("Invoking hardware core context_reset before first retro_run");
        mHwRenderCb.context_reset();
        mCoreContextResetDone = true;
    }

    return true;
}

void GLRenderer::initGL() {
    GLuint vs = compileShader(GL_VERTEX_SHADER, VERTEX_SHADER);
    GLuint fs = compileShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
    mProgram = glCreateProgram();
    glAttachShader(mProgram, vs);
    glAttachShader(mProgram, fs);
    glLinkProgram(mProgram);
    glDeleteShader(vs);
    glDeleteShader(fs);

    mPositionHandle = glGetAttribLocation(mProgram, "aPosition");
    mTexCoordHandle = glGetAttribLocation(mProgram, "aTexCoord");
    mSamplerHandle = glGetUniformLocation(mProgram, "uSampler");

    glGenTextures(1, &mTextureId);
    mTextureWidth = mTextureHeight = 0;
    mTextureFormat = mTextureType = 0;
    glBindTexture(GL_TEXTURE_2D, mTextureId);
    GLint filter = mBilinear ? GL_LINEAR : GL_NEAREST;
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
}

void GLRenderer::destroyEGLOnCurrentThread() {
    std::lock_guard<std::mutex> lock(mMutex);
    if (mDisplay != EGL_NO_DISPLAY) {
        if (mIsHwRender && mHwRenderCb.context_destroy) {
            mHwRenderCb.context_destroy();
        }
        mCoreContextResetDone = false;
        if (mProgram != 0) {
            glDeleteProgram(mProgram);
            mProgram = 0;
        }
        if (mTextureId != 0) {
            glDeleteTextures(1, &mTextureId);
            mTextureId = 0;
        }
        if (mHwFbo != 0) {
            glDeleteFramebuffers(1, &mHwFbo);
            mHwFbo = 0;
        }
        eglMakeCurrent(mDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (mSurface != EGL_NO_SURFACE) {
            eglDestroySurface(mDisplay, mSurface);
            mSurface = EGL_NO_SURFACE;
        }
        if (mContext != EGL_NO_CONTEXT) {
            eglDestroyContext(mDisplay, mContext);
            mContext = EGL_NO_CONTEXT;
        }
        eglTerminate(mDisplay);
        mDisplay = EGL_NO_DISPLAY;
    }
    mWindow = nullptr;
    mWindowNeedsInit = false;
}

void GLRenderer::setPixelFormat(retro_pixel_format fmt) {
    std::lock_guard<std::mutex> lock(mMutex);
    mPixelFormat = fmt;
}

void GLRenderer::setGeometry(unsigned baseW, unsigned baseH, unsigned maxW, unsigned maxH, float aspect) {
    std::lock_guard<std::mutex> lock(mMutex);
    mGameWidth = baseW;
    mGameHeight = baseH;
    mGameAspect = (aspect > 0.0f) ? aspect : ((float)baseW / (float)baseH);
}

void GLRenderer::setFilterMode(bool bilinear) {
    std::lock_guard<std::mutex> lock(mMutex);
    mBilinear = bilinear;
    if (mTextureId != 0) {
        glBindTexture(GL_TEXTURE_2D, mTextureId);
        GLint filter = mBilinear ? GL_LINEAR : GL_NEAREST;
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
    }
}

void GLRenderer::setAspectRatioMode(AspectRatioMode mode) {
    std::lock_guard<std::mutex> lock(mMutex);
    mAspectMode = mode;
}

void GLRenderer::updateSoftwareFrame(const void* data, unsigned width, unsigned height, size_t pitch) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (!data || width == 0 || height == 0 || mTextureId == 0) return;

    mGameWidth = width;
    mGameHeight = height;
    mHwFrameReady = false;

    glBindTexture(GL_TEXTURE_2D, mTextureId);

    const auto upload = [&](GLenum format, GLenum type, const void* pixels) {
        if (mTextureWidth != width || mTextureHeight != height ||
            mTextureFormat != format || mTextureType != type) {
            glTexImage2D(GL_TEXTURE_2D, 0, format, width, height, 0, format, type, nullptr);
            mTextureWidth = width;
            mTextureHeight = height;
            mTextureFormat = format;
            mTextureType = type;
        }
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, format, type, pixels);
    };

    if (mPixelFormat == RETRO_PIXEL_FORMAT_RGB565) {
        glPixelStorei(GL_UNPACK_ALIGNMENT, 2);
        if (pitch == width * 2) {
            upload(GL_RGB, GL_UNSIGNED_SHORT_5_6_5, data);
        } else {
            size_t tightBytes = width * height * 2;
            mPixelStaging.resize(tightBytes);
            const uint8_t* src = static_cast<const uint8_t*>(data);
            for (unsigned y = 0; y < height; ++y) {
                std::memcpy(&mPixelStaging[y * width * 2], src + (y * pitch), width * 2);
            }
            upload(GL_RGB, GL_UNSIGNED_SHORT_5_6_5, mPixelStaging.data());
        }
    } else if (mPixelFormat == RETRO_PIXEL_FORMAT_XRGB8888) {
        glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
        size_t tightBytes = width * height * 4;
        mPixelStaging.resize(tightBytes);
        const uint8_t* src = static_cast<const uint8_t*>(data);
        uint8_t* dst = mPixelStaging.data();
        for (unsigned y = 0; y < height; ++y) {
            const uint32_t* srcRow = reinterpret_cast<const uint32_t*>(src + (y * pitch));
            uint32_t* dstRow = reinterpret_cast<uint32_t*>(dst + (y * width * 4));
            for (unsigned x = 0; x < width; ++x) {
                uint32_t px = srcRow[x];
                uint8_t r = (px >> 16) & 0xFF;
                uint8_t g = (px >> 8) & 0xFF;
                uint8_t b = px & 0xFF;
                dstRow[x] = (0xFF << 24) | (b << 16) | (g << 8) | r;
            }
        }
        upload(GL_RGBA, GL_UNSIGNED_BYTE, mPixelStaging.data());
    } else if (mPixelFormat == RETRO_PIXEL_FORMAT_0RGB1555) {
        glPixelStorei(GL_UNPACK_ALIGNMENT, 2);
        mPixelStaging.resize(static_cast<size_t>(width) * height * 2);
        const auto* source = static_cast<const uint8_t*>(data);
        for (unsigned y = 0; y < height; ++y) {
            for (unsigned x = 0; x < width; ++x) {
                uint16_t pixel;
                std::memcpy(&pixel, source + y * pitch + x * 2, sizeof(pixel));
                const uint16_t rgba = static_cast<uint16_t>(((pixel & 0x7fff) << 1) | 1);
                std::memcpy(mPixelStaging.data() + (static_cast<size_t>(y) * width + x) * 2,
                            &rgba, sizeof(rgba));
            }
        }
        upload(GL_RGBA, GL_UNSIGNED_SHORT_5_5_5_1, mPixelStaging.data());
    }
}

void GLRenderer::notifyHwFrameReady(unsigned width, unsigned height) {
    std::lock_guard<std::mutex> lock(mMutex);
    mGameWidth = width;
    mGameHeight = height;
    mHwFrameReady = true;
}

void GLRenderer::calculateViewport(int winW, int winH, int& outX, int& outY, int& outW, int& outH) {
    if (mAspectMode == AspectRatioMode::Stretch) {
        outX = 0;
        outY = 0;
        outW = winW;
        outH = winH;
        return;
    }

    float targetAspect = mGameAspect;
    if (targetAspect <= 0.0f) targetAspect = 4.0f / 3.0f;

    if (mAspectMode == AspectRatioMode::IntegerScale) {
        int scaleX = std::max(1, winW / (int)mGameWidth);
        int scaleY = std::max(1, winH / (int)mGameHeight);
        int scale = std::min(scaleX, scaleY);
        outW = mGameWidth * scale;
        outH = mGameHeight * scale;
        outX = (winW - outW) / 2;
        outY = (winH - outH) / 2;
        return;
    }

    float winAspect = (float)winW / (float)winH;
    if (winAspect > targetAspect) {
        outH = winH;
        outW = static_cast<int>(winH * targetAspect);
    } else {
        outW = winW;
        outH = static_cast<int>(winW / targetAspect);
    }
    outX = (winW - outW) / 2;
    outY = (winH - outH) / 2;
}

void GLRenderer::render() {
    std::lock_guard<std::mutex> lock(mMutex);

    if (mWindowNeedsInit) {
        if (!initEGLInternal()) {
            return;
        }
    }

    if (mDisplay == EGL_NO_DISPLAY || mSurface == EGL_NO_SURFACE) return;

    if (mIsHwRender && mHwFrameReady) {
        // 硬體渲染核心直接呈現 Framebuffer，絕不可在此呼叫 glClear 擦除核心輸出 (R2-01)
        eglSwapBuffers(mDisplay, mSurface);
        mHwFrameReady = false;
        return;
    }

    if (mProgram == 0) return;

    EGLint winW = 0, winH = 0;
    eglQuerySurface(mDisplay, mSurface, EGL_WIDTH, &winW);
    eglQuerySurface(mDisplay, mSurface, EGL_HEIGHT, &winH);

    glClearColor(0.06f, 0.07f, 0.08f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

    int vpX, vpY, vpW, vpH;
    calculateViewport(winW, winH, vpX, vpY, vpW, vpH);
    glViewport(vpX, vpY, vpW, vpH);

    glUseProgram(mProgram);

    static const GLfloat vertices[] = {
        -1.0f, -1.0f,
         1.0f, -1.0f,
        -1.0f,  1.0f,
         1.0f,  1.0f,
    };

    static const GLfloat texCoords[] = {
        0.0f, 1.0f,
        1.0f, 1.0f,
        0.0f, 0.0f,
        1.0f, 0.0f,
    };

    glVertexAttribPointer(mPositionHandle, 2, GL_FLOAT, GL_FALSE, 0, vertices);
    glEnableVertexAttribArray(mPositionHandle);

    glVertexAttribPointer(mTexCoordHandle, 2, GL_FLOAT, GL_FALSE, 0, texCoords);
    glEnableVertexAttribArray(mTexCoordHandle);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, mTextureId);
    glUniform1i(mSamplerHandle, 0);

    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    glDisableVertexAttribArray(mPositionHandle);
    glDisableVertexAttribArray(mTexCoordHandle);

    eglSwapBuffers(mDisplay, mSurface);
}

void GLRenderer::setHwRenderCallback(const retro_hw_render_callback& cb) {
    std::lock_guard<std::mutex> lock(mMutex);
    mHwRenderCb = cb;
    mIsHwRender = (cb.context_type != RETRO_HW_CONTEXT_NONE);
    mCoreContextResetDone = false;
}

uintptr_t GLRenderer::getCurrentFramebuffer() {
    return (uintptr_t)mHwFbo;
}

void GLRenderer::resetCoreState() {
    std::lock_guard<std::mutex> lock(mMutex);
    if (mIsHwRender && mHwRenderCb.context_destroy) {
        mHwRenderCb.context_destroy();
    }
    mIsHwRender = false;
    mCoreContextResetDone = false;
    memset(&mHwRenderCb, 0, sizeof(mHwRenderCb));
    mHwFrameReady = false;
}
