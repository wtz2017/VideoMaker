package com.wtz.libvideomaker.egl;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.wtz.libvideomaker.utils.LogUtils;

import java.lang.ref.WeakReference;

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

    private WeakReference<WeGLSurfaceView> mWeakReference;
    private WeGLThread mGLThread;
    private WeGLRenderer mRenderer;

    private int mRenderMode = WeGLRenderer.RENDERMODE_CONTINUOUSLY;

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

    protected abstract WeGLRenderer getRenderer();

    protected abstract String getExternalLogTag();

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

            mWeakReference = new WeakReference<>(this);
            mGLThread = new GLThread(mWeakReference, getExternalLogTag());
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
        if (mGLThread != null) {
            mGLThread.requestExit(new WeGLThread.OnExitedListener() {
                @Override
                public void onExited(WeGLThread glThread) {
                    LogUtils.w(TAG, mExternalTag + "mGLThread onExited: " + glThread.hashCode());
                    synchronized (WeGLSurfaceView.this) {// 与初始化创建资源同步
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
    }

    private void releaseOnGLThreadExit() {
        // 不用把外部导入的资源置空，因为这些资源是可能一次性设置的，如果要回收由外部设置空
//        mSurface = null;
//        mShareContext = null;
        mRenderer = null;
        mGLThread = null;
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
     * GLES 渲染线程
     */
    static class GLThread extends WeGLThread {

        private WeakReference<WeGLSurfaceView> mWeakReference;

        public GLThread(WeakReference<WeGLSurfaceView> reference, String externalTag) {
            super(externalTag);
            this.mWeakReference = reference;
        }

        @Override
        protected EGLContext getEGLContext() {
            WeGLSurfaceView view = mWeakReference.get();
            if (view == null) {
                LogUtils.e(TAG, mExternalTag + "GLThread getEGLContext failed: WeGLSurfaceView got from mWeakReference is null");
                return null;
            }
            return view.mShareContext;
        }

        @Override
        protected Surface getSurface() {
            WeGLSurfaceView view = mWeakReference.get();
            if (view == null) {
                LogUtils.e(TAG, mExternalTag + "GLThread getSurface failed: WeGLSurfaceView got from mWeakReference is null");
                return null;
            }
            return view.mSurface;
        }

        @Override
        protected int getRenderMode() {
            WeGLSurfaceView view = mWeakReference.get();
            if (view == null) {
                LogUtils.e(TAG, mExternalTag + "GLThread getRenderMode failed: WeGLSurfaceView got from mWeakReference is null");
                return WeGLRenderer.RENDERMODE_CONTINUOUSLY;
            }
            return view.mRenderMode;
        }

        @Override
        protected WeGLRenderer getRenderer() {
            WeGLSurfaceView view = mWeakReference.get();
            if (view == null) {
                LogUtils.e(TAG, mExternalTag + "GLThread getRenderer failed: WeGLSurfaceView got from mWeakReference is null");
                return null;
            }
            return view.mRenderer;
        }

    }

}
