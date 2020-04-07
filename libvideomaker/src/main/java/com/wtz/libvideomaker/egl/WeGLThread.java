package com.wtz.libvideomaker.egl;

import android.view.Surface;

import com.wtz.libvideomaker.utils.LogUtils;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGL11;
import javax.microedition.khronos.egl.EGLContext;


/**
 * GLES 渲染线程
 */
public abstract class WeGLThread extends Thread {

    private static final String TAG = "WeGLThread";
    protected String mExternalTag;

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

    protected abstract EGLContext getEGLContext();

    protected abstract Surface getSurface();

    protected abstract int getRenderMode();

    protected abstract WeGLRenderer getRenderer();

    public WeGLThread(String externalTag) {
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
        Surface surface = getSurface();
        if (surface == null) {
            LogUtils.e(TAG, mExternalTag + "initEgl getSurface is null");
            return false;
        }
        WeGLRenderer renderer = getRenderer();
        if (renderer == null) {
            LogUtils.e(TAG, mExternalTag + "initEgl getRenderer is null");
            return false;
        }

        mEglHelper = new WeEGLHelper(mExternalTag);
        mEglHelper.initEGL(surface, getEGLContext());
        renderer.onEGLContextCreated();
        return true;
    }

    public EGLContext getSharedEGLContext() {
        return mEglHelper != null ? mEglHelper.getSharedEGLContext() : null;
    }

    private void onSurfaceChanged() {
        WeGLRenderer renderer = getRenderer();
        if (renderer != null) {
            renderer.onSurfaceChanged(mWidth, mHeight);
        } else {
            LogUtils.e(TAG, mExternalTag + "onSurfaceChanged getRenderer is null!");
            // TODO
        }
    }

    private void onDraw() {
        WeGLRenderer renderer = getRenderer();
        if (renderer != null) {
            renderer.onDrawFrame();
            if (isFirstDraw) {
                isFirstDraw = false;
                // 首次绘制时，GLES 绘图指令需要执行两遍才能生效
                renderer.onDrawFrame();
            }
        } else {
            LogUtils.e(TAG, mExternalTag + "onDraw getRenderer is null!");
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
        if (getRenderMode() == WeGLRenderer.RENDERMODE_WHEN_DIRTY) {
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
        WeGLRenderer renderer = getRenderer();
        if (renderer != null) {
            renderer.onEGLContextToDestroy();
        }

        if (mEglHelper != null) {
            mEglHelper.destroyEGL();
            mEglHelper = null;
        }

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
