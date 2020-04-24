package com.wtz.libvideomaker.push;

import android.content.Context;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.Toast;

import com.wtz.libpushflow.WePushFlow;
import com.wtz.libvideomaker.egl.WeGLRenderer;
import com.wtz.libvideomaker.egl.WeGLVideoPushEncoder;
import com.wtz.libvideomaker.renderer.OnScreenRenderer;
import com.wtz.libvideomaker.utils.ExponentialWaitStrategy;
import com.wtz.libvideomaker.utils.LogUtils;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGLContext;

public class WeVideoPusher extends WeGLVideoPushEncoder implements WeGLRenderer,
        WePushFlow.PushStateListener, WeGLVideoPushEncoder.OnEncodeDataListener {
    private static final String TAG = WeVideoPusher.class.getSimpleName();

    private boolean isUserPushing;
    private boolean isReleased;
    private boolean isPushStarted;

    private Context mContext;
    private EGLContext mSharedEGLContext;
    private int mVideoWidth;
    private int mVideoHeight;

    private OnScreenRenderer mOnScreenRenderer;
    private static final int RENDER_FPS = 30;//大部分摄像头最高30fps，FPS过高会导致部分低端机型渲染闪屏

    private WePushFlow mWePushFlow;
    private byte[] sps;
    private byte[] pps;

    private ExponentialWaitStrategy mWaitStrategy;
    private static final int WAIT_RANDOM_BOUND_SECONDS = 10;// 在等待间隔基础上加随机秒值的边界大小
    private long mRetryNumber;
    private WeakHandler mUIHandler = new WeakHandler(this);
    private static final int MSG_RETRY_START_PUSH = 1;

    static class WeakHandler extends Handler {
        private final WeakReference<WeVideoPusher> weakReference;

        // 为了避免非静态的内部类和匿名内部类隐式持有外部类的引用，改用静态类
        // 又因为内部类是静态类，所以不能直接操作宿主类中的方法了，
        // 于是需要传入宿主类实例的弱引用来操作宿主类中的方法
        public WeakHandler(WeVideoPusher host) {
            super(Looper.getMainLooper());
            this.weakReference = new WeakReference(host);
        }

        @Override
        public void handleMessage(Message msg) {
            LogUtils.d(TAG, "WeakHandler handleMessage: " + msg.what);
            WeVideoPusher host = weakReference.get();
            if (host == null) {
                return;
            }

            switch (msg.what) {
                case MSG_RETRY_START_PUSH:
                    removeMessages(MSG_RETRY_START_PUSH);
                    host.startInnerPush();
                    break;
            }
        }
    }

    public WeVideoPusher(Context context) {
        super();
        mContext = context;
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        setRenderFps(RENDER_FPS);
        setOnEncodeDataListener(this);

        mOnScreenRenderer = new OnScreenRenderer(context, TAG);
        mOnScreenRenderer.setClearScreenOnDraw(false);// 缓解某些低端机型录制视频时闪屏问题

        mWePushFlow = new WePushFlow();
        mWePushFlow.setPushStateListener(this);
    }

    public void setExternalTextureId(int id) {
        mOnScreenRenderer.setExternalTextureId(id);
    }

    @Override
    protected WeGLRenderer getRenderer() {
        return this;
    }

    @Override
    protected String getExternalLogTag() {
        return TAG;
    }

    public void setPushUrl(String url) {
        if (isReleased) {
            LogUtils.e(TAG, "setPushUrl but it's already released! Please new one instance.");
            return;
        }
        mWePushFlow.setPushUrl(url);
    }

    public void setConnectTimeout(int seconds) {
        if (isReleased) {
            LogUtils.e(TAG, "setConnectTimeout but it's already released! Please new one instance.");
            return;
        }
        mWePushFlow.setConnectTimeout(seconds);
    }

    public void setAudioEncodeBits(WePushFlow.EncodingBits audioEncodeBits) {
        if (isReleased) {
            LogUtils.e(TAG, "setAudioEncodeBits but it's already released! Please new one instance.");
            return;
        }
        mWePushFlow.setAudioEncodeBits(audioEncodeBits);
    }

    public void setAudioChannels(WePushFlow.ChannelLayout audioChannels) {
        if (isReleased) {
            LogUtils.e(TAG, "setAudioChannels but it's already released! Please new one instance.");
            return;
        }
        mWePushFlow.setAudioChannels(audioChannels);
    }

    public void startPush(EGLContext context, int videoWidth, int videoHeight) {
        if (isReleased) {
            LogUtils.e(TAG, "startPush but it's already released! Please new one instance.");
            return;
        }
        isUserPushing = true;
        mSharedEGLContext = context;
        mVideoWidth = videoWidth;
        mVideoHeight = videoHeight;

        startInnerPush();
    }

    private void startInnerPush() {
        if (mWePushFlow != null) {
            mWePushFlow.startPush();
        } else {
            LogUtils.e(TAG, "startInnerPush mWePushFlow == null");
        }
    }

    @Override
    public void onStartPushResult(boolean success, String info) {
        LogUtils.w(TAG, "onStartPushResult success=" + success + " " + info);
        if (mContext != null) {
            Toast.makeText(mContext, info, Toast.LENGTH_SHORT).show();
        }
        isPushStarted = success;
        if (success) {
            stopRetryStartPush();
            super.startEncode(mSharedEGLContext, MediaFormat.MIMETYPE_VIDEO_AVC, mVideoWidth, mVideoHeight);
        } else {
            if (isUserPushing) {
                retryStartPush();
            }
        }
    }

    @Override
    public void onPushDisconnect() {
        LogUtils.e(TAG, "onPushDisconnect");
        if (mContext != null) {
            Toast.makeText(mContext, "直播流断开！", Toast.LENGTH_SHORT).show();
        }
        stopInnerPush();
        if (isUserPushing) {
            retryStartPush();
        }
    }

    private void retryStartPush() {
        mRetryNumber++;
        if (mWaitStrategy == null) {
            mWaitStrategy = new ExponentialWaitStrategy(20000, 2, TimeUnit.MINUTES);
        }
        long delay = mWaitStrategy.computeSleepTime(mRetryNumber)
                + mWaitStrategy.getRandomDelayMillis(WAIT_RANDOM_BOUND_SECONDS);
        String time = getSpecifiedDateTime(new Date(System.currentTimeMillis() + delay), "HH:mm:ss");
        LogUtils.e(TAG, "plan retryStartPush num=" + mRetryNumber + " delay=" + (delay / 1000) + "s at " + time);
        if (mContext != null) {
            String tips = "将在" + time + "重试连接";
            Toast.makeText(mContext, tips, Toast.LENGTH_SHORT).show();
        }
        mUIHandler.removeMessages(MSG_RETRY_START_PUSH);
        mUIHandler.sendEmptyMessageDelayed(MSG_RETRY_START_PUSH, delay);
    }

    /**
     * @param date   指定的日期
     * @param format 返回的日期格式 e.g."yyyy-MM-dd_HH-mm-ss"
     * @return
     */
    public static String getSpecifiedDateTime(Date date, String format) {
        SimpleDateFormat df = new SimpleDateFormat(format);
        String nowTime = df.format(date);
        return nowTime;
    }

    private void stopRetryStartPush() {
        mUIHandler.removeMessages(MSG_RETRY_START_PUSH);
    }

    @Override
    public void onEGLContextCreated() {
        mOnScreenRenderer.onEGLContextCreated();
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        mOnScreenRenderer.onSurfaceChanged(width, height);
    }

    @Override
    public void onDrawFrame() {
        mOnScreenRenderer.onDrawFrame();
    }

    @Override
    public void onVideoDataCall(byte[] data, int size, boolean isKeyframe) {
        if (isReleased || !isPushStarted || mWePushFlow == null) return;

        if (isKeyframe) {
            sps = getSPS();
            pps = getPPS();
            if (sps != null && pps != null) {
                mWePushFlow.pushSpsPps(sps, sps.length, pps, pps.length);
            } else {
                LogUtils.e(TAG, "Can't get sps or pps!");
            }
            sps = null;
            pps = null;
        }

        mWePushFlow.pushVideoData(data, size, isKeyframe);
    }

    public void enqueueAudioData(byte[] data, int size) {
        if (isReleased || !isPushStarted || mWePushFlow == null) return;

        super.onAudioPCMDataCall(data, size);
    }

    @Override
    public void onAudioDataCall(byte[] data, int size) {
        if (isReleased || !isPushStarted || mWePushFlow == null) return;

        mWePushFlow.pushAudioData(data, size);
    }

    public void stopPush() {
        isUserPushing = false;
        stopInnerPush();
    }

    private void stopInnerPush() {
        isPushStarted = false;
        super.stopEncode();
        if (mWePushFlow != null) {
            mWePushFlow.stopPush();
        }
    }

    public void release() {
        if (isReleased) {
            LogUtils.w(TAG, "It's already released!");
            return;
        }
        isReleased = true;
        mUIHandler.removeCallbacksAndMessages(null);
        stopPush();
        super.release();
        if (mWePushFlow != null) {
            mWePushFlow.release();
            mWePushFlow = null;
        }
        mContext = null;
    }

    @Override
    public void onEGLContextToDestroy() {
        mOnScreenRenderer.onEGLContextToDestroy();
        if (isReleased) {
            mSharedEGLContext = null;
            mOnScreenRenderer = null;
        }
    }

}
