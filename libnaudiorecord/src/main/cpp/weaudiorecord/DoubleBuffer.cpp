//
// Created by WTZ on 2020/4/15.
//

#include "DoubleBuffer.h"

DoubleBuffer::DoubleBuffer(int elemCountPerBuffer) {
    this->bytesSizePerBuffer = elemCountPerBuffer * sizeof(short);
    buffers = new short *[VIDMK_DOUBLE_BUFFER_COUNT];
    for (int i = 0; i < VIDMK_DOUBLE_BUFFER_COUNT; i++) {
        buffers[i] = new short[elemCountPerBuffer];
    }
}

DoubleBuffer::~DoubleBuffer() {
    for (int i = 0; i < VIDMK_DOUBLE_BUFFER_COUNT; i++) {
        delete buffers[i];
        buffers[i] = NULL;
    }
    delete buffers;
    buffers = NULL;
}

short *DoubleBuffer::getFreeBuffer() {
    index++;
    if (index >= VIDMK_DOUBLE_BUFFER_COUNT) {
        index = 0;
    }
    return buffers[index];
}

short *DoubleBuffer::getDataBuffer() {
    return buffers[index];
}

int DoubleBuffer::getBytesSizePerBuffer() {
    return bytesSizePerBuffer;
}
