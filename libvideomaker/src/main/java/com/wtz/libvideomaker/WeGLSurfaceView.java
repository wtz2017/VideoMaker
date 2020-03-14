package com.wtz.libvideomaker;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.wtz.libvideomaker.utils.LogUtils;

import java.lang.ref.WeakReference;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGL11;
import javax.microedition.khronos.egl.EGLContext;

/**
 * 尽可能用简单的方式来达到目标
 */
public abstract class WeGLSurfaceView extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = "WeGLSurfaceView";

    private EGLContext mShareContext;
    private Surface mSurface;

    private WeGLThread mGLThread;
    private WeRenderer mRenderer;

    /**
     * The renderer only renders
     * when the surface is created, or when {@link #requestRender()} is called.
     */
    public static final int RENDERMODE_WHEN_DIRTY = 0;

    /**
     * The renderer is called
     * continuously to re-render the scene.
     */
    public static final int RENDERMODE_CONTINUOUSLY = 1;

    private int mRenderMode = RENDERMODE_CONTINUOUSLY;

    public WeGLSurfaceView(Context context) {
        this(context, null);
    }

    public WeGLSurfaceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WeGLSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        getHolder().addCallback(this);
    }

    protected abstract WeRenderer getRenderer();

    public void setRenderMode(int renderMode) {
        if ((RENDERMODE_WHEN_DIRTY != renderMode) && (renderMode != RENDERMODE_CONTINUOUSLY)) {
            throw new IllegalArgumentException(
                    exceptionPrefix() + "illegal argument: renderMode " + renderMode);
        }
        mRenderMode = renderMode;
    }

    public void requestRender() {
        if (mGLThread == null) {
            LogUtils.e(TAG, exceptionPrefix()
                    + "GLThread is null! You can't call requestRender before onEGLContextCreated.");
            return;
        }
        mGLThread.requestRender();
    }

    public void importEGLContext(EGLContext context, Surface surface) {
        this.mShareContext = context;
        this.mSurface = surface;
    }

    public EGLContext getSharedEGLContext() {
        return mGLThread != null ? mGLThread.getSharedEGLContext() : null;
    }

    @Override
    protected void onAttachedToWindow() {
        LogUtils.w(TAG, "onAttachedToWindow");
        super.onAttachedToWindow();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        LogUtils.w(TAG, "surfaceCreated");
        if (mSurface == null) {
            // 若没有导入外部 Surface，则使用自己的 Surface
            mSurface = holder.getSurface();
        }

        mRenderer = getRenderer();
        if (mRenderer == null) {
            throw new RuntimeException("The render from getRenderer can't be null!");
        }

        mGLThread = new WeGLThread(new WeakReference<>(this));
        mGLThread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        LogUtils.w(TAG, "surfaceChanged " + width + "x" + height);
        mGLThread.onWindowResize(width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        LogUtils.w(TAG, "surfaceDestroyed");
        mGLThread.requestExit(new WeGLThread.OnExitedListener() {
            @Override
            public void onExited() {
                mSurface = null;
                mShareContext = null;
                mRenderer = null;
            }
        });
        mGLThread = null;
    }

    @Override
    protected void onDetachedFromWindow() {
        LogUtils.w(TAG, "onDetachedFromWindow");
        super.onDetachedFromWindow();
    }

    private static String exceptionPrefix() {
        return TAG + " tid=" + android.os.Process.myTid() + " ";
    }

    /**
     * 保证接口方法在 WeGLThread 中回调
     */
    public interface WeRenderer {

        void onEGLContextCreated();

        void onSurfaceChanged(int width, int height);

        void onDrawFrame();

        void onEGLContextToDestroy();

    }

    /**
     * GLES 渲染线程
     */
    static class WeGLThread extends Thread {
        private static final String TAG = "WeGLThread";

        private WeakReference<WeGLSurfaceView> mWeakReference;
        private WeEGLHelper mEglHelper;

        private Object mRenderLock;
        // 在配置 RGBA 32、EGL_DEPTH_SIZE 8、EGL_STENCIL_SIZE 8 情况下：
        // 在 MI 4LTE Android 4.4.4 上测试 eglSwapBuffers 本身耗时约 0 或 10 ms，其中 10 ms 占比 20%
        // 在 HUAWEI DUA-AL00 Android 8.1.0 上测试 eglSwapBuffers 本身耗时约 2-5 ms
        private static final int RENDER_TIME_INTERVAL = 16;// 期望 1 秒 60 帧包括指令耗时在内的总间隔
        private long mNextFrameStartTime;
        private int mRenderInterval;

        private int mWidth;
        private int mHeight;

        private boolean isShouldExit;
        private boolean isSurfaceChanged;
        private boolean isFirstDraw = true;

        private OnExitedListener mOnExitedListener;

        public WeGLThread(WeakReference<WeGLSurfaceView> reference) {
            this.mWeakReference = reference;
            mRenderLock = new Object();
        }

        public void onWindowResize(int w, int h) {
            this.isSurfaceChanged = true;
            this.mWidth = w;
            this.mHeight = h;
        }

        @Override
        public void run() {
            setName(TAG + " " + android.os.Process.myTid());
            LogUtils.w(TAG, "Render thread starting tid=" + android.os.Process.myTid());
            try {
                guardedRun();
            } finally {
                release();
            }
            LogUtils.w(TAG, "Render thread end tid=" + android.os.Process.myTid());
        }

        private void guardedRun() {
            initEgl();

            while (!isShouldExit) {
                if (isSurfaceChanged) {
                    isSurfaceChanged = false;
                    onSurfaceChanged();
                }
                onDraw();
                swap();
                applyRenderMode();
            }
        }

        private void initEgl() {
            WeGLSurfaceView view = mWeakReference.get();
            if (view == null) {
                throw new RuntimeException(exceptionPrefix() + "initEgl: WeGLSurfaceView got from mWeakReference is null");
            }
            mEglHelper = new WeEGLHelper();
            mEglHelper.initEGL(view.mSurface, view.mShareContext);
            view.mRenderer.onEGLContextCreated();
        }

        public EGLContext getSharedEGLContext() {
            return mEglHelper != null ? mEglHelper.getSharedEGLContext() : null;
        }

        private void onSurfaceChanged() {
            WeGLSurfaceView view = mWeakReference.get();
            if (view != null) {
                if (view.mRenderer != null) {
                    view.mRenderer.onSurfaceChanged(mWidth, mHeight);
                }
            } else {
                LogUtils.e(TAG, "onSurfaceChanged WeGLSurfaceView got from mWeakReference is null!");
                // TODO
            }
        }

        private void onDraw() {
            WeGLSurfaceView view = mWeakReference.get();
            if (view != null) {
                if (view.mRenderer != null) {
                    view.mRenderer.onDrawFrame();
                    if (isFirstDraw) {
                        isFirstDraw = false;
                        // 首次绘制时，GLES 绘图指令需要执行两遍才能生效
                        view.mRenderer.onDrawFrame();
                    }
                }
            } else {
                LogUtils.e(TAG, "onDraw WeGLSurfaceView got from mWeakReference is null!");
                // TODO
            }
        }

        private void swap() {
            int ret = mEglHelper.swapBuffers();
            switch (ret) {
                case EGL10.EGL_SUCCESS:
                    break;
                case EGL11.EGL_CONTEXT_LOST:
                    LogUtils.e(TAG, "Tid=" + android.os.Process.myTid() + " EGL context lost!");
                    //TODO lostEglContext = true;
                    break;
                default:
                    // Other errors typically mean that the current surface is bad,
                    // probably because the SurfaceView surface has been destroyed,
                    // but we haven't been notified yet.
                    // Log the error to help developers understand why rendering stopped.
                    LogUtils.e(TAG, "Tid=" + android.os.Process.myTid() + " eglSwapBuffers error:" + ret);
                    //TODO mSurfaceIsBad = true;
                    break;
            }
        }

        private void applyRenderMode() {
            int renderMode = RENDERMODE_CONTINUOUSLY;
            WeGLSurfaceView view = mWeakReference.get();
            if (view != null) {
                renderMode = view.mRenderMode;
            } else {
                LogUtils.e(TAG, "applyRenderMode WeGLSurfaceView got from mWeakReference is null!");
                // TODO
            }
            if (renderMode == RENDERMODE_WHEN_DIRTY) {
                synchronized (mRenderLock) {
                    try {
                        mRenderLock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                if (mNextFrameStartTime == 0) {
                    mRenderInterval = RENDER_TIME_INTERVAL - 3;
                } else {
                    mRenderInterval = RENDER_TIME_INTERVAL - (int) (System.currentTimeMillis() - mNextFrameStartTime);
                }
                if (mRenderInterval <= 0) {
                    // LogUtils.e(TAG, "Render too slow!!! " + mRenderInterval + "ms");
                } else {
                    try {
                        Thread.sleep(mRenderInterval);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                mNextFrameStartTime = System.currentTimeMillis();
            }
        }

        public void requestRender() {
            synchronized (mRenderLock) {
                mRenderLock.notifyAll();
            }
        }

        public interface OnExitedListener {
            void onExited();
        }

        public void requestExit(OnExitedListener listener) {
            this.mOnExitedListener = listener;
            isShouldExit = true;
            requestRender();
        }

        private void release() {
            WeGLSurfaceView view = mWeakReference.get();
            if (view != null && view.mRenderer != null) {
                view.mRenderer.onEGLContextToDestroy();
            }

            if (mEglHelper != null) {
                mEglHelper.destroyEGL();
                mEglHelper = null;
            }

            mWeakReference.clear();
            mWeakReference = null;
            mRenderLock = null;

            if (mOnExitedListener != null) {
                mOnExitedListener.onExited();
            }
        }

        private static String exceptionPrefix() {
            return TAG + " tid=" + android.os.Process.myTid() + " ";
        }

    }

}
