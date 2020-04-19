//
// Created by WTZ on 2020/4/17.
//

#ifndef VIDEOMAKER_WEMP3ENCODER_H
#define VIDEOMAKER_WEMP3ENCODER_H

#include "lame.h"
#include "OnEncodeProgressListener.h"

/**
 * lame.h: lame_encode_buffer:
 *
 * The required mp3buf_size can be computed from num_samples,
 * samplerate and encoding rate, but here is a worst case estimate:
 *
 * mp3buf_size in bytes = 1.25*num_samples + 7200
 *
 * I think a tighter bound could be:  (mt, March 2000)
 * MPEG1:
 *    num_samples*(bitrate/8)/samplerate + 4*1152*(bitrate/8)/samplerate + 512
 * MPEG2:
 *    num_samples*(bitrate/8)/samplerate + 4*576*(bitrate/8)/samplerate + 256
 *
 * but test first if you use that!
 */
#define MP3_BUFFER_SIZE 8192

class WeMp3Encoder {

private:
    const char *LOG_TAG = "_WeMp3Encoder";

    OnEncodeProgressListener *onEncodeProgressListener = NULL;
    bool stopEncodeFromPCMFile = false;

    // 公共输出参数
    MPEG_mode outChannelMode = STEREO;// 默认使用立体声
    vbr_mode_e brMode = vbr_mtrh;// 默认使用 VBR
    int outBitrate = 128;// 默认使用 128Kbps
    int outQuality = 5;// 默认使用中等质量

    // For PCM buffer Operate
    // 从 PCM buffer 取数据编码的方法分成了多步操作，涉及状态切换，只能单任务执行
    int inChannelNumForPCMBuf = 0;
    int bytesPerSampleForPCMBuf = 0;
    lame_t lameForPCMBuf = NULL;
    unsigned char *mp3BufForPCMBuf = NULL;
    FILE *outFileForPCMBuf = NULL;

public:
    WeMp3Encoder(OnEncodeProgressListener *listener);

    ~WeMp3Encoder();

    /**
     * @param mode 0:STEREO 1:MONO
     */
    void setOutChannelMode(int mode);

    /**
     * @param mode     0:CBR 1:ABR 2:VBR
     * @param bitrate
     */
    void setOutBitrateMode(int mode, int bitrate);

    /**
     * @param quality quality=0..9.  0=best (very slow).  9=worst.
     * recommended:
     *          2     near-best quality, not too slow
     *          5     good quality, fast
     *          7     ok quality, really fast
     */
    void setOutQuality(int quality);

    /**
     * 开启或停止所有从 PCM 文件读取数据编码 MP3 的工作
     */
    void enableEncodeFromPCMFile(bool enable);

    /**
     * 每个 PCM 文件独立编码，可以多任务多文件操作
     */
    void encodeFromPCMFile(const char *pcmFilePath, int inSampleRate, int inChannelNum,
                           int bitsPerSample, bool hasWavHead, const char *savePath);

    /**
     * ！！！注意：从 PCMBuffer 取数据编码的方法分成了多步操作，涉及状态切换，适合单任务执行
     */
    bool startEncodePCMBuffer(int inSampleRate, int inChannelNum, int bitsPerSample,
                              const char *savePath);

    /**
     * ！！！注意：从 PCMBuffer 取数据编码的方法分成了多步操作，涉及状态切换，适合单任务执行
     * @param pcmBuffer
     * @param bufferSize pcmBuffer 的 short 元素个数
     */
    void encodeFromPCMBuffer(short *pcmBuffer, int bufferSize);

    /**
     * ！！！注意：从 PCMBuffer 取数据编码的方法分成了多步操作，涉及状态切换，适合单任务执行
     */
    void stopEncodePCMBuffer();

private:
    lame_t initLame(int inSampleRate, int inChannelNum);

};


#endif //VIDEOMAKER_WEMP3ENCODER_H
