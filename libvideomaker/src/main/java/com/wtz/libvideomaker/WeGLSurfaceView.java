package com.wtz.libvideomaker;

import android.content.Context;
import android.util.AttributeSet;
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
    private String mExternalTag;

    private EGLContext mShareContext;
    private Surface mSurface;

    /**
     * 此 View 是否可用，true 可用
     */
    private boolean isUsable;

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
        mExternalTag = getExternalLogTag() + ": ";
        this.isUsable = true;
        LogUtils.w(TAG, mExternalTag + "WeGLSurfaceView created");
    }

    /**
     * 用于少数情况下暂时不需要使用了，但未来得及删除此 view
     */
    public void setUsability(boolean isUsable) {
        this.isUsable = isUsable;
    }

    protected abstract WeRenderer getRenderer();

    protected abstract String getExternalLogTag();

    public void setRenderMode(int renderMode) {
        if ((RENDERMODE_WHEN_DIRTY != renderMode) && (renderMode != RENDERMODE_CONTINUOUSLY)) {
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

    /**
     * 在 surfaceCreated 之前调用有效
     *
     * @param context
     * @param surface
     */
    public void importEGLContext(EGLContext context, Surface surface) {
        this.mShareContext = context;
        this.mSurface = surface;
    }

    public EGLContext getSharedEGLContext() {
        return mGLThread != null ? mGLThread.getSharedEGLContext() : null;
    }

    @Override
    protected void onAttachedToWindow() {
        LogUtils.w(TAG, mExternalTag + "onAttachedToWindow");
        super.onAttachedToWindow();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        LogUtils.w(TAG, mExternalTag + "surfaceCreated isUsable=" + isUsable);
        if (!isUsable) {
            return;
        }

        if (mSurface == null) {
            // 若没有导入外部 Surface，则使用自己的 Surface
            mSurface = holder.getSurface();
        }

        synchronized (WeGLSurfaceView.this) {// 与退出销毁资源同步
            mRenderer = getRenderer();
            if (mRenderer == null) {
                throw new RuntimeException("The render from getRenderer can't be null!");
            }

            mGLThread = new WeGLThread(new WeakReference<>(this), getExternalLogTag());
            mGLThread.start();
            LogUtils.w(TAG, mExternalTag + "mGLThread start: " + mGLThread.hashCode());
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        LogUtils.w(TAG, mExternalTag + "surfaceChanged " + width + "x" + height + ", isUsable=" + isUsable);
        if (!isUsable) {
            return;
        }

        mGLThread.onWindowResize(width, height);
        mGLThread.requestRender();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        LogUtils.w(TAG, mExternalTag + "surfaceDestroyed");
        if (mGLThread == null) {
            // 在被屏蔽使用的情况下走到这里
            return;
        }
        mGLThread.requestExit(new WeGLThread.OnExitedListener() {
            @Override
            public void onExited(WeGLThread glThread) {
                LogUtils.w(TAG, mExternalTag + "mGLThread onExited: " + glThread.hashCode());
                synchronized (WeGLSurfaceView.this) {// 与初始化创建资源同步
                    if (glThread != mGLThread) {
                        // 新的线程已经创建
                        return;
                    }
                    // 不用把外部导入的资源置空，因为这些资源是可能一次性设置的，如果要回收由外部设置空
//                    mSurface = null;
//                    mShareContext = null;
                    mRenderer = null;
                    mGLThread = null;
                }
            }
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        LogUtils.w(TAG, mExternalTag + "onDetachedFromWindow");
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
        private String mExternalTag;

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
        private boolean isExited;
        private boolean isSurfaceChanged;
        private boolean isFirstDraw = true;

        private OnExitedListener mOnExitedListener;

        public WeGLThread(WeakReference<WeGLSurfaceView> reference, String externalTag) {
            this.mWeakReference = reference;
            this.mExternalTag = externalTag + ": ";
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
            LogUtils.w(TAG, mExternalTag + "Render thread starting tid=" + android.os.Process.myTid());
            try {
                guardedRun();
            } catch (Throwable e) {
                LogUtils.e(TAG, mExternalTag + "catch exception: " + e.toString());
                if (isShouldExit) {
                    LogUtils.e(TAG, "Because isShouldExit = true, so ignore this exception");
                } else {
                    throw e;
                }
            } finally {
                release();
            }
            LogUtils.w(TAG, mExternalTag + "Render thread end tid=" + android.os.Process.myTid());
        }

        private void guardedRun() {
            if (!initEgl()) {
                return;
            }

            while (!isShouldExit) {
                if (isSurfaceChanged) {
                    isSurfaceChanged = false;
                    onSurfaceChanged();
                    onDraw();// 解决脏模式下当surface大小改变时不多画一次就不能正确绘制的问题
                    swap();
                    // 这里不需要等待，可以走到下一个循环直接画第二次
                } else {
                    onDraw();
                    swap();
                    applyRenderMode();
                }
            }
        }

        private boolean initEgl() {
            WeGLSurfaceView view = mWeakReference.get();
            if (view == null) {
                LogUtils.e(TAG, mExternalTag + "initEgl failed: WeGLSurfaceView got from mWeakReference is null");
                return false;
            }

            mEglHelper = new WeEGLHelper(mExternalTag);
            mEglHelper.initEGL(view.mSurface, view.mShareContext);
            view.mRenderer.onEGLContextCreated();
            return true;
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
                LogUtils.e(TAG, mExternalTag + "onSurfaceChanged WeGLSurfaceView got from mWeakReference is null!");
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
                LogUtils.e(TAG, mExternalTag + "onDraw WeGLSurfaceView got from mWeakReference is null!");
                // TODO
            }
        }

        private void swap() {
            int ret = mEglHelper.swapBuffers();
            switch (ret) {
                case EGL10.EGL_SUCCESS:
                    break;
                case EGL11.EGL_CONTEXT_LOST:
                    LogUtils.e(TAG, mExternalTag + "Tid=" + android.os.Process.myTid() + " EGL context lost!");
                    //TODO lostEglContext = true;
                    break;
                default:
                    // Other errors typically mean that the current surface is bad,
                    // probably because the SurfaceView surface has been destroyed,
                    // but we haven't been notified yet.
                    // Log the error to help developers understand why rendering stopped.
                    LogUtils.e(TAG, mExternalTag + "Tid=" + android.os.Process.myTid() + " eglSwapBuffers error:" + ret);
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
                LogUtils.e(TAG, mExternalTag + "applyRenderMode WeGLSurfaceView got from mWeakReference is null!");
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
                    // LogUtils.e(TAG, mExternalTag + "Render too slow!!! " + mRenderInterval + "ms");
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
            void onExited(WeGLThread glThread);
        }

        public void requestExit(OnExitedListener listener) {
            this.mOnExitedListener = listener;
            if (isExited) {
                if (mOnExitedListener != null) {
                    mOnExitedListener.onExited(this);
                }
            } else {
                isShouldExit = true;
                requestRender();
            }
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

            isExited = true;
            if (mOnExitedListener != null) {
                mOnExitedListener.onExited(this);
            }
        }

        private static String exceptionPrefix() {
            return TAG + " tid=" + android.os.Process.myTid() + " ";
        }

    }

}
