cmake_minimum_required(VERSION 3.22.1)

project("aladdin_emulator")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/libretro
    ${CMAKE_CURRENT_SOURCE_DIR}/video
    ${CMAKE_CURRENT_SOURCE_DIR}/audio
    ${CMAKE_CURRENT_SOURCE_DIR}/input
    ${CMAKE_CURRENT_SOURCE_DIR}/storage
)

add_library(aladdin_emulator SHARED
    jni/emulator_jni.cpp
    libretro/core_bridge.cpp
    video/gl_renderer.cpp
)

find_library(log-lib log)
find_library(android-lib android)
find_library(egl-lib EGL)
find_library(gles2-lib GLESv2)

target_link_libraries(aladdin_emulator
    ${log-lib}
    ${android-lib}
    ${egl-lib}
    ${gles2-lib}
    m
    dl
)
