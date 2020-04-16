//
// Created by WTZ on 2020/4/15.
//

#ifndef VIDEOMAKER_DOUBLEBUFFER_H
#define VIDEOMAKER_DOUBLEBUFFER_H

#include <stddef.h>

#define VIDMK_DOUBLE_BUFFER_COUNT 2

class DoubleBuffer {

private:
    short **buffers = NULL;
    int index = 0;
    int bytesSizePerBuffer = 0;

public:
    DoubleBuffer(int elemCountPerBuffer);

    ~DoubleBuffer();

    short *getFreeBuffer();

    short *getDataBuffer();

    int getBytesSizePerBuffer();

};


#endif //VIDEOMAKER_DOUBLEBUFFER_H
