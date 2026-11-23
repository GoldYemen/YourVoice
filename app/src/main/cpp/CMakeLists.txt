# ---------------------------------------------------------------------------
#  Sotak native audio layer
#  Builds libsotak_audio.so : the Opus codec + a low-latency jitter buffer.
#  Opus is fetched at configure time, so the repository stays tiny.
# ---------------------------------------------------------------------------
cmake_minimum_required(VERSION 3.22.1)
project(sotak_audio C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)

# Small, static Opus build -- no tests, no command-line tools.
set(OPUS_BUILD_TESTING        OFF CACHE BOOL "" FORCE)
set(OPUS_BUILD_PROGRAMS       OFF CACHE BOOL "" FORCE)
set(OPUS_BUILD_SHARED_LIBRARY OFF CACHE BOOL "" FORCE)
set(OPUS_STATIC_RUNTIME       OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    opus
    GIT_REPOSITORY https://github.com/xiph/opus.git
    GIT_TAG        v1.5.2
    GIT_SHALLOW    TRUE
)
FetchContent_MakeAvailable(opus)

add_library(sotak_audio SHARED
    audio_engine.cpp
    jitter_buffer.cpp
    native_bridge.cpp
)

target_include_directories(sotak_audio PRIVATE ${opus_SOURCE_DIR}/include)
target_compile_options(sotak_audio PRIVATE -O3 -ffast-math -fvisibility=hidden)
target_link_libraries(sotak_audio PRIVATE opus log)
