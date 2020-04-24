//
// Created by WTZ on 2020/4/20.
//

#include "RtmpPacketQueue.h"

RtmpPacketQueue::RtmpPacketQueue() {
    allowOperation = true;
    pthread_mutex_init(&mutex, NULL);
    pthread_cond_init(&condition, NULL);
}

RtmpPacketQueue::~RtmpPacketQueue() {
    allowOperation = false;
    releaseQueue();
    pthread_cond_destroy(&condition);
    pthread_mutex_destroy(&mutex);
}

void RtmpPacketQueue::setAllowOperation(bool allow) {
    this->allowOperation = allow;
}

void RtmpPacketQueue::setProductDataComplete(bool complete) {
    pthread_mutex_lock(&mutex);

    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "setProductDataComplete: %d", complete);
    }
    productDataComplete = complete;// 作用：当完成标志位设置早于消费者读数据时，消费者不会再 wait
    if (complete) {// 作用：当完成标志位设置晚于消费者 wait 时，可以通知消费者退出
        pthread_cond_signal(&condition);
    }

    pthread_mutex_unlock(&mutex);
}

bool RtmpPacketQueue::isProductDataComplete() {
    bool ret;
    pthread_mutex_lock(&mutex);
    ret = productDataComplete;
    pthread_mutex_unlock(&mutex);
    return ret;
}

void RtmpPacketQueue::putPacket(RTMPPacket *packet) {
    pthread_mutex_lock(&mutex);

    queue.push(packet);
    if (LOG_REPEAT_DEBUG) {
        LOGD(LOG_TAG, "putAVpacket current size：%d", queue.size());
    }
    pthread_cond_signal(&condition);

    pthread_mutex_unlock(&mutex);
}

RTMPPacket *RtmpPacketQueue::getPacket() {
    pthread_mutex_lock(&mutex);

    RTMPPacket *packet = NULL;
    // 循环是为了在队列为空导致阻塞等待后被唤醒时继续取下一个
    while (allowOperation) {
        if (!queue.empty()) {
            packet = queue.front();
            queue.pop();
            break;
        } else if (!productDataComplete) {
            pthread_cond_wait(&condition, &mutex);
        } else {
            // 队列为空，也不再生产数据，那就退出
            break;
        }
    }

    pthread_mutex_unlock(&mutex);
    return packet;
}

int RtmpPacketQueue::getQueueSize() {
    int size = 0;
    pthread_mutex_lock(&mutex);
    size = queue.size();
    pthread_mutex_unlock(&mutex);
    return size;
}

void RtmpPacketQueue::clearQueue() {
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "clearQueue...");
    }
    pthread_cond_signal(&condition);
    pthread_mutex_lock(&mutex);

    RTMPPacket *packet = NULL;
    while (!queue.empty()) {
        packet = queue.front();
        queue.pop();
        RTMPPacket_Free(packet);
    }
    packet = NULL;

    pthread_mutex_unlock(&mutex);
    if (LOG_DEBUG) {
        LOGD(LOG_TAG, "clearQueue finished");
    }
}

void RtmpPacketQueue::releaseQueue() {
    clearQueue();
    std::queue<RTMPPacket *> empty;
    swap(empty, queue);
}
