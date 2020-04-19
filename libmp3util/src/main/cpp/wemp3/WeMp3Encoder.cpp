//
// Created by WTZ on 2020/4/17.
//

#include <AndroidLog.h>
#include "WeMp3Encoder.h"

WeMp3Encoder::WeMp3Encoder(OnEncodeProgressListener *listener) {
    this->onEncodeProgressListener = listener;
}

WeMp3Encoder::~WeMp3Encoder() {
    enableEncodeFromPCMFile(false);
    stopEncodePCMBuffer();

    delete onEncodeProgressListener;
    onEncodeProgressListener = NULL;
}

void WeMp3Encoder::setOutChannelMode(int mode) {
    switch (mode) {
        case 0:
            this->outChannelMode = STEREO;
            break;

        case 1:
            this->outChannelMode = MONO;
            break;
    }
}

void WeMp3Encoder::setOutBitrateMode(int mode, int bitrate) {
    switch (mode) {
        case 0:
            // constant bitrate 从头到尾码率都是固定的，这种编码方式压缩出来的体积会很大
            // 音质相对于其他两种，会有些优势，但这个优势可能微乎其微。
            this->brMode = vbr_off;//CBR
            break;

        case 1:
            // Average Bitrate Lame会将该文件的85%用固定编码，然后对剩余15%进行动态优化
            // ABR 编码在速度上是 VBR 编码的 2~3 倍，在 128~256kbps 范围内质量要好于 CBR。
            this->brMode = vbr_abr;//ABR
            break;

        case 2:
            // Variable Bitrate 压缩软件在压缩时根据音频数据即时确定使用什么比特率。
            // 这是 MP3 以质量为前提兼顾文件大小的最佳编码模式
            this->brMode = vbr_mtrh;//VBR
            break;
    }
    this->outBitrate = bitrate;
}

void WeMp3Encoder::setOutQuality(int quality) {
    if (quality < 0 || quality > 9) {
        quality = 5;
    }
    this->outQuality = quality;
}

lame_t WeMp3Encoder::initLame(int inSampleRate, int inChannelNum) {
    lame_t lame = lame_init();
    if (lame == NULL) {
        LOGE(LOG_TAG, "lame_init return NULL");
        return NULL;
    }

    if (lame_set_in_samplerate(lame, inSampleRate) == -1) {
        LOGE(LOG_TAG, "lame_set_in_samplerate %d failed", inSampleRate);
        lame_close(lame);
        return NULL;
    }

    if (lame_set_out_samplerate(lame, inSampleRate) == -1) {
        LOGE(LOG_TAG, "lame_set_out_samplerate %d failed", inSampleRate);
        lame_close(lame);
        return NULL;
    }

    if (lame_set_num_channels(lame, inChannelNum) == -1) {
        LOGE(LOG_TAG, "lame_set_num_channels %d failed", inChannelNum);
        lame_close(lame);
        return NULL;
    }

    if (lame_set_mode(lame, outChannelMode) == -1) {
        LOGE(LOG_TAG, "lame_set_mode %d failed", outChannelMode);
        lame_close(lame);
        return NULL;
    }

    if (lame_set_VBR(lame, brMode) == -1) {
        LOGE(LOG_TAG, "lame_set_VBR %d failed", brMode);
        lame_close(lame);
        return NULL;
    }

    if (brMode == vbr_off) {
        if (lame_set_brate(lame, outBitrate) == -1) {
            LOGE(LOG_TAG, "lame_set_brate %d failed", outBitrate);
            lame_close(lame);
            return NULL;
        }
    } else if (brMode == vbr_abr) {
        if (lame_set_VBR_mean_bitrate_kbps(lame, outBitrate) == -1) {
            LOGE(LOG_TAG, "lame_set_VBR_mean_bitrate_kbps %d failed", outBitrate);
            lame_close(lame);
            return NULL;
        }
    }

    if (lame_set_quality(lame, outQuality) == -1) {
        LOGE(LOG_TAG, "lame_set_quality %d failed", outQuality);
        lame_close(lame);
        return NULL;
    }

    if (lame_init_params(lame) == -1) {
        LOGE(LOG_TAG, "lame_init_params failed");
        lame_close(lame);
        return NULL;
    }

    return lame;
}

void WeMp3Encoder::enableEncodeFromPCMFile(bool enable) {
    this->stopEncodeFromPCMFile = !enable;
}

void WeMp3Encoder::encodeFromPCMFile(const char *pcmFilePath, int inSampleRate, int inChannelNum,
                                     int bitsPerSample, bool hasWavHead, const char *savePath) {
    if (stopEncodeFromPCMFile) {
        LOGE(LOG_TAG, "invoke encodeFromPCMFile but stopEncodeFromPCMFile == true");
        return;
    }
    lame_t lame = initLame(inSampleRate, inChannelNum);
    if (lame == NULL) {
        if (onEncodeProgressListener != NULL) {
            onEncodeProgressListener->callback(3, savePath, 0, true);
        }
        return;
    }

    FILE *inFile = fopen(pcmFilePath, "rb");//只读方式打开一个二进制文件，不存在会报错
    if (inFile == NULL) {
        LOGE(LOG_TAG, "encodeFromPCMFile but pcm file open failed: %s", pcmFilePath);
        lame_close(lame);
        if (onEncodeProgressListener != NULL) {
            onEncodeProgressListener->callback(3, savePath, 0, true);
        }
        return;
    }
    if (hasWavHead) {
        fseek(inFile, 4*1024, SEEK_CUR);// wav 格式的文件要跳过文件头
    }

    FILE *outFile = fopen(savePath, "wb");//只写方式打开一个二进制文件，不存在会创建，存在会覆盖
    if (outFile == NULL) {
        LOGE(LOG_TAG, "encodeFromPCMFile but save file open failed: %s", savePath);
        lame_close(lame);
        if (onEncodeProgressListener != NULL) {
            onEncodeProgressListener->callback(3, savePath, 0, true);
        }
        return;
    }

    const int PCM_BUFFER_SIZE = MP3_BUFFER_SIZE * inChannelNum;// 取通道数的整数倍
    short int *pcmBuf = new short int[PCM_BUFFER_SIZE];
    unsigned char *mp3Buf = new unsigned char[MP3_BUFFER_SIZE];

    int bytesPerSample = bitsPerSample / 8;// 每个通道单次采样字节数
    int numSamplesPerChannel = 0;// 读到的 buffer 中对应每个通道有多少个采样

    int readBytesPerPcmElem = sizeof(short int) * inChannelNum;// 每次读够所有通道的数据
    int readMaxPcmElemCount = sizeof(short int) * PCM_BUFFER_SIZE / readBytesPerPcmElem;
    int readPcmElemCount = 0;
    int readPcmBytes = 0;
    int totalPcmBytesSize = 0;// 已有多少原始数据被编码
    int encodeSize = 0;

    LOGW(LOG_TAG, "encodeFromPCMFile start! source: %s", pcmFilePath);
    do {
        // 从 PCM 文件读取数据
        readPcmElemCount = fread(pcmBuf, readBytesPerPcmElem, readMaxPcmElemCount, inFile);
        readPcmBytes = readBytesPerPcmElem * readPcmElemCount;
        totalPcmBytesSize += readPcmBytes;
        if (onEncodeProgressListener != NULL) {
            onEncodeProgressListener->callback(3, savePath, totalPcmBytesSize, false);
        }

        // 把 PCM 编码为 MP3
        numSamplesPerChannel = readPcmBytes / bytesPerSample / inChannelNum;
//            encodeSize = lame_encode_buffer(lame, pcmBuf, NULL, numSamplesPerChannel, mp3Buf,
//                                            MP3_BUFFER_SIZE);
        encodeSize = lame_encode_buffer_interleaved(lame, pcmBuf, numSamplesPerChannel, mp3Buf,
                                                    MP3_BUFFER_SIZE);
        // 把编码的 MP3 数据写到文件里
        fwrite(mp3Buf, 1, encodeSize, outFile);

        if (stopEncodeFromPCMFile) {
            break;
        }
    } while (readPcmElemCount != 0);

    // 最后把缓冲区的数据也写入文件
    encodeSize = lame_encode_flush(lame, mp3Buf, MP3_BUFFER_SIZE);
    fwrite(mp3Buf, 1, encodeSize, outFile);

    // 释放资源
    fclose(outFile);
    fclose(inFile);
    lame_close(lame);

    LOGW(LOG_TAG, "encodeFromPCMFile complete! save: %s", savePath);
    if (onEncodeProgressListener != NULL) {
        onEncodeProgressListener->callback(3, savePath, totalPcmBytesSize, true);
    }
}

bool WeMp3Encoder::startEncodePCMBuffer(int inSampleRate, int inChannelNum, int bitsPerSample,
                                        const char *savePath) {
    this->inChannelNumForPCMBuf = inChannelNum;
    lameForPCMBuf = initLame(inSampleRate, inChannelNum);
    if (lameForPCMBuf == NULL) {
        return false;
    }

    outFileForPCMBuf = fopen(savePath, "wb");
    if (outFileForPCMBuf == NULL) {
        LOGE(LOG_TAG, "startEncodePCMBuffer but save file open failed: %s", savePath);
        stopEncodePCMBuffer();
        return false;
    }

    mp3BufForPCMBuf = new unsigned char[MP3_BUFFER_SIZE * inChannelNum];
    bytesPerSampleForPCMBuf = bitsPerSample / 8;
    return true;
}

void WeMp3Encoder::encodeFromPCMBuffer(short *pcmBuffer, int bufferSize) {
    if (lameForPCMBuf == NULL) {
        LOGE(LOG_TAG, "encodeFromPCMBuffer but lame == NULL");
        return;
    }

    int pcmBytes = sizeof(short) * bufferSize;
    int numSamplesPerChannel = pcmBytes / bytesPerSampleForPCMBuf / inChannelNumForPCMBuf;
    // return code  number of bytes output in mp3buf. Can be 0
    //        -1:  mp3buf was too small
    //        -2:  malloc() problem
    //        -3:  lame_init_params() not called
    //        -4:  psycho acoustic problems
    int encodeSize = lame_encode_buffer_interleaved(lameForPCMBuf, pcmBuffer, numSamplesPerChannel,
                                                    mp3BufForPCMBuf, MP3_BUFFER_SIZE);
    if (encodeSize > 0) {
        fwrite(mp3BufForPCMBuf, 1, encodeSize, outFileForPCMBuf);
    } else if (encodeSize < 0) {
        LOGE(LOG_TAG, "lame_encode_buffer_interleaved failed! ret=%d", encodeSize);
    }
}

void WeMp3Encoder::stopEncodePCMBuffer() {
    if (lameForPCMBuf == NULL) {
        LOGW(LOG_TAG, "stopEncodePCMBuffer but lame == NULL");
        return;
    }
    int encodeSize = lame_encode_flush(lameForPCMBuf, mp3BufForPCMBuf, MP3_BUFFER_SIZE);
    if (encodeSize > 0) {
        fwrite(mp3BufForPCMBuf, 1, encodeSize, outFileForPCMBuf);
    } else if (encodeSize < 0) {
        LOGE(LOG_TAG, "lame_encode_flush failed! ret=%d", encodeSize);
    }

    delete mp3BufForPCMBuf;
    mp3BufForPCMBuf = NULL;

    fclose(outFileForPCMBuf);
    outFileForPCMBuf = NULL;

    lame_close(lameForPCMBuf);
    lameForPCMBuf = NULL;
}
