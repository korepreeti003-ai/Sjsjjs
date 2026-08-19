cmake_minimum_required(VERSION 3.18.1)
project(netspoof_zygisk)

set(CMAKE_CXX_STANDARD 17)

add_library(netspoof_zygisk SHARED main.cpp)

target_link_libraries(netspoof_zygisk
    android
    log
)

target_compile_options(netspoof_zygisk PRIVATE
    -fvisibility=hidden
    -ffunction-sections
    -fdata-sections
    -O2
)

target_link_options(netspoof_zygisk PRIVATE
    -Wl,--gc-sections
    -Wl,--strip-all
)
