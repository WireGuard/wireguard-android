cmake_minimum_required(VERSION 3.4.1)
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}")

add_executable(libwg-quick.so wireguard-android-integration/wg-quick.c)
SET_TARGET_PROPERTIES(libwg-quick.so PROPERTIES COMPILE_FLAGS "-O3 -std=gnu11 -Wall -pedantic -Wno-missing-field-initializers -DWG_CONFIG_SEARCH_PATHS=\"\\\"/data/data/com.wireguard.android/files\\\"\"")

FILE(GLOB WG_SOURCES wireguard/src/tools/*.c libmnl/src/*.c)
add_executable(libwg.so ${WG_SOURCES})
SET_TARGET_PROPERTIES(libwg.so PROPERTIES COMPILE_FLAGS "'-I${CMAKE_CURRENT_SOURCE_DIR}libmnl/src/' '-I${CMAKE_CURRENT_SOURCE_DIR}/libmnl/include/' '-I${CMAKE_CURRENT_SOURCE_DIR}/wireguard/src/tools/' -O3 -std=gnu11 -D_GNU_SOURCE -DHAVE_VISIBILITY_HIDDEN -DRUNSTATEDIR=\"\\\"/data/data/com.wireguard.android/cache\\\"\" -Wno-pointer-arith -Wno-unused-parameter")
