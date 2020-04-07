package com.wtz.libvideomaker.egl;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.text.TextUtils;
import android.view.Surface;

import com.wtz.libvideomaker.utils.LogUtils;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

import javax.microedition.khronos.egl.EGLContext;

public abstract class WeGLVideoEncoder {

    private static final String TAG = "WeGLVideoEncoder";
    private String mExternalTag;

    private WeakReference<WeGLVideoEncoder> mWeakReference;

    private EGLContext mShareContext;
    private Surface mSurface;

    private WeGLThread mGLThread;
    private WeGLRenderer mRenderer;
    private int mRenderMode = WeGLRenderer.RENDERMODE_CONTINUOUSLY;

    private MediaMuxer mMediaMuxer;

    private MediaCodec mVideoEncoder;
    private MediaFormat mVideoFormat;
    private MediaCodec.BufferInfo mVideoBufInfo;
    private long mEncodeTimeMills;

    private VideoEncodeThread mVideoEncodeThread;

    protected abstract WeGLRenderer getRenderer();

    protected abstract String getExternalLogTag();

    public WeGLVideoEncoder() {
        mExternalTag = getExternalLogTag() + ": ";
        LogUtils.w(TAG, mExternalTag + "WeGLVideoEncoder created");
    }

    public void setRenderMode(int renderMode) {
        if ((WeGLRenderer.RENDERMODE_WHEN_DIRTY != renderMode)
                && (renderMode != WeGLRenderer.RENDERMODE_CONTINUOUSLY)) {
            throw new IllegalArgumentException(
                    exceptionPrefix() + "illegal argument: renderMode " + renderMode);
        }
        mRenderMode = renderMode;
    }

    public void requestRender() {
        if (mGLThread == null) {
            LogUtils.e(TAG, mExternalTag + exceptionPrefix()
                    + "GLThread is null! You can't call requestRender before onEGLContextCreated.");
            return;
        }
        mGLThread.requestRender();
    }

    public void startEncode(EGLContext context, String savePath, String mimeType, int videoWidth, int videoHeight) {
        LogUtils.w(TAG, mExternalTag + "startEncode mimeType=" + mimeType +
                ", video size=" + videoWidth + "x" + videoHeight);
        this.mShareContext = context;
        if (mShareContext == null) {
            throw new IllegalArgumentException("EGLContext can't be null!");
        }

        if (TextUtils.isEmpty(mimeType) || videoWidth <= 0 || videoHeight <= 0) {
            throw new IllegalArgumentException("startEncode video arguments is illegal");
        }

        synchronized (WeGLVideoEncoder.this) {// 与退出销毁资源同步
            mRenderer = getRenderer();
            if (mRenderer == null) {
                throw new RuntimeException("The render from getRenderer can't be null!");
            }

            if (!initMuxer(savePath)) {
                return;
            }

            if (!initVideoEncoder(mimeType, videoWidth, videoHeight)) {
                return;
            }

            mWeakReference = new WeakReference<>(this);
            mGLThread = new GLThread(mWeakReference, getExternalLogTag());
            mGLThread.onWindowResize(videoWidth, videoHeight);
            mGLThread.start();

            mVideoEncodeThread = new VideoEncodeThread(mWeakReference);
            mVideoEncodeThread.start();
        }
    }

    private boolean initMuxer(String savePath) {
        try {
            mMediaMuxer = new MediaMuxer(savePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private boolean initVideoEncoder(String mimeType, int videoWidth, int videoHeight) {
        mVideoFormat = MediaFormat.createVideoFormat(mimeType, videoWidth, videoHeight);
        mVideoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mVideoFormat.setInteger(MediaFormat.KEY_BIT_RATE, videoWidth * videoHeight * 4);// 设置码率
        mVideoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);// 一般视频最大 30 帧每秒
        mVideoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);// 设置关键帧间隔为 1 秒

        try {
            mVideoEncoder = MediaCodec.createEncoderByType(mimeType);
            mVideoEncoder.configure(mVideoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mSurface = mVideoEncoder.createInputSurface();
        } catch (Exception e) {
            e.printStackTrace();
            mVideoFormat = null;
            mVideoEncoder = null;
            return false;
        }

        mVideoBufInfo = new MediaCodec.BufferInfo();
        return true;
    }

    public void onVideoSizeChanged(int width, int height) {
        LogUtils.w(TAG, mExternalTag + "onVideoSizeChanged " + width + "x" + height);
        mGLThread.onWindowResize(width, height);
        mGLThread.requestRender();
    }

    /**
     * 获取当前已编码时长，单位：毫秒
     */
    public long getEncodeTimeMills() {
        return mEncodeTimeMills;
    }

    public void stopEncode() {
        LogUtils.w(TAG, mExternalTag + "stopEncode");
        if (mGLThread != null) {
            mGLThread.requestExit(new WeGLThread.OnExitedListener() {
                @Override
                public void onExited(WeGLThread glThread) {
                    LogUtils.w(TAG, mExternalTag + "mGLThread onExited: " + glThread.hashCode());
                    synchronized (WeGLVideoEncoder.this) {// 与初始化创建资源同步
                        if (mGLThread != null && glThread != mGLThread) {
                            // 新的线程已经创建
                            return;
                        }
                        releaseOnGLThreadExit();
                    }
                }
            });
        } else {
            releaseOnGLThreadExit();
        }
        if (mVideoEncodeThread != null) {
            mVideoEncodeThread.requestExit(new OnThreadExitedListener() {
                @Override
                public void onExited(Thread thread) {
                    LogUtils.w(TAG, mExternalTag + "mVideoEncodeThread onExited: " + thread.hashCode());
                    synchronized (WeGLVideoEncoder.this) {// 与初始化创建资源同步
                        if (mVideoEncodeThread != null && mVideoEncodeThread != thread) {
                            // 新的线程已经创建
                            return;
                        }
                        releaseOnVideoEncThreadExit();
                    }
                }
            });
        } else {
            releaseOnVideoEncThreadExit();
        }
    }

    private void releaseOnGLThreadExit() {
        mShareContext = null;
        mRenderer = null;
        mGLThread = null;
        mSurface = null;
    }

    private void releaseOnVideoEncThreadExit() {
        if (mVideoEncoder != null) {
            try {
                mVideoEncoder.stop();
                mVideoEncoder.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mVideoEncoder = null;
        }
        if (mMediaMuxer != null) {
            try {
                mMediaMuxer.stop();// 在停止时才会写入视频头信息
                mMediaMuxer.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mMediaMuxer = null;
        }
        mVideoFormat = null;
        mVideoBufInfo = null;
    }

    private static String exceptionPrefix() {
        return TAG + " tid=" + android.os.Process.myTid() + " ";
    }

    public interface OnThreadExitedListener {
        void onExited(Thread thread);
    }

    /**
     * 要编码的视频内容 GLES 渲染线程
     */
    static class GLThread extends WeGLThread {

        private WeakReference<WeGLVideoEncoder> mWeakReference;

        public GLThread(WeakReference<WeGLVideoEncoder> reference, String externalTag) {
            super(externalTag);
            this.mWeakReference = reference;
        }

        @Override
        protected EGLContext getEGLContext() {
            WeGLVideoEncoder encoder = mWeakReference.get();
            if (encoder == null) {
                LogUtils.e(TAG, mExternalTag + "GLThread getEGLContext failed: WeGLVideoEncoder got from mWeakReference is null");
                return null;
            }
            return encoder.mShareContext;
        }

        @Override
        protected Surface getSurface() {
            WeGLVideoEncoder encoder = mWeakReference.get();
            if (encoder == null) {
                LogUtils.e(TAG, mExternalTag + "GLThread getSurface failed: WeGLVideoEncoder got from mWeakReference is null");
                return null;
            }
            return encoder.mSurface;
        }

        @Override
        protected int getRenderMode() {
            WeGLVideoEncoder encoder = mWeakReference.get();
            if (encoder == null) {
                LogUtils.e(TAG, mExternalTag + "GLThread getRenderMode failed: WeGLVideoEncoder got from mWeakReference is null");
                return WeGLRenderer.RENDERMODE_CONTINUOUSLY;
            }
            return encoder.mRenderMode;
        }

        @Override
        protected WeGLRenderer getRenderer() {
            WeGLVideoEncoder encoder = mWeakReference.get();
            if (encoder == null) {
                LogUtils.e(TAG, mExternalTag + "GLThread getRenderer failed: WeGLVideoEncoder got from mWeakReference is null");
                return null;
            }
            return encoder.mRenderer;
        }

    }

    /**
     * 视频编码线程
     */
    static class VideoEncodeThread extends Thread {
        private static final String TAG = "VideoEncodeThread";

        private WeakReference<WeGLVideoEncoder> mWeakReference;
        private MediaCodec mVideoEncoder;
        private MediaCodec.BufferInfo mVideoBufInfo;
        private MediaMuxer mMediaMuxer;

        private int mOutputBufIndex;
        private int mVideoTrackIndex = -1;
        private long mStartPts = 0;

        private boolean isMuxerStarted;
        private boolean isShouldExit;
        private boolean isExited;

        private OnThreadExitedListener mOnExitedListener;

        public VideoEncodeThread(WeakReference<WeGLVideoEncoder> weakReference) {
            this.mWeakReference = weakReference;
            mVideoEncoder = mWeakReference.get().mVideoEncoder;
            mVideoBufInfo = mWeakReference.get().mVideoBufInfo;
            mMediaMuxer = mWeakReference.get().mMediaMuxer;
        }

        @Override
        public void run() {
            setName(TAG + " " + android.os.Process.myTid());
            LogUtils.w(TAG, "Video encode thread starting tid=" + android.os.Process.myTid());
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
            LogUtils.w(TAG, "Video encode thread end tid=" + android.os.Process.myTid());
        }

        private void guardedRun() {
            mVideoEncoder.start();
            while (!isShouldExit) {
                WeGLVideoEncoder master = mWeakReference.get();
                if (master == null) {
                    // 主类已被回收，因为某种原因没有来得及置退出标志，这里就直接退出线程
                    LogUtils.e(TAG, "WeGLVideoEncoder got from mWeakReference is null, so exit!");
                    return;
                }

                if (mVideoEncoder == null || mVideoBufInfo == null || mMediaMuxer == null) {
                    LogUtils.e(TAG, "mVideoEncoder or mVideoBufInfo or mMediaMuxer got from mWeakReference is null!");
                    return;
                }

                try {
                    mOutputBufIndex = mVideoEncoder.dequeueOutputBuffer(mVideoBufInfo, 0);
                    if (mOutputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        LogUtils.e(TAG, "mOutputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED");
                        mVideoTrackIndex = mMediaMuxer.addTrack(mVideoEncoder.getOutputFormat());
                        if (!isMuxerStarted) {
                            // MediaMuxer.start() is called after addTrack and before writeSampleData
                            mMediaMuxer.start();
                            isMuxerStarted = true;
                        }
                    } else {
                        while (mOutputBufIndex >= 0) {
                            ByteBuffer outBuffer = mVideoEncoder.getOutputBuffers()[mOutputBufIndex];
                            outBuffer.position(mVideoBufInfo.offset);
                            outBuffer.limit(mVideoBufInfo.offset + mVideoBufInfo.size);

                            if (isMuxerStarted) {
                                if (mStartPts == 0) {
                                    mStartPts = mVideoBufInfo.presentationTimeUs;
                                }
                                mVideoBufInfo.presentationTimeUs = mVideoBufInfo.presentationTimeUs - mStartPts;
                                mMediaMuxer.writeSampleData(mVideoTrackIndex, outBuffer, mVideoBufInfo);
                                master.mEncodeTimeMills = mVideoBufInfo.presentationTimeUs / 1000;
                            }

                            mVideoEncoder.releaseOutputBuffer(mOutputBufIndex, false);
                            mOutputBufIndex = mVideoEncoder.dequeueOutputBuffer(mVideoBufInfo, 0);
                        }
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
            // mVideoEncoder 系列在这里只置空，具体回收交给外部主类释放
            mVideoEncoder = null;
            mVideoBufInfo = null;
            mMediaMuxer = null;
            mWeakReference = null;

            isExited = true;
            if (mOnExitedListener != null) {
                mOnExitedListener.onExited(this);
            }
        }

    }

}
