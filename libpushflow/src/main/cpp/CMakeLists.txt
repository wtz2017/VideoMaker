cmake_minimum_required(VERSION 3.4.1)

include_directories(pushflow/include/)
include_directories(librtmp/)

aux_source_directory(librtmp RTMP_SRC_LIST)# 查找在某个路径下的所有源文件

# 大多数不使用 OpenSSL 功能
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -DNO_CRYPTO")

# Creates and names a library, sets it as either STATIC or SHARED,
# and provides the relative paths to its source code.
add_library(
        wepushflow

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        ${RTMP_SRC_LIST}
        pushflow/WePushFlowJNI.cpp
        pushflow/WePushFlow.cpp
        pushflow/RtmpPacketQueue.cpp
        pushflow/JavaListener.cpp)

# Searches for a specified prebuilt library and stores the path as a variable.
# Because CMake includes system libraries in the search path by default,
# you only need to specify the name of the public NDK library you want to add.
find_library(
        # Sets the name of the path variable.
        log-lib
        # Specifies the name of the NDK library that you want CMake to locate.
        log)

# Specifies libraries CMake should link to your target library.
target_link_libraries(
        wepushflow

        # Links the target library to the log library included in the NDK.
        ${log-lib})
