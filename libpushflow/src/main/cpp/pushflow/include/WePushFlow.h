//
// Created by WTZ on 2020/4/20.
//

#ifndef VIDEOMAKER_WEPUSHFLOW_H
#define VIDEOMAKER_WEPUSHFLOW_H

extern "C" {
#include "rtmp.h"
};

#include <pthread.h>
#include "RtmpPacketQueue.h"
#include "OnStartPushResultListener.h"
#include "OnPushDisconnectCall.h"

#define RTMP_SPS_PPS_EXTRA_BYTES_SIZE  16
#define RTMP_VIDEO_EXTRA_BYTES_SIZE  9
#define RTMP_AAC_EXTRA_BYTES_SIZE  2

#define RTMP_STREAM_CHANNEL_METADATA  0x03
#define RTMP_STREAM_CHANNEL_VIDEO     0x04
#define RTMP_STREAM_CHANNEL_AUDIO     0x05

class WePushFlow {

private:
    const char *LOG_TAG = "_WePushFlow";

    OnStartPushResultListener *onStartPushResultListener = NULL;
    OnPushDisconnectCall *onPushDisconnectCall = NULL;

    RTMP *rtmp = NULL;
    char *pushUrl = NULL;
    int connectTimeout = 5;// seconds

    int audioEncodeBitsFlag = 0x2;// 第2位标志 default 16bits
    int audioChannelFlag = 0x1;// 第1位标志 default stereo

    RtmpPacketQueue *queue = NULL;
    RTMPPacket *packet = NULL;

    bool isStarting = false;
    bool isStartSuccess = false;
    long startPushTime = 0;
    bool isShouldCallDisconnect = false;
    bool isShouldExit = false;

public:
    pthread_t pushThread;
    bool isPushThreadStarted = false;

public:
    WePushFlow(OnStartPushResultListener *startListener, OnPushDisconnectCall *disconnectCall);

    ~WePushFlow();

    void setPushUrl(char *url);

    void setConnectTimeout(int seconds);

    void setAudioEncodeBits(int audioEncodeBits);

    void setAudioChannels(int audioChannels);

    void startPush();

    void _loopPush();

    void pushSpsPps(char *sps, int spsLength, char *pps, int ppsLength);

    void pushVideoData(char *data, int dataLength, bool isKeyframe);

    void pushAudioData(char *data, int dataLength);

    void setStopFlag();

    void stopPush();

private:
    void handleOnStartPushFailed(char *error);

    void freeRTMP();

};


#endif //VIDEOMAKER_WEPUSHFLOW_H
