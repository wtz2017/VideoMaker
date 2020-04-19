package com.wtz.libmp3util;


import com.wtz.libmp3util.utlis.LogUtils;

/**
 * Created by WTZ on 2020/4/17.
 * <p>
 * 1. WeMp3Encoder 支持同一进程多实例操作。
 * <p>
 * 2. MP3 编码数据来源分两种：
 * - PCM 文件 / WAV 文件
 * 每个文件独立编码，可以多线程多任务多文件操作。
 * 适用场景：批量文件异步编码。
 * 支持多线程操作。
 * <p>
 * - PCM buffer
 * 从 PCM buffer 取数据编码的方法分成了多步操作，涉及状态切换，适合单任务执行。
 * 适用场景：一边录音一边保存 MP3。
 * 支持多线程操作。
 * <p>
 * 以上方法没有新开线程，是耗时操作，调用者根据需要创建线程调用
 */
public class WeMp3Encoder {

    private static final String TAG = WeMp3Encoder.class.getSimpleName();

    private final int mObjKey = this.hashCode();

    private boolean isPcmBufEncoderStarted = false;
    private boolean isPcmBufEncoding = false;

    private EncodeProgressListener mEncodeListener;

    static {
        System.loadLibrary("wemp3");
    }

    private native boolean nativeInitMp3Encoder(int objKey);

    private native void nativeSetOutChannelMode(int objKey, int mode);

    private native void nativeSetOutBitrateMode(int objKey, int mode, int bitrate);

    private native void nativeSetOutQuality(int objKey, int quality);

    private native void nativeEnableEncodeFromPCMFile(int objKey, boolean enable);

    /**
     * 每个 PCM 文件独立编码，可以多线程多任务多文件操作
     * 此方法没有新开线程，是耗时操作，调用者根据需要创建线程调用
     *
     * @param hasWavHead true 表示在 PCM 文件上加了 WAV 头
     */
    private native void nativeEncodeFromPCMFile(int objKey, String pcmFilePath, int inSampleRate, int inChannelNum,
                                                int bitsPerSample, boolean hasWavHead, String savePath);

    /**
     * 从 PCM buffer 取数据编码的方法分成了多步操作，涉及状态切换，适合单任务执行
     */
    private native boolean nativeStartEncodePCMBuffer(int objKey, int inSampleRate, int inChannelNum,
                                                      int bitsPerSample, String savePath);

    /**
     * 从 PCM buffer 取数据编码的方法分成了多步操作，涉及状态切换，适合单任务执行
     * 此方法没有新开线程，是耗时操作，调用者根据需要创建线程调用
     */
    private native void nativeEncodeFromPCMBuffer(int objKey, short[] pcmBuffer, int bufferSize);

    /**
     * 从 PCM buffer 取数据编码的方法分成了多步操作，涉及状态切换，适合单任务执行
     */
    private native void nativeStopEncodePCMBuffer(int objKey);

    private native void nativeReleaseMp3Encoder(int objKey);

    public enum ChannelMode {
        STEREO(0), MONO(1);

        private int nativeValue;

        ChannelMode(int nativeValue) {
            this.nativeValue = nativeValue;
        }

        public int getNativeValue() {
            return nativeValue;
        }
    }

    public enum BitrateMode {
        CBR(0), ABR(1), VBR(2);

        private int nativeValue;

        BitrateMode(int nativeValue) {
            this.nativeValue = nativeValue;
        }

        public int getNativeValue() {
            return nativeValue;
        }
    }

    /**
     * quality=0..9.  0=best (very slow).  9=worst.
     * recommended:
     * 2     near-best quality, not too slow
     * 5     good quality, fast
     * 7     ok quality, really fast
     */
    public enum Quality {
        L0(0), L1(1), L2(2), L3(3),
        L4(4), L5(5), L6(6), L7(7),
        L8(8), L9(9);

        private int nativeValue;

        Quality(int nativeValue) {
            this.nativeValue = nativeValue;
        }

        public int getNativeValue() {
            return nativeValue;
        }
    }

    public interface EncodeProgressListener {
        void onEncodeProgressChanged(String saveFilePath, int totalBytes, boolean isComplete);
    }

    public void setEncodeListener(EncodeProgressListener listener) {
        this.mEncodeListener = listener;
    }

    public WeMp3Encoder() {
        nativeInitMp3Encoder(mObjKey);
    }

    public void setOutChannelMode(ChannelMode mode) {
        nativeSetOutChannelMode(mObjKey, mode.getNativeValue());
    }

    /**
     * @param mode    BitrateMode
     * @param bitrate kbps
     */
    public void setOutBitrateMode(BitrateMode mode, int bitrate) {
        nativeSetOutBitrateMode(mObjKey, mode.getNativeValue(), bitrate);
    }

    public void setOutQuality(Quality quality) {
        nativeSetOutQuality(mObjKey, quality.getNativeValue());
    }

    public void enableEncodeFromPCMFile(boolean enable) {
        nativeEnableEncodeFromPCMFile(mObjKey, enable);
    }

    public void encodeFromPCMFile(String pcmFilePath, int inSampleRate, int inChannelNum,
                                  int bitsPerSample, String savePath) {
        nativeEncodeFromPCMFile(mObjKey, pcmFilePath, inSampleRate, inChannelNum, bitsPerSample,
                false, savePath);
    }

    public void encodeFromWAVFile(String pcmFilePath, int inSampleRate, int inChannelNum,
                                  int bitsPerSample, String savePath) {
        nativeEncodeFromPCMFile(mObjKey, pcmFilePath, inSampleRate, inChannelNum, bitsPerSample,
                true, savePath);
    }

    private void onNativeEncodeProgressChanged(String saveFilePath, int totalBytes, boolean isComplete) {
        if (mEncodeListener != null) {
            mEncodeListener.onEncodeProgressChanged(saveFilePath, totalBytes, isComplete);
        }
    }

    public boolean startEncodePCMBuffer(int inSampleRate, int inChannelNum,
                                        int bitsPerSample, String savePath) {
        LogUtils.e(TAG, "startEncodePCMBuffer " + inSampleRate + "Hz "
                + inChannelNum + "Channels " + bitsPerSample + "bits save:" + savePath);
        synchronized (this) {
            if (isPcmBufEncoderStarted) {// 检查重复初始化
                LogUtils.e(TAG, "No need to startEncodePCMBuffer because it's already started! ");
                return true;
            }
            // 此处放在 synchronized 内部是为了与 stopEncodePCMBuffer 同步
            isPcmBufEncoderStarted = nativeStartEncodePCMBuffer(mObjKey, inSampleRate, inChannelNum, bitsPerSample, savePath);
        }
        return isPcmBufEncoderStarted;
    }

    public void encodeFromPCMBuffer(short[] pcmBuffer, int bufferSize) {
        synchronized (this) {
            if (!isPcmBufEncoderStarted) {// 检查没有初始化不能工作
                LogUtils.e(TAG, "encodeFromPCMBuffer but PcmBuffer Encoder is not started! ");
                return;
            }
            isPcmBufEncoding = true;// 把工作标志置true
        }

        nativeEncodeFromPCMBuffer(mObjKey, pcmBuffer, bufferSize);

        // 由于 stopEncodePCMBuffer 会在 synchronized 中阻塞等待此标志置 false，
        // 因此这里不能加 synchronized
        isPcmBufEncoding = false;// 工作完成后把工作标志置true
    }

    public void stopEncodePCMBuffer() {
        LogUtils.w(TAG, "stopEncodePCMBuffer...isPcmBufEncoderStarted=" + isPcmBufEncoderStarted);
        synchronized (this) {
            if (!isPcmBufEncoderStarted) {
                LogUtils.w(TAG, "no need to stopEncodePCMBuffer");
                return;
            }
            isPcmBufEncoderStarted = false;// 首先置未初始化标志，以拦截即将新增的后续工作

            // 工作结束后再释放资源
            try {// try 要放在 while 的外边，避免死循环
                while (isPcmBufEncoding) {
                    Thread.sleep(10);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // 此处放在 synchronized 内部是为了与 startEncodePCMBuffer 同步
            nativeStopEncodePCMBuffer(mObjKey);
        }
        LogUtils.w(TAG, "stopEncodePCMBuffer...end!");
    }

    public void release() {
        enableEncodeFromPCMFile(false);
        stopEncodePCMBuffer();
        nativeReleaseMp3Encoder(mObjKey);
    }

}
