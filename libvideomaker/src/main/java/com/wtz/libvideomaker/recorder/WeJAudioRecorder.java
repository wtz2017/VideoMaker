package com.wtz.libvideomaker.recorder;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import com.wtz.libvideomaker.utils.LogUtils;

import java.lang.ref.WeakReference;

/**
 * 对 Java 层的 AudioRecord 的封装
 */
public class WeJAudioRecorder {

    private static final String TAG = WeJAudioRecorder.class.getSimpleName();

    private boolean constructHasParams;
    private WeakReference<WeJAudioRecorder> mWeakReference;

    private RecordThread mRecordThread;
    private boolean isRecordThreadExiting;

    private int mAudioSource;
    private int mSampleRate;
    private int mChannelConfig;
    private int mEncodingFormat;
    private int mBufferSizeInBytes;
    private int mAudioBytesPerSecond;
    private long mRecordTimeMills;
    private AudioRecord mRecorder;

    // 接口调度线程
    private Handler mWorkHandler;
    private HandlerThread mWorkThread;
    private boolean isInitSuccess;
    private boolean isRecording;
    private boolean isReleased;

    private static final int HANDLE_INIT = 0;
    private static final int HANDLE_START_RECORD = 1;
    private static final int HANDLE_STOP_RECORD = 2;
    private static final int HANDLE_RELEASE = 3;
    private static final String PARAMS_EGL_CONTEXT = "egl_context";

    public interface OnAudioRecordDataListener {
        void onAudioRecordData(byte[] data, int size);
    }

    private OnAudioRecordDataListener mOnAudioRecordDataListener;

    public void setOnAudioRecordDataListener(OnAudioRecordDataListener listener) {
        this.mOnAudioRecordDataListener = listener;
    }

    public WeJAudioRecorder() {
        this.constructHasParams = false;
        initHandlerThread();
    }

    public WeJAudioRecorder(int audioSource, int sampleRateInHz, int channelConfig, int encodingFormat) {
        this.constructHasParams = true;
        this.mAudioSource = audioSource;
        this.mSampleRate = sampleRateInHz;
        this.mChannelConfig = channelConfig;
        this.mEncodingFormat = encodingFormat;
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
        if (constructHasParams) {
            mRecorder = createRecorder(mAudioSource, mSampleRate, mChannelConfig, mEncodingFormat);
        } else {
            mRecorder = findAudioRecord();
        }
        mAudioBytesPerSecond = mSampleRate * getChannelNums() * getBitsPerSample() / 8;
        isInitSuccess = mRecorder != null;
    }

    private AudioRecord findAudioRecord() {
        int[] mSampleRates = new int[]{
                /*48000,/* miniDV、数字电视、DVD、DAT、电影和专业音频 */
                44100,/* 音频 CD，也常用于 MPEG-1 音频(VCD/SVCD/MP3) */
                24000,/* FM 调频广播 */
                22050,/* FM 调频广播 */
                11025,/* AM调幅广播 */
                8000/* 电话 */
        };
        for (int rate : mSampleRates) {
            for (short encodingFormat : new short[]{AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_8BIT}) {
                for (short channelConfig : new short[]{AudioFormat.CHANNEL_IN_STEREO, AudioFormat.CHANNEL_IN_MONO}) {
                    for (short audioSource : new short[]{MediaRecorder.AudioSource.MIC, MediaRecorder.AudioSource.DEFAULT,
                            MediaRecorder.AudioSource.CAMCORDER}) {
                        AudioRecord recorder = createRecorder(audioSource, rate, channelConfig, encodingFormat);
                        if (recorder != null) return recorder;
                    }
                }
            }
        }
        return null;
    }

    private AudioRecord createRecorder(int audioSource, int sampleRateInHz, int channelConfig, int encodingFormat) {
        try {
            int bufferMinSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, encodingFormat);
            if (bufferMinSize == AudioRecord.ERROR_BAD_VALUE) {
                LogUtils.e(TAG, "AudioRecord.getMinBufferSize failed! return: " + bufferMinSize);
                return null;
            }

            mBufferSizeInBytes = bufferMinSize;
            AudioRecord recorder = new AudioRecord(audioSource, sampleRateInHz, channelConfig,
                    encodingFormat, mBufferSizeInBytes);
            if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
                LogUtils.w(TAG, "Create Recorder success!--->sampleRateInHz=" + sampleRateInHz
                        + ", encodingFormat=" + getEncodingFormatName(encodingFormat)
                        + ", channel= " + getChannelConfigName(channelConfig)
                        + ", audioSource=" + getAudioSourceName(audioSource)
                        + ", mBufferSizeInBytes=" + mBufferSizeInBytes);
                mAudioSource = audioSource;
                mSampleRate = sampleRateInHz;
                mChannelConfig = channelConfig;
                mEncodingFormat = encodingFormat;
                return recorder;
            }
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String getEncodingFormatName(int encodingFormat) {
        String name;
        switch (encodingFormat) {
            case AudioFormat.ENCODING_PCM_16BIT:
                name = "ENCODING_PCM_16BIT";
                break;

            case AudioFormat.ENCODING_PCM_8BIT:
                name = "ENCODING_PCM_8BIT";
                break;

            default:
                name = "unknow";
                break;
        }
        return name;
    }

    private String getChannelConfigName(int channelConfig) {
        String name;
        switch (channelConfig) {
            case AudioFormat.CHANNEL_IN_STEREO:
                name = "CHANNEL_IN_STEREO";
                break;

            case AudioFormat.CHANNEL_IN_MONO:
                name = "CHANNEL_IN_MONO";
                break;

            default:
                name = "unknow";
                break;
        }
        return name;
    }

    private String getAudioSourceName(int audioSource) {
        String name;
        switch (audioSource) {
            case MediaRecorder.AudioSource.DEFAULT:
                name = "DEFAULT";
                break;

            case MediaRecorder.AudioSource.MIC:
                name = "MIC";
                break;

            case MediaRecorder.AudioSource.CAMCORDER:
                name = "CAMCORDER";
                break;

            default:
                name = "unknow";
                break;
        }
        return name;
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
        mRecordTimeMills = 0;

        mWeakReference = new WeakReference<>(this);
        mRecordThread = new RecordThread(mWeakReference);
        mRecordThread.start();
    }

    /**
     * 获取当前已录音时长，单位：毫秒
     */
    public long getRecordTimeMills() {
        return mRecordTimeMills;
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
        mRecordTimeMills = 0;

        isRecordThreadExiting = false;
        if (mRecordThread != null) {
            isRecordThreadExiting = true;
            mRecordThread.requestExit(new OnThreadExitedListener() {
                @Override
                public void onExited(Thread thread) {
                    LogUtils.w(TAG, "mRecordThread onExited: " + thread.hashCode());
                    isRecordThreadExiting = false;
                }
            });
        }

        while (isRecordThreadExiting) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (mRecorder != null) {
            mRecorder.stop();
        }
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
        if (mRecorder != null) {
            mRecorder.release();
            mRecorder = null;
        }

        mWeakReference.clear();
        mWeakReference = null;

        mWorkHandler.removeCallbacksAndMessages(null);
        try {
            mWorkThread.quit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int getSampleRate() {
        return mSampleRate;
    }

    public int getChannelNums() {
        int nums;
        switch (mChannelConfig) {
            case AudioFormat.CHANNEL_IN_STEREO:
                nums = 2;
                break;

            case AudioFormat.CHANNEL_IN_MONO:
                nums = 1;
                break;

            default:
                nums = 0;
                break;
        }
        return nums;
    }

    public int getBitsPerSample() {
        int bits;
        switch (mEncodingFormat) {
            case AudioFormat.ENCODING_PCM_16BIT:
                bits = 16;
                break;

            case AudioFormat.ENCODING_PCM_8BIT:
                bits = 8;
                break;

            default:
                bits = 0;
                break;
        }
        return bits;
    }

    /**
     * 音频录制线程
     */
    static class RecordThread extends Thread {
        private WeakReference<WeJAudioRecorder> mWeakReference;
        private AudioRecord mRecorder;
        private int mBufferSizeInBytes;
        private int mAudioBytesPerSecond;
        private OnAudioRecordDataListener mOnAudioRecordDataListener;

        private boolean isShouldExit;
        private boolean isExited;

        private long mAudioPts = 0;

        private OnThreadExitedListener mOnExitedListener;

        public RecordThread(WeakReference<WeJAudioRecorder> weakReference) {
            this.mWeakReference = weakReference;
            mRecorder = mWeakReference.get().mRecorder;
            mBufferSizeInBytes = mWeakReference.get().mBufferSizeInBytes;
            mAudioBytesPerSecond = mWeakReference.get().mAudioBytesPerSecond;
            mOnAudioRecordDataListener = mWeakReference.get().mOnAudioRecordDataListener;
        }

        @Override
        public void run() {
            setName(TAG + " " + android.os.Process.myTid());
            LogUtils.w(TAG, "Record thread starting tid=" + android.os.Process.myTid());
            try {
                guardedRun();
            } catch (Throwable e) {
                LogUtils.e(TAG, "catch exception: " + e.toString());
                if (isShouldExit) {
                    LogUtils.e(TAG, "Because isShouldExit = true, so ignore this exception");
                } else {
                    throw e;
                }
            } finally {
                release();
            }
            LogUtils.w(TAG, "Record thread end tid=" + android.os.Process.myTid());
        }

        private void guardedRun() {
            if (mRecorder == null) {
                LogUtils.e(TAG, "mRecorder is null!");
                return;
            }
            mRecorder.startRecording();

            int readSize = 0;
            byte[] readBytes = new byte[mBufferSizeInBytes];
            while (!isShouldExit) {
                WeJAudioRecorder master = mWeakReference.get();
                if (master == null) {
                    // 主类已被回收，因为某种原因没有来得及置退出标志，这里就直接退出线程
                    LogUtils.e(TAG, "WeJAudioRecorder got from mWeakReference is null, so exit!");
                    return;
                }

                try {
                    readSize = mRecorder.read(readBytes, 0, mBufferSizeInBytes);
                    mAudioPts += (long) (1.0f * readSize / mAudioBytesPerSecond * 1000000);
                    master.mRecordTimeMills = mAudioPts / 1000;
                    if (mOnAudioRecordDataListener != null) {
                        mOnAudioRecordDataListener.onAudioRecordData(readBytes, readSize);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        public void requestExit(OnThreadExitedListener listener) {
            this.mOnExitedListener = listener;
            if (isExited) {
                if (mOnExitedListener != null) {
                    mOnExitedListener.onExited(this);
                }
            } else {
                isShouldExit = true;
            }
        }

        private void release() {
            // mRecorder 系列在这里只置空，具体回收交给外部主类释放
            mRecorder = null;
            mWeakReference = null;

            isExited = true;
            if (mOnExitedListener != null) {
                mOnExitedListener.onExited(this);
            }
        }

    }

    public interface OnThreadExitedListener {
        void onExited(Thread thread);
    }

}
