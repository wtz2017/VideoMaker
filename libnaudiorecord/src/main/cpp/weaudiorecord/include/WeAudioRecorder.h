//
// Created by WTZ on 2020/4/15.
//

#ifndef VIDEOMAKER_WEAUDIORECORDER_H
#define VIDEOMAKER_WEAUDIORECORDER_H


#include <stddef.h>
#include "OnPCMDataCall.h"
#include "DoubleBuffer.h"

extern "C"
{
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
}

#define VIDMK_DOUBLE_BUFFER_ELEM_COUNT 4096

class WeAudioRecorder {

private:
    OnPCMDataCall *onPCMDataCall = NULL;

    SLuint32 slSampleRate = 0;
    SLuint32 slChannelLayout = 0;
    SLuint16 slEncodingBits = 0;

    int sampleRateInHz = 0;
    int channelNum = 0;
    int bitsPerSample = 0;

    bool initOpenSLSuccess = false;
    bool enqueueFailed = false;

    // 引擎
    SLObjectItf engineObject = NULL;
    SLEngineItf engineItf = NULL;

    // 录音机
    SLObjectItf recordObject = NULL;
    SLRecordItf recordItf = NULL;

    // 用于注册接收数据回调和传入接收数据 buffer 的队列接口
    SLAndroidSimpleBufferQueueItf bufferQueueItf = NULL;
    // 用于接收数据的 buffer
    DoubleBuffer *recordBuffer = NULL;

public:
    const char *LOG_TAG = "_WeAudioRecorder";
    bool isRecording = false;
    bool workFinished = true;

public:
    WeAudioRecorder(OnPCMDataCall *onPcmDataCall);

    ~WeAudioRecorder();

    bool init();

    bool init2(int sampleRateInHz, int channelConfig, int encodingBits);

    int getSampleRate();

    int getChannelNums();

    int getBitsPerSample();

    bool start();

    bool resume();

    bool enqueueReceiveBuffer();

    void callbackData();

    void pause();

    void stop();

    void release();

private:
    SLuint32 transformSampleRate(int sampleRateInHz);

    SLuint32 transformChannelLayout(int channelLayout);

    SLuint32 getChannelNum(int channelLayout);

    SLuint16 transformEncodingBits(int encodingFormat);

    bool _initOpenSL();

    bool _initEngine();

    bool _initRecorder();

    bool _setRecordState(SLuint32 state);

    /**
     * 确保在退出应用时销毁所有对象。
     * 对象应按照与创建时相反的顺序销毁，因为销毁具有依赖对象的对象并不安全。
     * 例如，请按照以下顺序销毁：音频播放器和录制器、输出混合，最后是引擎。
     */
    void _destroyOpenSL();

    /**
     * destroy audio recorder object, and invalidate all associated interfaces
     */
    void _destroyRecorder();

    /**
     * destroy engine object, and invalidate all associated interfaces
     */
    void _destroyEngine();

};


#endif //VIDEOMAKER_WEAUDIORECORDER_H
