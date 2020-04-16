cmake_minimum_required(VERSION 3.4.1)

include_directories(weaudiorecord/include/)

# Creates and names a library, sets it as either STATIC or SHARED,
# and provides the relative paths to its source code.
add_library(
        we_audio_record

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        weaudiorecord/WeAudioRecordJNI.cpp
        weaudiorecord/WeAudioRecorder.cpp
        weaudiorecord/JavaListener.cpp
        weaudiorecord/DoubleBuffer.cpp )

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
        we_audio_record

        OpenSLES

        # Links the target library to the log library included in the NDK.
        ${log-lib})
