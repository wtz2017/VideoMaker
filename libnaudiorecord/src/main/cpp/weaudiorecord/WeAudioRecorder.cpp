//
// Created by WTZ on 2020/4/15.
//

#include <AndroidLog.h>
#include "WeAudioRecorder.h"

WeAudioRecorder::WeAudioRecorder(OnPCMDataCall *onPcmDataCall) {
    this->onPCMDataCall = onPcmDataCall;
}

WeAudioRecorder::~WeAudioRecorder() {
    release();// 与 init 对等

    delete onPCMDataCall;// 既然在构造函数里创建，就要在析构里销毁，不要放在 release 里
    onPCMDataCall = NULL;
}

bool WeAudioRecorder::init() {
    slSampleRate = SL_SAMPLINGRATE_44_1;
    sampleRateInHz = 44100;
    slChannelLayout = SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT;
    channelNum = 2;
    slEncodingBits = SL_PCMSAMPLEFORMAT_FIXED_16;
    bitsPerSample = 16;
    // 本来是想在此调用 _initOpenSL()，然后在 release 中调用 _destroyOpenSL() 释放 OpenSL 资源的，
    // 但是某些机型在停止录音后如若不直接释放资源，再重复启动几次后就报错：
    // libOpenSLES: Leaving BufferQueue::Enqueue (SL_RESULT_BUFFER_INSUFFICIENT)
    return true;
}

bool WeAudioRecorder::init2(int sampleRateInHz, int channelLayout, int encodingBits) {
    this->slSampleRate = transformSampleRate(sampleRateInHz);
    this->sampleRateInHz = sampleRateInHz;
    this->slChannelLayout = transformChannelLayout(channelLayout);
    this->channelNum = getChannelNum(channelLayout);
    this->slEncodingBits = transformEncodingBits(encodingBits);
    this->bitsPerSample = encodingBits;
    // 本来是想在此调用 _initOpenSL()，然后在 release 中调用 _destroyOpenSL() 释放 OpenSL 资源的，
    // 但是没这么做的原因同 init()
    return true;
}

int WeAudioRecorder::getSampleRate() {
    return sampleRateInHz;
}

int WeAudioRecorder::getChannelNums() {
    return channelNum;
}

int WeAudioRecorder::getBitsPerSample() {
    return bitsPerSample;
}

bool WeAudioRecorder::start() {
    if (LOG_DEBUG) {
        LOGW(LOG_TAG, "start initOpenSLSuccess=%d", initOpenSLSuccess);
    }
    _initOpenSL();
    if (!initOpenSLSuccess) {
        return false;
    }

    if (!_setRecordState(SL_RECORDSTATE_RECORDING)) {
        return false;
    }

    isRecording = true;
    recordBuffer = new DoubleBuffer(VIDMK_DOUBLE_BUFFER_ELEM_COUNT);
    if (!enqueueReceiveBuffer()) {
        isRecording = false;
        return false;
    }

    return true;
}

bool WeAudioRecorder::resume() {
    if (LOG_DEBUG) {
        LOGW(LOG_TAG, "resume initOpenSLSuccess=%d", initOpenSLSuccess);
    }
    if (!initOpenSLSuccess) {
        return false;
    }

    if (!_setRecordState(SL_RECORDSTATE_RECORDING)) {
        return false;
    }

    isRecording = true;
    if (enqueueFailed) {
        if (!enqueueReceiveBuffer()) {
            isRecording = false;
            return false;
        }
    }

    return true;
}

bool WeAudioRecorder::enqueueReceiveBuffer() {
    SLresult result;
    result = (*bufferQueueItf)->Enqueue(
            bufferQueueItf, recordBuffer->getFreeBuffer(), recordBuffer->getBytesSizePerBuffer());
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "enqueueReceiveBuffer failed: %d", result);
        enqueueFailed = true;
        return false;
    }

    enqueueFailed = false;
    return true;
}

void WeAudioRecorder::callbackData() {
    if (onPCMDataCall != NULL) {
        onPCMDataCall->callback(2, recordBuffer->getDataBuffer(),
                                recordBuffer->getBytesSizePerBuffer());
    }
}

void WeAudioRecorder::pause() {
    if (LOG_DEBUG) {
        LOGW(LOG_TAG, "pause initOpenSLSuccess=%d", initOpenSLSuccess);
    }
    if (!initOpenSLSuccess) {
        return;
    }

    isRecording = false;
    _setRecordState(SL_RECORDSTATE_PAUSED);
}

void WeAudioRecorder::stop() {
    if (LOG_DEBUG) {
        LOGW(LOG_TAG, "stop initOpenSLSuccess=%d", initOpenSLSuccess);
    }
    isRecording = false;

    if (initOpenSLSuccess) {
        _setRecordState(SL_RECORDSTATE_STOPPED);

        int sleepCount = 0;
        while (!workFinished) {
            if (sleepCount > 100) {// 最多睡眠 1000 ms
                break;
            }
            sleepCount++;
            usleep(10 * 1000);// 每次睡眠 10 ms
        }
        if (LOG_DEBUG) {
            LOGW(LOG_TAG, "Record stopped after sleep %d ms", sleepCount * 10);
        }

        int ret = (*bufferQueueItf)->Clear(bufferQueueItf);
        if (ret != SL_RESULT_SUCCESS) {
            LOGE(LOG_TAG, "bufferQueueItf clear failed: %d", ret);
        }
    }

    _destroyOpenSL();
    delete recordBuffer;
    recordBuffer = NULL;
}

void WeAudioRecorder::release() {
    // 1.本来是想在此调用 _destroyOpenSL() 释放 OpenSL 资源的，在 init 中调用 _initOpenSL()，
    // 但是某些机型在停止录音后如若不直接释放资源，再重复启动几次后就报错：
    // libOpenSLES: Leaving BufferQueue::Enqueue (SL_RESULT_BUFFER_INSUFFICIENT)
    // 2.stop 会等待工作结束后再释放资源，避免多线程并发错误
    stop();
}

void recordCallback(SLAndroidSimpleBufferQueueItf bq, void *context) {
    WeAudioRecorder *recorder = (WeAudioRecorder *) context;
    if (recorder == NULL) {
        LOGE("_WeAudioRecorder", "recordCallback cast context to WeAudioRecorder result is NULL")
        return;
    }
    if (!recorder->isRecording) {
        LOGW("_WeAudioRecorder", "recordCallback recorder isRecording=false")
        return;
    }

    recorder->workFinished = false;
    recorder->callbackData();
    recorder->enqueueReceiveBuffer();
    recorder->workFinished = true;
}

SLuint32 WeAudioRecorder::transformSampleRate(int sampleRateInHz) {
    SLuint32 rate = 0;
    switch (sampleRateInHz) {
        case 8000:
            rate = SL_SAMPLINGRATE_8;
            break;
        case 11025:
            rate = SL_SAMPLINGRATE_11_025;
            break;
        case 12000:
            rate = SL_SAMPLINGRATE_12;
            break;
        case 16000:
            rate = SL_SAMPLINGRATE_16;
            break;
        case 22050:
            rate = SL_SAMPLINGRATE_22_05;
            break;
        case 24000:
            rate = SL_SAMPLINGRATE_24;
            break;
        case 32000:
            rate = SL_SAMPLINGRATE_32;
            break;
        case 44100:
            rate = SL_SAMPLINGRATE_44_1;
            break;
        case 48000:
            rate = SL_SAMPLINGRATE_48;
            break;
        case 64000:
            rate = SL_SAMPLINGRATE_64;
            break;
        case 88200:
            rate = SL_SAMPLINGRATE_88_2;
            break;
        case 96000:
            rate = SL_SAMPLINGRATE_96;
            break;
        case 192000:
            rate = SL_SAMPLINGRATE_192;
            break;
        default:
            rate = SL_SAMPLINGRATE_44_1;
    }
    return rate;
}

SLuint32 WeAudioRecorder::transformChannelLayout(int channelLayout) {
    SLuint32 ret = 0;
    switch (channelLayout) {
        case 1:
            ret = SL_SPEAKER_FRONT_CENTER;
            break;
        case 2:
            ret = SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT;
            break;
        default:
            ret = SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT;
    }
    return ret;
}

SLuint32 WeAudioRecorder::getChannelNum(int channelLayout) {
    SLuint32 ret = 0;
    switch (channelLayout) {
        case 1:
            ret = 1;
            break;
        case 2:
            ret = 2;
            break;
        default:
            ret = 2;
    }
    return ret;
}

SLuint16 WeAudioRecorder::transformEncodingBits(int encodingBits) {
    SLuint16 ret = 0;
    switch (encodingBits) {
        case 8:
            ret = SL_PCMSAMPLEFORMAT_FIXED_8;
            break;
        case 16:
            ret = SL_PCMSAMPLEFORMAT_FIXED_16;
            break;
        default:
            ret = SL_PCMSAMPLEFORMAT_FIXED_16;
    }
    return ret;
}

bool WeAudioRecorder::_initOpenSL() {
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "_initOpenSL");
    }

    // 初始化引擎
    if (!_initEngine()) {
        _destroyOpenSL();
        return false;
    }

    // 使用引擎创建录音器
    if (!_initRecorder()) {
        _destroyOpenSL();
        return false;
    }

    initOpenSLSuccess = true;
    return initOpenSLSuccess;
}

bool WeAudioRecorder::_initEngine() {
    // create engine object
    SLresult result;
    result = slCreateEngine(&engineObject, 0, NULL, 0, NULL, NULL);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "slCreateEngine exception: %d", result);
        return false;
    }

    // realize the engine object
    (void) result;
    result = (*engineObject)->Realize(engineObject, SL_BOOLEAN_FALSE);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "engineObject Realize exception: %d", result);
        _destroyEngine();
        return false;
    }

    // get the engine interface, which is needed in order to create other objects
    (void) result;
    result = (*engineObject)->GetInterface(engineObject, SL_IID_ENGINE, &engineItf);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "GetInterface SLEngineItf exception: %d", result);
        _destroyEngine();
        return false;
    }

    return true;
}

bool WeAudioRecorder::_initRecorder() {
    // 配置声音数据来源
    SLDataLocator_IODevice locIODevice = {SL_DATALOCATOR_IODEVICE,
                                          SL_IODEVICE_AUDIOINPUT,
                                          SL_DEFAULTDEVICEID_AUDIOINPUT,
                                          NULL};
    SLDataSource audioSrc = {&locIODevice, NULL};


    // 配置数据接收缓冲池和格式
    SLDataLocator_AndroidSimpleBufferQueue locBufQueue = {
            SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE,
            2 //缓存队列个数
    };
    SLDataFormat_PCM pcmFormat = {
            SL_DATAFORMAT_PCM,// 数据格式：pcm
            channelNum,// 声道数
            slSampleRate,// 采样率
            slEncodingBits,// bitsPerSample
            slEncodingBits,// containerSize：和采样位数一致就行
            slChannelLayout,// 声道布局
            SL_BYTEORDER_LITTLEENDIAN// 字节排列顺序：小端 little-endian，将低序字节存储在起始地址
    };
    SLDataSink audioSink = {&locBufQueue, &pcmFormat};

    // 配置录音器需要放开的缓冲队列接口
    // 如果某个功能接口没注册 id 和写为 SL_BOOLEAN_TRUE，后边通过 GetInterface 就获取不到这个接口
    const int ID_COUNT = 1;
    const SLInterfaceID ids[ID_COUNT] = {SL_IID_ANDROIDSIMPLEBUFFERQUEUE};
    const SLboolean reqs[ID_COUNT] = {SL_BOOLEAN_TRUE};

    // 创建录音器
    SLresult result;
    result = (*engineItf)->CreateAudioRecorder(engineItf, &recordObject, &audioSrc, &audioSink,
                                               ID_COUNT, ids, reqs);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "CreateAudioRecorder exception: %d", result);
        return false;
    }
    // 实例化录音器
    (void) result;
    result = (*recordObject)->Realize(recordObject, SL_BOOLEAN_FALSE);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "Realize recordObject exception: %d", result);
        return false;
    }

    // 获取录音控制接口
    (void) result;
    result = (*recordObject)->GetInterface(recordObject, SL_IID_RECORD, &recordItf);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "GetInterface recordItf exception: %d", result);
        return false;
    }

    // 获取缓冲队列接口
    (void) result;
    result = (*recordObject)->GetInterface(recordObject, SL_IID_ANDROIDSIMPLEBUFFERQUEUE,
                                           &bufferQueueItf);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "GetInterface bufferQueueItf exception: %d", result);
        return false;
    }
    // 注册数据回调接口
    (void) result;
    result = (*bufferQueueItf)->RegisterCallback(bufferQueueItf, recordCallback, this);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "bufferQueueItf RegisterCallback exception: %d", result);
        return false;
    }

    return true;
}

bool WeAudioRecorder::_setRecordState(SLuint32 state) {
    if (recordItf == NULL) {
        return false;
    }

    SLresult result;
    result = (*recordItf)->SetRecordState(recordItf, state);
    if (SL_RESULT_SUCCESS != result) {
        LOGE(LOG_TAG, "_setRecordState exception: %d", result);
        return false;
    }

    return true;
}

void WeAudioRecorder::_destroyOpenSL() {
    if (LOG_DEBUG) {
        LOGW(LOG_TAG, "_destroyOpenSL initOpenSLSuccess=%d", initOpenSLSuccess);
    }
    initOpenSLSuccess = false;
    _destroyRecorder();
    _destroyEngine();
}

void WeAudioRecorder::_destroyRecorder() {
    if (recordObject != NULL) {
        (*recordObject)->Destroy(recordObject);
        recordObject = NULL;
        recordItf = NULL;
        bufferQueueItf = NULL;
    }
}

void WeAudioRecorder::_destroyEngine() {
    if (engineObject != NULL) {
        (*engineObject)->Destroy(engineObject);
        engineObject = NULL;
        engineItf = NULL;
    }
}
