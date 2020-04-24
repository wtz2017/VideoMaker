//
// Created by WTZ on 2020/4/20.
//

#include "WePushFlow.h"

WePushFlow::WePushFlow(OnStartPushResultListener *listener) {
    this->onStartPushResultListener = listener;
}

WePushFlow::~WePushFlow() {
    stopPush();
    if (pushUrl != NULL) {
        delete[] pushUrl;// pushUrl 是 JNI 中通过 new char[] 创建的
        pushUrl = NULL;
    }
    delete onStartPushResultListener;
    onStartPushResultListener = NULL;
}

void WePushFlow::setPushUrl(char *url) {
    if (this->pushUrl != NULL) {
        // 先释放旧的 pushUrl
        delete[] this->pushUrl;// pushUrl 是 JNI 中通过 new char[] 创建的
    }
    this->pushUrl = url;
}

void WePushFlow::setConnectTimeout(int seconds) {
    this->connectTimeout = seconds;
}

void WePushFlow::setAudioEncodeBits(int audioEncodeBits) {
    if (audioEncodeBits == 8) {
        // 8bits
        audioEncodeBitsFlag = 0x0;
    } else {
        // 16bits
        audioEncodeBitsFlag = 0x2;
    }
}

void WePushFlow::setAudioChannels(int audioChannels) {
    if (audioChannels == 1) {
        // mono
        audioChannelFlag = 0x0;
    } else {
        // stereo
        audioChannelFlag = 0x1;
    }
}

void *pushThreadCall(void *data) {
    if (LOG_DEBUG) {
        LOGW("pushThreadCall", "pushThread run...");
    }
    WePushFlow *wePushFlow = static_cast<WePushFlow *>(data);
    wePushFlow->_loopPush();

    if (LOG_DEBUG) {
        LOGW("pushThreadCall", "pushThread exit...");
    }
    wePushFlow->isPushThreadStarted = false;
    pthread_exit(&wePushFlow->pushThread);
}

void WePushFlow::startPush() {
    if (isStartSuccess || isStarting) {
        LOGW(LOG_TAG, "Do not call startPush repeatedly! It's already started!");
        return;
    }
    isStarting = true;
    isShouldExit = false;

    if (pushUrl == NULL) {
        LOGE(LOG_TAG, "startPush failed! The push url is empty");
        handleOnStartPushFailed("The push url is empty");
        return;
    }

    rtmp = RTMP_Alloc();
    RTMP_Init(rtmp);
    rtmp->Link.timeout = connectTimeout;
    rtmp->Link.lFlags |= RTMP_LF_LIVE;// stream is live

    if (!RTMP_SetupURL(rtmp, pushUrl)) {
        LOGE(LOG_TAG, "RTMP_SetupURL failed:%s", pushUrl);
        handleOnStartPushFailed("RTMP_SetupURL failed");
        return;
    }

    RTMP_EnableWrite(rtmp);

    if (!RTMP_Connect(rtmp, NULL)) {
        LOGE(LOG_TAG, "RTMP_Connect failed:%s", pushUrl);
        handleOnStartPushFailed("RTMP_Connect failed");
        return;
    }

    if (!RTMP_ConnectStream(rtmp, 0)) {
        LOGE(LOG_TAG, "RTMP_ConnectStream failed:%s", pushUrl);
        handleOnStartPushFailed("RTMP_ConnectStream failed");
        return;
    }

    queue = new RtmpPacketQueue();
    startPushTime = RTMP_GetTime();
    isStartSuccess = true;
    isStarting = false;
    if (onStartPushResultListener != NULL) {
        onStartPushResultListener->callback(2, true, "startPush success!");
    }

    pushThread = pthread_create(&pushThread, NULL, pushThreadCall, this);
    isPushThreadStarted = true;
}

void WePushFlow::handleOnStartPushFailed(char *error) {
    freeRTMP();
    isStarting = false;
    if (onStartPushResultListener != NULL) {
        onStartPushResultListener->callback(2, false, error);
    }
}

void WePushFlow::_loopPush() {
    while (!isShouldExit) {
        packet = queue->getPacket();
        if (packet != NULL) {
            int result = RTMP_SendPacket(rtmp, packet, 1);
            if (result == FALSE) {
                LOGE(LOG_TAG, "RTMP_SendPacket failed! IsConnected=%d", result, RTMP_IsConnected(rtmp));
            }
            RTMPPacket_Free(packet);
            free(packet);
            packet = NULL;
        }
    }

    freeRTMP();
    queue->clearQueue();
    delete queue;
    queue = NULL;
}

void WePushFlow::freeRTMP() {
    if (rtmp == NULL) {
        return;
    }
    RTMP_Close(rtmp);
    RTMP_Free(rtmp);
    rtmp = NULL;
}

void WePushFlow::pushSpsPps(char *sps, int spsLength, char *pps, int ppsLength) {
    if (!isStartSuccess) {
        LOGW(LOG_TAG, "pushSpsPps but is not start success yet");
        return;
    }

    // 去掉帧界定符
    // 3字节的 0x000001 NALU 起始标识只有一种场合下使用，就是一个完整的帧被编为多个 slice 时，
    // 包含这些 slice 的 NALU 使用 3 字节起始码。其余场合都是 4 字节 0x00000001 NALU 起始标识。
    if (sps[2] == 0x00) {/*00 00 00 01*/
        sps += 4;
        spsLength -= 4;
    } else if (sps[2] == 0x01) {/*00 00 01*/
        sps += 3;
        spsLength -= 3;
    }
    if (pps[2] == 0x00) {/*00 00 00 01*/
        pps += 4;
        ppsLength -= 4;
    } else if (pps[2] == 0x01) {/*00 00 01*/
        pps += 3;
        ppsLength -= 3;
    }

    int bodySize = spsLength + ppsLength + RTMP_SPS_PPS_EXTRA_BYTES_SIZE;
    RTMPPacket *packet = static_cast<RTMPPacket *>(malloc(sizeof(RTMPPacket)));
    if (!RTMPPacket_Alloc(packet, bodySize)) {
        LOGE(LOG_TAG, "RTMPPacket_Alloc failed! spsLength=%d ppsLength=%d", spsLength, ppsLength);
        free(packet);
        return;
    }

    RTMPPacket_Reset(packet);

    char *body = packet->m_body;
    int i = 0;

    /********** 以下是基本信息 **********/
    body[i++] = 0x17;// 4bit(frame type: 1 关键帧) + 4bit(codec id: 7 AVC)

    body[i++] = 0x00;// 8bit(packet type: 0 配置信息 sps pps)

    // 24bit(composite time: 00 00 00)
    body[i++] = 0x00;
    body[i++] = 0x00;
    body[i++] = 0x00;

    /********** 以下是 AVCDecoderConfigurationRecord **********/
    body[i++] = 0x01;// 8bit(configuration Version: 1)

    body[i++] = sps[1];// 8bit(AVCProfile Indication)
    body[i++] = sps[2];// 8bit(profile compatibility)
    body[i++] = sps[3];// 8bit(AVCLevel Indication)

    body[i++] = 0xFF;// 6bit(reserved ‘111111’b) + 2bit(lengthSizeMinusOne: 3 NALU数据长度占用4个字节)

    body[i++] = 0xE1;// 3bit(reserved ‘111’b) + 5bit(SPS Num: 1)

    // 16bit(SPS Length)
    body[i++] = (spsLength >> 8) & 0xff;
    body[i++] = spsLength & 0xff;

    memcpy(&body[i], sps, spsLength);// SPS NALU Data
    i += spsLength;

    body[i++] = 0x01;// 8bit(PPS Num: 1)

    // 16bit(PPS Length)
    body[i++] = (ppsLength >> 8) & 0xff;
    body[i++] = ppsLength & 0xff;

    memcpy(&body[i], pps, ppsLength);// PPS NALU Data

    packet->m_packetType = RTMP_PACKET_TYPE_VIDEO;
    packet->m_nBodySize = bodySize;
    packet->m_nTimeStamp = 0;// SPS/PPS 不用时间
    packet->m_hasAbsTimestamp = 0;// 没有绝对时间
    packet->m_nChannel = RTMP_STREAM_CHANNEL_VIDEO;
    packet->m_headerType = RTMP_PACKET_SIZE_MEDIUM;
    packet->m_nInfoField2 = rtmp->m_stream_id;

    queue->putPacket(packet);
}

void WePushFlow::pushVideoData(char *data, int dataLength, bool isKeyframe) {
    if (!isStartSuccess) {
        LOGW(LOG_TAG, "pushVideoData but is not start success yet");
        return;
    }

    // 去掉帧界定符
    // 3字节的 0x000001 NALU 起始标识只有一种场合下使用，就是一个完整的帧被编为多个 slice 时，
    // 包含这些 slice 的 NALU 使用 3 字节起始码。其余场合都是 4 字节 0x00000001 NALU 起始标识。
    if (data[2] == 0x00) {/*00 00 00 01*/
        data += 4;
        dataLength -= 4;
    } else if (data[2] == 0x01) {/*00 00 01*/
        data += 3;
        dataLength -= 3;
    }
    // 如果不用外传参数 isKeyframe，也可以内部判断：
    // int type = data[0] & 0x1f;// 要使用去掉界定符后的第1个字节低5位来判断
    // isKeyframe = type == NAL_SLICE_IDR;// NAL_SLICE_IDR = 5 关键帧

    int bodySize = dataLength + RTMP_VIDEO_EXTRA_BYTES_SIZE;
    RTMPPacket *packet = static_cast<RTMPPacket *>(malloc(sizeof(RTMPPacket)));
    if (!RTMPPacket_Alloc(packet, bodySize)) {
        LOGE(LOG_TAG, "RTMPPacket_Alloc failed! dataLength=%d", dataLength);
        free(packet);
        return;
    }

    RTMPPacket_Reset(packet);

    char *body = packet->m_body;
    int i = 0;

    /********** 以下是基本信息 **********/
    if (isKeyframe) {
        body[i++] = 0x17;// 4bit(frame type: 1 关键帧) + 4bit(codec id: 7 AVC)
    } else {
        body[i++] = 0x27;// 4bit(frame type: 2 非关键帧) + 4bit(codec id: 7 AVC)
    }

    body[i++] = 0x01;// 8bit(packet type: 1 纯视频数据)

    // 24bit(composite time: 00 00 00)
    body[i++] = 0x00;
    body[i++] = 0x00;
    body[i++] = 0x00;

    /********** 以下是 H.264 原始数据信息 **********/
    // 32bit(Video Data Length)
    body[i++] = (dataLength >> 24) & 0xff;
    body[i++] = (dataLength >> 16) & 0xff;
    body[i++] = (dataLength >> 8) & 0xff;
    body[i++] = dataLength & 0xff;

    memcpy(&body[i], data, dataLength);// Video NALU Data

    packet->m_packetType = RTMP_PACKET_TYPE_VIDEO;
    packet->m_nBodySize = bodySize;
    packet->m_nTimeStamp = RTMP_GetTime() - startPushTime;// 相对时间
    packet->m_hasAbsTimestamp = 0;// 没有绝对时间
    packet->m_nChannel = RTMP_STREAM_CHANNEL_VIDEO;
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_nInfoField2 = rtmp->m_stream_id;

    queue->putPacket(packet);
}

void WePushFlow::pushAudioData(char *data, int dataLength) {
    if (!isStartSuccess) {
        LOGW(LOG_TAG, "pushAudioData but is not start success yet");
        return;
    }

    int bodySize = dataLength + RTMP_AAC_EXTRA_BYTES_SIZE;
    RTMPPacket *packet = static_cast<RTMPPacket *>(malloc(sizeof(RTMPPacket)));
    if (!RTMPPacket_Alloc(packet, bodySize)) {
        LOGE(LOG_TAG, "RTMPPacket_Alloc failed! dataLength=%d", dataLength);
        free(packet);
        return;
    }

    RTMPPacket_Reset(packet);

    char *body = packet->m_body;

    // 4bit(sound format: 10 表示AAC) + 2bit(sampleRate: 3 表示44K)
    // + 1bit(sound size: 0表示8bits, 1表示16bits) + 1bit(sound type: 0表示单声道, 1表双通道)
    body[0] = 0xAC | audioEncodeBitsFlag | audioChannelFlag;

    body[1] = 0x01;// 8bit(AAC packet type: 1 表示AAC原始音频数据)

    memcpy(&body[2], data, dataLength);// AAC 原始音频数据

    packet->m_packetType = RTMP_PACKET_TYPE_AUDIO;
    packet->m_nBodySize = bodySize;
    packet->m_nTimeStamp = RTMP_GetTime() - startPushTime;
    packet->m_hasAbsTimestamp = 0;// 没有绝对时间
//    packet->m_nChannel = RTMP_STREAM_CHANNEL_VIDEO;
    packet->m_nChannel = RTMP_STREAM_CHANNEL_AUDIO;
    packet->m_headerType = RTMP_PACKET_SIZE_MEDIUM;
    packet->m_nInfoField2 = rtmp->m_stream_id;

    queue->putPacket(packet);
}

void WePushFlow::setStopFlag() {
    LOGW(LOG_TAG, "setStopFlag...isPushThreadStarted=%d", isPushThreadStarted);
    isShouldExit = true;
    isStartSuccess = false;
}

void WePushFlow::stopPush() {
    LOGW(LOG_TAG, "stopPush...isPushThreadStarted=%d", isPushThreadStarted);
    isShouldExit = true;
    isStartSuccess = false;
    if (queue != NULL) {
        queue->setAllowOperation(false);
        queue->setProductDataComplete(true);
    }
    if (isPushThreadStarted) {
        pthread_join(pushThread, NULL);// 阻塞等待子线程结束
    }
    LOGW(LOG_TAG, "stopPush complete");
}
