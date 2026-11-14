# Automix (BitChord port): native analysis front end and loop-mix renderer.
# Sources are copied from BitChord (GPLv3, see LICENSE.BitChord) and live next
# to this file. The Beat This! ONNX model runs through ONNX Runtime on the
# Kotlin side, not here.
cmake_minimum_required(VERSION 3.22.1)
project(bcmix LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(bitchord_analysis SHARED
    analyzer/audio_analysis.cpp
    analyzer/tempo_analysis.cpp
    analyzer/resampler.cpp
    analyzer/mel_spectrogram.cpp
    analyzer/vocal_spectrogram.cpp
    jni/analysis_jni.cpp
    jni/mel_jni.cpp
    jni/vocal_jni.cpp
)
target_include_directories(bitchord_analysis PRIVATE ${CMAKE_CURRENT_SOURCE_DIR} ${CMAKE_CURRENT_SOURCE_DIR}/analyzer)
target_compile_options(bitchord_analysis PRIVATE -O3 -fvisibility=hidden)
find_library(log-lib log)
target_link_libraries(bitchord_analysis ${log-lib})
target_link_options(bitchord_analysis PRIVATE "-Wl,-z,max-page-size=16384")

add_library(bitchord_loopmix SHARED
    loopmix/loop_mix_renderer.cpp
    jni/loopmix_jni.cpp
)
target_include_directories(bitchord_loopmix PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/signalsmith-stretch
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/signalsmith-linear
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party
    ${CMAKE_CURRENT_SOURCE_DIR}/loopmix
)
target_compile_options(bitchord_loopmix PRIVATE -O3 -ffast-math -fvisibility=hidden -Wno-unused-parameter)
target_link_libraries(bitchord_loopmix ${log-lib})
target_link_options(bitchord_loopmix PRIVATE "-Wl,-z,max-page-size=16384")
