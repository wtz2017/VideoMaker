package com.wtz.libnaudiorecord;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import com.wtz.libnaudiorecord.utlis.LogUtils;

public class WeNAudioRecorder {
    private static final String TAG = "WeNAudioRecorder";

    static {
        System.loadLibrary("we_audio_record");
    }

    private native boolean nativeInitRecorder();

    private native boolean nativeInitRecorder2(
            int sampleRateInHz, int channelLayout, int encodingBits
    );

    private native int nativeGetSampleRate();

    private native int nativeGetChannelNums();

    private native int nativeGetBitsPerSample();

    private native boolean nativeStartRecord();

    private native boolean nativeResumeRecord();

    private native void nativePauseRecord();

    private native void nativeStopRecord();

    private native void nativeReleaseRecorder();

    private boolean constructHasParams;

    private SampleRate mSampleRate;
    private ChannelLayout mChannelLayout;
    private EncodingBits mEncodingBits;
    private int mSampleRateInHz = 0;
    private int mChannelNum = 0;
    private int mBitsPerSample = 0;
    private int mAudioBytesPerSecond;
    private long mAudioPts = 0;
    private long mRecordTimeMills;
    private double mAmplitudeAvg = 0;// 当前播放声音振幅平均值，即当前所有采样值大小平均值
    private double mSoundDecibels = 0;// 当前播放声音分贝值，单位：dB

    // 接口调度线程
    private HandlerThread mWorkThread;
    private Handler mWorkHandler;
    private boolean isInitSuccess;
    private boolean isRecording;
    private boolean isReleased;

    private static final int HANDLE_INIT = 0;
    private static final int HANDLE_START_RECORD = 1;
    private static final int HANDLE_RESUME_RECORD = 2;
    private static final int HANDLE_PAUSE_RECORD = 3;
    private static final int HANDLE_STOP_RECORD = 4;
    private static final int HANDLE_RELEASE = 5;

    public interface OnAudioRecordDataListener {
        void onAudioRecordData(byte[] data, int size);
    }

    private OnAudioRecordDataListener mOnAudioRecordDataListener;

    public void setOnAudioRecordDataListener(OnAudioRecordDataListener listener) {
        this.mOnAudioRecordDataListener = listener;
    }

    public enum SampleRate {
        SR_8000(8000), SR_11025(11025), SR_22050(22050),
        SR_24000(24000), SR_44100(44100), SR_48000(48000);

        private int nativeValue;

        SampleRate(int nativeValue) {
            this.nativeValue = nativeValue;
        }

        public int getNativeValue() {
            return nativeValue;
        }
    }

    public enum ChannelLayout {
        MONO(1), STEREO(2);

        private int nativeValue;

        ChannelLayout(int nativeValue) {
            this.nativeValue = nativeValue;
        }

        public int getNativeValue() {
            return nativeValue;
        }
    }

    public enum EncodingBits {
        PCM_8BIT(8), PCM_16BIT(16);

        private int nativeValue;

        EncodingBits(int nativeValue) {
            this.nativeValue = nativeValue;
        }

        public int getNativeValue() {
            return nativeValue;
        }
    }

    public WeNAudioRecorder() {
        this.constructHasParams = false;
        initHandlerThread();
    }

    public WeNAudioRecorder(SampleRate sampleRateInHz, ChannelLayout channelLayout, EncodingBits encodingBits) {
        this.constructHasParams = true;
        this.mSampleRate = sampleRateInHz;
        this.mChannelLayout = channelLayout;
        this.mEncodingBits = encodingBits;
        initHandlerThread();
    }

    private void initHandlerThread() {
        mWorkThread = new HandlerThread(TAG);
        mWorkThread.start();
        mWorkHandler = new Handler(mWorkThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                int msgType = msg.what;
                LogUtils.d(TAG, "mWorkHandler handleMessage: " + msgType);
                switch (msgType) {
                    case HANDLE_INIT:
                        handleInit();
                        break;

                    case HANDLE_START_RECORD:
                        handleStartRecord();
                        break;

                    case HANDLE_RESUME_RECORD:
                        handleResumeRecord();
                        break;

                    case HANDLE_PAUSE_RECORD:
                        handlePauseRecord();
                        break;

                    case HANDLE_STOP_RECORD:
                        handleStopRecord();
                        break;

                    case HANDLE_RELEASE:
                        handleRelease();
                        break;
                }
            }
        };
        Message msg = mWorkHandler.obtainMessage(HANDLE_INIT);
        mWorkHandler.sendMessage(msg);
    }

    private void handleInit() {
        if (constructHasParams && mSampleRate != null && mChannelLayout != null && mEncodingBits != null) {
            isInitSuccess = nativeInitRecorder2(mSampleRate.getNativeValue(),
                    mChannelLayout.getNativeValue(), mEncodingBits.getNativeValue());
        } else {
            isInitSuccess = nativeInitRecorder();
        }
        if (isInitSuccess) {
            mSampleRateInHz = nativeGetSampleRate();
            mChannelNum = nativeGetChannelNums();
            mBitsPerSample = nativeGetBitsPerSample();
            mAudioBytesPerSecond = mSampleRateInHz * mChannelNum * mBitsPerSample / 8;
            LogUtils.w(TAG, "init success! " + mSampleRateInHz + "Hz " + mChannelNum
                    + "Channels " + mBitsPerSample + "bit");
        }
    }

    public int getSampleRate() {
        return mSampleRateInHz;
    }

    public int getChannelNums() {
        return mChannelNum;
    }

    public int getBitsPerSample() {
        return mBitsPerSample;
    }

    public void startRecord() {
        if (isReleased) {
            LogUtils.e(TAG, "startRecord but it's already released! Please new one instance.");
            return;
        }
        mWorkHandler.removeMessages(HANDLE_START_RECORD);// 以最新设置为准
        Message msg = mWorkHandler.obtainMessage(HANDLE_START_RECORD);
        mWorkHandler.sendMessage(msg);
    }

    private void handleStartRecord() {
        if (!isInitSuccess) {
            LogUtils.e(TAG, "Can't start recorder: it's init failed!");
            return;
        }
        if (isRecording) {
            LogUtils.e(TAG, "Can't start recorder again: it's already recording!");
            return;
        }
        isRecording = true;

        boolean success = nativeStartRecord();
        if (!success) {
            LogUtils.e(TAG, "Start native recorder failed!");
        }
    }

    public void resumeRecord() {
        if (isReleased) {
            LogUtils.e(TAG, "resumeRecord but it's already released! Please new one instance.");
            return;
        }
        mWorkHandler.removeMessages(HANDLE_RESUME_RECORD);// 以最新设置为准
        Message msg = mWorkHandler.obtainMessage(HANDLE_RESUME_RECORD);
        mWorkHandler.sendMessage(msg);
    }

    private void handleResumeRecord() {
        if (!isInitSuccess) {
            LogUtils.e(TAG, "Can't resume recorder: it's init failed!");
            return;
        }
        if (isRecording) {
            LogUtils.e(TAG, "Can't resume recorder again: it's already recording!");
            return;
        }
        isRecording = true;

        boolean success = nativeResumeRecord();
        if (!success) {
            LogUtils.e(TAG, "Resume native recorder failed!");
        }
    }

    /**
     * Called by native
     *
     * @param pcmData
     * @param size
     */
    private void onNativePCMDataCall(byte[] pcmData, int size) {
        if (pcmData == null || size <= 0 || pcmData.length < size) {
            LogUtils.e(TAG, "onNativePCMDataCall but pcmData=" + pcmData + ";size=" + size);
            mAmplitudeAvg = 0;
            mSoundDecibels = 0;
            return;
        }

        // 更新时长
        mAudioPts += (long) (1.0f * size / mAudioBytesPerSecond * 1000000);
        mRecordTimeMills = mAudioPts / 1000;

        // 更新声响
        updateSoundDecibels(pcmData, size);

        if (mOnAudioRecordDataListener != null) {
            mOnAudioRecordDataListener.onAudioRecordData(pcmData, size);
        }
    }

    private void updateSoundDecibels(byte[] pcmData, int size) {
        double amplitudeSum = 0;// 采样值加和
        switch (mBitsPerSample) {
            case 8:
                for (int i = 0; i < size; i++) {
                    amplitudeSum += Math.abs(pcmData[i]);// 把这段时间的所有采样值加和
                }
                // 更新振幅平均值
                mAmplitudeAvg = amplitudeSum / size;// 除数是 8 位采样点个数
                break;

            case 16:
                // 针对 16 位编码，要先把两个 8 位转成 16 位的数据再计算振幅
                short amplitudeShort = 0;// 16bit 采样值
                for (int i = 0; i < size; i += 2) {
                    amplitudeShort = (short) ((pcmData[i] & 0xff) | ((pcmData[i + 1] & 0xff) << 8));
                    amplitudeSum += Math.abs(amplitudeShort);// 把这段时间的所有采样值加和
                }
                // 更新振幅平均值
                mAmplitudeAvg = amplitudeSum / (size / 2);// 除数是 16 位采样点个数
                break;
        }
        // 更新分贝值：分贝 = 20 * log10(振幅)
        if (mAmplitudeAvg > 0) {
            mSoundDecibels = 20 * Math.log10(mAmplitudeAvg);
        } else {
            mSoundDecibels = 0;
        }
    }

    /**
     * 获取当前已录音时长，单位：毫秒
     */
    public long getRecordTimeMills() {
        return mRecordTimeMills;
    }

    /**
     * 获取当前播放声音分贝值，单位：dB
     */
    public double getSoundDecibels() {
        return mSoundDecibels;
    }

    public void pauseRecord() {
        if (isReleased) {
            LogUtils.e(TAG, "pauseRecord but it's already released!");
            return;
        }
        Message msg = mWorkHandler.obtainMessage(HANDLE_PAUSE_RECORD);
        mWorkHandler.sendMessage(msg);
    }

    private void handlePauseRecord() {
        LogUtils.w(TAG, "handlePauseRecord");
        if (!isRecording) {
            LogUtils.w(TAG, "No need to pause recorder: it's already stopped!");
            return;
        }
        isRecording = false;

        nativePauseRecord();
    }

    public void stopRecord() {
        if (isReleased) {
            LogUtils.e(TAG, "stopRecord but it's already released!");
            return;
        }
        mWorkHandler.removeMessages(HANDLE_START_RECORD);
        Message msg = mWorkHandler.obtainMessage(HANDLE_STOP_RECORD);
        mWorkHandler.sendMessage(msg);
    }

    private void handleStopRecord() {
        LogUtils.w(TAG, "handleStopRecord");
        if (!isRecording) {
            LogUtils.w(TAG, "No need to stop recorder again: it's already stopped!");
            return;
        }
        isRecording = false;
        mAudioPts = 0;
        mRecordTimeMills = 0;

        nativeStopRecord();
    }

    public void release() {
        if (isReleased) {
            return;
        }
        // 首先置总的标志位，阻止消息队列的正常消费
        isReleased = true;

        // 然后抛到工作线程做释放工作
        mWorkHandler.removeCallbacksAndMessages(null);
        Message msg = mWorkHandler.obtainMessage(HANDLE_RELEASE);
        mWorkHandler.sendMessage(msg);
    }

    private void handleRelease() {
        handleStopRecord();
        nativeReleaseRecorder();

        mWorkHandler.removeCallbacksAndMessages(null);
        try {
            mWorkThread.quit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
