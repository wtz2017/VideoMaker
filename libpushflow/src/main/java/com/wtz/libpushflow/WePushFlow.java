package com.wtz.libpushflow;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import com.wtz.libpushflow.utlis.LogUtils;

public class WePushFlow {

    private static final String TAG = WePushFlow.class.getSimpleName();

    static {
        System.loadLibrary("wepushflow");
    }

    private native boolean nativeCreatePushFlow();

    private native void nativeSetPushUrl(String url);

    private native void nativeSetConnectTimeout(int seconds);

    private native void nativeSetAudioEncodeBits(int audioEncodeBits);

    private native void nativeSetAudioChannels(int audioChannels);

    private native void nativeStartPush();

    private native void nativePushSpsPps(byte[] sps, int spsLength, byte[] pps, int ppsLength);

    private native void nativePushVideoData(byte[] data, int dataLength, boolean isKeyframe);

    private native void nativePushAudioData(byte[] data, int dataLength);

    private native void nativeSetStopFlag();

    private native void nativeStopPush();

    private native void nativeDestroyPushFlow();

    // 接口调度线程
    private HandlerThread mWorkThread;
    private Handler mWorkHandler;
    private Handler mUIHandler;// 用以把回调切换到主线程，不占用工作线程资源
    private boolean isStartSuccess;
    private boolean isStarting;
    private boolean isReleased;

    private static final int HANDLE_START_PUSH = 1;
    private static final int HANDLE_STOP_PUSH = 2;
    private static final int HANDLE_RELEASE = 3;

    public interface PushStateListener {
        void onStartPushResult(boolean success, String info);

        void onPushDisconnect();
    }

    private PushStateListener mPushStateListener;

    public void setPushStateListener(PushStateListener listener) {
        this.mPushStateListener = listener;
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

    public WePushFlow() {
        mUIHandler = new Handler(Looper.getMainLooper());
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
                if (isReleased && msgType != HANDLE_RELEASE) {
                    LogUtils.e(TAG, "mWorkHandler handleMessage but it's already destroyed!");
                    return;
                }
                switch (msgType) {
                    case HANDLE_START_PUSH:
                        handleStartPush();
                        break;

                    case HANDLE_STOP_PUSH:
                        handleStopPush();
                        break;

                    case HANDLE_RELEASE:
                        handleRelease();
                        break;
                }
            }
        };
        nativeCreatePushFlow();
    }

    public void setPushUrl(String url) {
        if (isReleased) {
            LogUtils.e(TAG, "setPushUrl but it's already released! Please new one instance.");
            return;
        }
        nativeSetPushUrl(url);
    }

    public void setConnectTimeout(int seconds) {
        if (isReleased) {
            LogUtils.e(TAG, "setConnectTimeout but it's already released! Please new one instance.");
            return;
        }
        nativeSetConnectTimeout(seconds);
    }

    public void setAudioEncodeBits(EncodingBits audioEncodeBits) {
        if (isReleased) {
            LogUtils.e(TAG, "setAudioEncodeBits but it's already released! Please new one instance.");
            return;
        }
        nativeSetAudioEncodeBits(audioEncodeBits.getNativeValue());
    }

    public void setAudioChannels(ChannelLayout audioChannels) {
        if (isReleased) {
            LogUtils.e(TAG, "setAudioChannels but it's already released! Please new one instance.");
            return;
        }
        nativeSetAudioChannels(audioChannels.getNativeValue());
    }

    public void startPush() {
        if (isReleased) {
            LogUtils.e(TAG, "startPush but it's already released! Please new one instance.");
            return;
        }
        mWorkHandler.removeMessages(HANDLE_START_PUSH);// 以最新设置为准
        Message msg = mWorkHandler.obtainMessage(HANDLE_START_PUSH);
        mWorkHandler.sendMessage(msg);
    }

    private void handleStartPush() {
        if (isStartSuccess || isStarting) {
            LogUtils.e(TAG, "Can't start push again: it's already starting or started!");
            return;
        }
        isStarting = true;

        nativeStartPush();
    }

    private void onNativeStartPushResult(final boolean success, final String info) {
        isStartSuccess = success;
        isStarting = false;
        if (!success) {
            LogUtils.e(TAG, "Start native push failed: " + info);
        }
        if (mPushStateListener != null) {
            mUIHandler.post(new Runnable() {
                @Override
                public void run() {
                    mPushStateListener.onStartPushResult(success, info);
                }
            });
        }
    }

    private void onNativePushDisconnect() {
        LogUtils.e(TAG, "onNativePushDisconnect");
        if (mPushStateListener != null) {
            mUIHandler.post(new Runnable() {
                @Override
                public void run() {
                    mPushStateListener.onPushDisconnect();
                }
            });
        }
    }

    public void pushSpsPps(byte[] sps, int spsLength, byte[] pps, int ppsLength) {
        if (isReleased) {
            LogUtils.e(TAG, "pushSpsPps but it's already released! Please new one instance.");
            return;
        }
        if (!isStartSuccess) {
            LogUtils.e(TAG, "pushSpsPps but it's not started yet.");
            return;
        }
        nativePushSpsPps(sps, spsLength, pps, ppsLength);
    }

    public void pushVideoData(byte[] data, int dataLength, boolean isKeyframe) {
        if (isReleased) {
            LogUtils.e(TAG, "pushVideoData but it's already released! Please new one instance.");
            return;
        }
        if (!isStartSuccess) {
            LogUtils.e(TAG, "pushVideoData but it's not started yet.");
            return;
        }
        nativePushVideoData(data, dataLength, isKeyframe);
    }

    public void pushAudioData(byte[] data, int dataLength) {
        if (isReleased) {
            LogUtils.e(TAG, "pushAudioData but it's already released! Please new one instance.");
            return;
        }
        if (!isStartSuccess) {
            LogUtils.e(TAG, "pushAudioData but it's not started yet.");
            return;
        }
        nativePushAudioData(data, dataLength);
    }

    public void stopPush() {
        nativeSetStopFlag();// 设置停止标志位立即执行，不进消息队列

        // 先清除其它未执行的不必要消息
        mWorkHandler.removeMessages(HANDLE_START_PUSH);
        Message msg = mWorkHandler.obtainMessage(HANDLE_STOP_PUSH);
        mWorkHandler.sendMessage(msg);
    }

    private void handleStopPush() {
        isStartSuccess = false;
        nativeStopPush();
    }

    public void release() {
        if (isReleased) {
            return;
        }
        // 首先置总的标志位，阻止消息队列的正常消费
        isReleased = true;
        isStarting = false;
        nativeSetStopFlag();

        // 然后停止工作线程
        mWorkHandler.removeCallbacksAndMessages(null);
        Message msg = mWorkHandler.obtainMessage(HANDLE_RELEASE);
        mWorkHandler.sendMessage(msg);

        // 最后停止回调工作结果
        mUIHandler.removeCallbacksAndMessages(null);
    }

    private void handleRelease() {
        nativeDestroyPushFlow();

        mWorkHandler.removeCallbacksAndMessages(null);
        try {
            mWorkThread.quit();
        } catch (Exception e) {
            e.printStackTrace();
        }

        mUIHandler.removeCallbacksAndMessages(null);
    }

}
