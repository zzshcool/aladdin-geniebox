#!/usr/bin/env bash
# ==============================================================================
# 阿拉丁 GenieBox: Libretro 原生核心可重現建置與封裝腳本
# 目標 ABI: arm64-v8a
# 支援平台: NES (FCEUmm), GB/GBC (Gambatte), GBA (mGBA), SFC (Snes9x), PS1 (PCSX ReARMed), PSP (PPSSPP)
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BUILD_DIR="${PROJECT_ROOT}/build/cores"
OUTPUT_DIR="${PROJECT_ROOT}/app/src/main/jniLibs/arm64-v8a"

NDK_VERSION="26.1.10909125"
ANDROID_API="26"
ABI="arm64-v8a"

# 自動偵測 Android NDK 與 CMake
if [ -z "${ANDROID_NDK_HOME:-}" ]; then
    if [ -f "${PROJECT_ROOT}/local.properties" ]; then
        SDK_DIR=$(grep -E '^sdk.dir=' "${PROJECT_ROOT}/local.properties" | cut -d'=' -f2 | sed 's/\\\\/\\/g' | tr '\\' '/')
        if command -v cygpath >/dev/null 2>&1; then
            SDK_DIR=$(cygpath -u "${SDK_DIR}")
        fi
        if [ -d "${SDK_DIR}/ndk/${NDK_VERSION}" ]; then
            export ANDROID_NDK_HOME="${SDK_DIR}/ndk/${NDK_VERSION}"
        elif [ -d "${SDK_DIR}/ndk" ]; then
            LATEST_NDK=$(ls -1d "${SDK_DIR}/ndk/"* 2>/dev/null | tail -n 1)
            if [ -n "${LATEST_NDK}" ]; then
                export ANDROID_NDK_HOME="${LATEST_NDK}"
            fi
        fi
        if [ -d "${SDK_DIR}/cmake/3.22.1/bin" ]; then
            export PATH="${SDK_DIR}/cmake/3.22.1/bin:${PATH}"
        fi
    fi
fi

if [ -z "${ANDROID_NDK_HOME:-}" ]; then
    echo "錯誤: 未找到 ANDROID_NDK_HOME，請確認 local.properties 或環境變數。"
    exit 1
fi

if command -v cygpath >/dev/null 2>&1; then
    ANDROID_NDK_HOME="$(cygpath -u "${ANDROID_NDK_HOME}")"
fi

NDK_BUILD_BIN="${ANDROID_NDK_HOME}/ndk-build"
if [ -f "${ANDROID_NDK_HOME}/ndk-build.cmd" ] && command -v cygpath >/dev/null 2>&1; then
    NDK_BUILD_BIN="${ANDROID_NDK_HOME}/ndk-build.cmd"
fi

CMAKE_BIN="cmake"
NINJA_BIN="ninja"
if command -v cygpath >/dev/null 2>&1 && [ -f "${SDK_DIR:-}/cmake/3.22.1/bin/cmake.exe" ]; then
    CMAKE_BIN="${SDK_DIR}/cmake/3.22.1/bin/cmake.exe"
    NINJA_BIN="${SDK_DIR}/cmake/3.22.1/bin/ninja.exe"
fi

# 核心版本鎖定 (對齊 CoreRegistry.kt 登錄資訊)
COMMIT_FCEUMM="ac0d00a7fc01166f84c7bac01d1084ebfd2b865e"
COMMIT_GAMBATTE="d9d6cd06382d1ced30de34d56d3609452323dab1"
TAG_MGBA="0.10.3"
COMMIT_SNES9X="1bcc369e89f08243e0a462882fb1f3e42e51de3a"
COMMIT_PCSX="c8816799b50388e61cfe237fe2cdbb7d8175f20a"
TAG_PPSSPP="v1.17.1"

echo "=== 阿拉丁 GenieBox 核心編譯腳本 ==="
echo "NDK 路徑: ${ANDROID_NDK_HOME}"
echo "目標輸出目錄: ${OUTPUT_DIR}"
mkdir -p "${OUTPUT_DIR}"
mkdir -p "${BUILD_DIR}"

# 1. NES - FCEUmm
echo "[1/6] 獲取並建置 FCEUmm (NES)..."
cd "${BUILD_DIR}"
if [ ! -d "libretro-fceumm" ]; then
    git clone https://github.com/libretro/libretro-fceumm.git
fi
cd libretro-fceumm
git checkout "${COMMIT_FCEUMM}"
cd jni
"${NDK_BUILD_BIN}" APP_ABI=${ABI} APP_PLATFORM=android-${ANDROID_API} APP_SHORT_COMMANDS=true -j4
cp "${BUILD_DIR}/libretro-fceumm/libs/${ABI}/libretro.so" "${OUTPUT_DIR}/libfceumm_libretro_android.so"

# 2. GB/GBC - Gambatte
echo "[2/6] 獲取並建置 Gambatte (GB/GBC)..."
cd "${BUILD_DIR}"
if [ ! -d "gambatte-libretro" ]; then
    git clone https://github.com/libretro/gambatte-libretro.git
fi
cd gambatte-libretro
git checkout "${COMMIT_GAMBATTE}"
cd libgambatte/libretro/jni
"${NDK_BUILD_BIN}" APP_ABI=${ABI} APP_PLATFORM=android-${ANDROID_API} APP_SHORT_COMMANDS=true -j4
cp "${BUILD_DIR}/gambatte-libretro/libgambatte/libretro/libs/${ABI}/libretro.so" "${OUTPUT_DIR}/libgambatte_libretro_android.so"

# 3. GBA - mGBA
echo "[3/6] 獲取並建置 mGBA (GBA)..."
cd "${BUILD_DIR}"
if [ ! -d "mgba" ]; then
    git clone --depth 1 --branch "${TAG_MGBA}" https://github.com/mgba-emu/mgba.git
fi
cd mgba
mkdir -p build_android && cd build_android
"${CMAKE_BIN}" -B . -S .. -G "Ninja" \
      -DCMAKE_MAKE_PROGRAM="${NINJA_BIN}" \
      -DCMAKE_TOOLCHAIN_FILE="${ANDROID_NDK_HOME}/build/cmake/android.toolchain.cmake" \
      -DANDROID_ABI=${ABI} \
      -DANDROID_PLATFORM=android-${ANDROID_API} \
      -DBUILD_LIBRETRO=ON \
      -DBUILD_QT=OFF \
      -DBUILD_SDL=OFF \
      -DBUILD_STATIC=OFF \
      -DBUILD_SHARED=OFF
"${NINJA_BIN}" mgba_libretro
cp mgba_libretro.so "${OUTPUT_DIR}/libmgba_libretro_android.so"

# 4. SFC - Snes9x
echo "[4/6] 獲取並建置 Snes9x (SFC)..."
cd "${BUILD_DIR}"
if [ ! -d "snes9x" ]; then
    git clone https://github.com/snes9xgit/snes9x.git
fi
cd snes9x
git checkout "${COMMIT_SNES9X}"
cd libretro/jni
"${NDK_BUILD_BIN}" APP_ABI=${ABI} APP_PLATFORM=android-${ANDROID_API} APP_SHORT_COMMANDS=true -j4
cp "${BUILD_DIR}/snes9x/libretro/libs/${ABI}/libretro.so" "${OUTPUT_DIR}/libsnes9x_libretro_android.so"

# 5. PS1 - PCSX ReARMed
echo "[5/6] 獲取並建置 PCSX ReARMed (PS1)..."
cd "${BUILD_DIR}"
if [ ! -d "pcsx_rearmed" ]; then
    git clone https://github.com/libretro/pcsx_rearmed.git
fi
cd pcsx_rearmed
git checkout "${COMMIT_PCSX}"
echo '#define REV "r23-c881679"' > include/revision.h
git submodule update --init --recursive
cd jni
"${NDK_BUILD_BIN}" APP_ABI=${ABI} APP_PLATFORM=android-${ANDROID_API} APP_SHORT_COMMANDS=true -j4
cp "${BUILD_DIR}/pcsx_rearmed/libs/${ABI}/libretro.so" "${OUTPUT_DIR}/libpcsx_rearmed_libretro_android.so"

# 6. PSP - PPSSPP Libretro
echo "[6/6] 獲取並建置 PPSSPP Libretro (PSP)..."
cd "${BUILD_DIR}"
if [ ! -d "ppsspp" ]; then
    git clone --depth 1 --branch "${TAG_PPSSPP}" https://github.com/hrydgard/ppsspp.git
fi
cd ppsspp
git submodule update --init --recursive
mkdir -p build_android && cd build_android
"${CMAKE_BIN}" -B . -S .. -G "Ninja" \
      -DCMAKE_MAKE_PROGRAM="${NINJA_BIN}" \
      -DCMAKE_TOOLCHAIN_FILE="${ANDROID_NDK_HOME}/build/cmake/android.toolchain.cmake" \
      -DANDROID_ABI=${ABI} \
      -DANDROID_PLATFORM=android-${ANDROID_API} \
      -DLIBRETRO=ON \
      -DUSING_GLES2=ON \
      -DUSE_FFMPEG=ON \
      -DUSE_DISCORD=OFF \
      -DUNITTEST=OFF
"${NINJA_BIN}" ppsspp_libretro
cp ppsspp_libretro_android.so "${OUTPUT_DIR}/libppsspp_libretro_android.so"

# 部署 PPSSPP Assets
mkdir -p "${PROJECT_ROOT}/app/src/main/assets/ppsspp"
cp -r "${BUILD_DIR}/ppsspp/assets/"* "${PROJECT_ROOT}/app/src/main/assets/ppsspp/"

echo "=== 核心編譯完成，所有 arm64-v8a 產物與 assets 已就緒 ==="
ls -lh "${OUTPUT_DIR}"
