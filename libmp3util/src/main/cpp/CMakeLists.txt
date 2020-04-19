cmake_minimum_required(VERSION 3.4.1)

include_directories(wemp3/include/)
include_directories(lamemp3/include/)

aux_source_directory(lamemp3 LAME_SRC_LIST)# 查找在某个路径下的所有源文件

# 因为很多类里用到的一些标准函数需要定义这个宏定义才会导入对应头文件
add_compile_options("-DSTDC_HEADERS")

# Creates and names a library, sets it as either STATIC or SHARED,
# and provides the relative paths to its source code.
add_library(
        wemp3

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        ${LAME_SRC_LIST}
        wemp3/WeMp3JNI.cpp
        wemp3/WeMp3Encoder.cpp
        wemp3/JavaListener.cpp )

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
        wemp3

        # Links the target library to the log library included in the NDK.
        ${log-lib})
