package com.wtz.libvideomaker.push;

import android.content.Context;
import android.media.MediaFormat;
import android.widget.Toast;

import com.wtz.libpushflow.WePushFlow;
import com.wtz.libvideomaker.egl.WeGLRenderer;
import com.wtz.libvideomaker.egl.WeGLVideoPushEncoder;
import com.wtz.libvideomaker.renderer.OnScreenRenderer;
import com.wtz.libvideomaker.utils.HexUtils;
import com.wtz.libvideomaker.utils.LogUtils;

import javax.microedition.khronos.egl.EGLContext;

public class WeVideoPusher extends WeGLVideoPushEncoder implements WeGLRenderer, WePushFlow.OnStartPushListener, WeGLVideoPushEncoder.OnEncodeDataListener {
    private static final String TAG = WeVideoPusher.class.getSimpleName();

    private boolean isReleased;
    private boolean isPushStarted;

    private Context mContext;
    private EGLContext mEGLContext;
    private int mVideoWidth;
    private int mVideoHeight;

    private OnScreenRenderer mOnScreenRenderer;
    private static final int RENDER_FPS = 30;//大部分摄像头最高30fps，FPS过高会导致部分低端机型渲染闪屏

    private WePushFlow mWePushFlow;
    private byte[] sps;
    private byte[] pps;

    public WeVideoPusher(Context context) {
        super();
        mContext = context;
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        setRenderFps(RENDER_FPS);
        setOnEncodeDataListener(this);

        mOnScreenRenderer = new OnScreenRenderer(context, TAG);
        mOnScreenRenderer.setClearScreenOnDraw(false);// 缓解某些低端机型录制视频时闪屏问题

        mWePushFlow = new WePushFlow();
        mWePushFlow.setOnStartPushListener(this);
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
        mEGLContext = context;
        mVideoWidth = videoWidth;
        mVideoHeight = videoHeight;
        mWePushFlow.startPush();
    }

    @Override
    public void onStartPushResult(boolean success, String info) {
        LogUtils.w(TAG, "onStartPushResult success=" + success + " " + info);
        isPushStarted = success;
        if (success) {
            super.startEncode(mEGLContext, MediaFormat.MIMETYPE_VIDEO_AVC, mVideoWidth, mVideoHeight);
        } else {
            if (mContext != null) {
                Toast.makeText(mContext, info, Toast.LENGTH_SHORT).show();
            }
        }
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
        mEGLContext = null;
        if (isReleased) {
            mOnScreenRenderer = null;
        }
    }

}
