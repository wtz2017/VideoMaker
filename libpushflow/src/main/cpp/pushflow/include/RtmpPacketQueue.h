//
// Created by WTZ on 2020/4/20.
//

#ifndef VIDEOMAKER_RTMPPACKETQUEUE_H
#define VIDEOMAKER_RTMPPACKETQUEUE_H

#include "queue"
#include <pthread.h>
#include "AndroidLog.h"

extern "C"
{
#include "rtmp.h"
};

class RtmpPacketQueue {

private:
    const char *LOG_TAG = "RtmpPacketQueue";

    bool allowOperation = true;
    bool productDataComplete = false;

    std::queue<RTMPPacket *> queue;
    pthread_mutex_t mutex;
    pthread_cond_t condition;

public:
    static const int MAX_CACHE_NUM = 40;//TODO TEST

public:
    RtmpPacketQueue();

    ~RtmpPacketQueue();

    /**
     * 是否允许操作队列
     *
     * @param allow
     */
    void setAllowOperation(bool allow);

    /**
     * 生产者线程通知是否还有数据可以入队，防止最后消费者线程一直阻塞等待
     */
    void setProductDataComplete(bool complete);

    bool isProductDataComplete();

    void putPacket(RTMPPacket *packet);

    RTMPPacket *getPacket();

    int getQueueSize();

    void clearQueue();

private:
    void releaseQueue();

};


#endif //VIDEOMAKER_RTMPPACKETQUEUE_H
