cmake_minimum_required(VERSION 3.4.1)
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}")

add_executable(libwg-quick.so wireguard-android-integration/wg-quick.c)
target_compile_options(libwg-quick.so PUBLIC -O3 -std=gnu11 -Wall -pedantic -Wno-missing-field-initializers -DWG_CONFIG_SEARCH_PATHS=\"\\\"/data/data/com.wireguard.android/files\\\"\")

file(GLOB WG_SOURCES wireguard/src/tools/*.c libmnl/src/*.c)
add_executable(libwg.so ${WG_SOURCES})
target_compile_options(libwg.so PUBLIC "-I${CMAKE_CURRENT_SOURCE_DIR}libmnl/src/" "-I${CMAKE_CURRENT_SOURCE_DIR}/libmnl/include/" "-I${CMAKE_CURRENT_SOURCE_DIR}/wireguard/src/tools/" -O3 -std=gnu11 -D_GNU_SOURCE -DHAVE_VISIBILITY_HIDDEN -DRUNSTATEDIR=\"\\\"/data/data/com.wireguard.android/cache\\\"\" -Wno-pointer-arith -Wno-unused-parameter)
