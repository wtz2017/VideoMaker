package com.wtz.videomaker.surfaceview;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;

import com.wtz.libvideomaker.egl.WeGLSurfaceView;
import com.wtz.libvideomaker.renderer.OnScreenRenderer;
import com.wtz.libvideomaker.renderer.origins.ImgRenderer;
import com.wtz.libvideomaker.renderer.origins.SingleImgRenderer;
import com.wtz.videomaker.R;

import javax.microedition.khronos.egl.EGLContext;

public class SingleImgSurfaceView extends WeGLSurfaceView implements WeGLSurfaceView.WeRenderer, ImgRenderer.OnSharedTextureChangedListener {
    private static final String TAG = SingleImgSurfaceView.class.getSimpleName();

    private ImgRenderer mImgOffScreenRenderer;
    private OnScreenRenderer mOnScreenRenderer;
    private ImgRenderer.OnSharedTextureChangedListener mOnSharedTextureChangedListener;

    public interface OnEGLContextCreatedListener {
        void onEGLContextCreated(EGLContext eglContext);
    }

    public interface OnEGLContextToDestroyListener {
        void onEGLContextToDestroy();
    }

    private OnEGLContextCreatedListener mOnEGLContextCreatedListener;
    private OnEGLContextToDestroyListener mOnEGLContextToDestroyListener;

    public void setOnEGLContextCreatedListener(OnEGLContextCreatedListener listener) {
        this.mOnEGLContextCreatedListener = listener;
    }

    public void setOnEGLContextToDestroyListener(OnEGLContextToDestroyListener listener) {
        this.mOnEGLContextToDestroyListener = listener;
    }

    public SingleImgSurfaceView(Context context) {
        this(context, null);
    }

    public SingleImgSurfaceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SingleImgSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setRenderMode(RENDERMODE_WHEN_DIRTY);

        mImgOffScreenRenderer = new SingleImgRenderer(context, R.drawable.carry_up);
        mImgOffScreenRenderer.setSharedTextureChangedListener(this);

        mOnScreenRenderer = new OnScreenRenderer(context, TAG);
        mOnScreenRenderer.setExternalTextureId(mImgOffScreenRenderer.getSharedTextureId());
    }

    @Override
    protected String getExternalLogTag() {
        return TAG;
    }

    public int getSharedTextureId() {
        return mImgOffScreenRenderer.getSharedTextureId();
    }

    public void setSharedTextureChangedListener(ImgRenderer.OnSharedTextureChangedListener listener) {
        this.mOnSharedTextureChangedListener = listener;
    }

    @Override
    public void onSharedTextureChanged(int textureID) {
        mOnScreenRenderer.setExternalTextureId(textureID);
        if (mOnSharedTextureChangedListener != null) {
            mOnSharedTextureChangedListener.onSharedTextureChanged(textureID);
        }
    }

    @Override
    protected WeRenderer getRenderer() {
        return this;
    }

    @Override
    public void onEGLContextCreated() {
        Log.d(TAG, "onEGLContextCreated");
        mImgOffScreenRenderer.onEGLContextCreated();
        mOnScreenRenderer.onEGLContextCreated();
        if (mOnEGLContextCreatedListener != null) {
            mOnEGLContextCreatedListener.onEGLContextCreated(getSharedEGLContext());
        }
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        Log.d(TAG, "onSurfaceChanged " + width + "x" + height);
        mImgOffScreenRenderer.onSurfaceChanged(width, height);
        mOnScreenRenderer.onSurfaceChanged(width, height);
    }

    @Override
    public void onDrawFrame() {
        Log.d(TAG, "onDrawFrame");
        mImgOffScreenRenderer.onDrawFrame();
        mOnScreenRenderer.onDrawFrame();
    }

    @Override
    public void onEGLContextToDestroy() {
        Log.d(TAG, "onEGLContextToDestroy");
        if (mOnEGLContextToDestroyListener != null) {
            mOnEGLContextToDestroyListener.onEGLContextToDestroy();
        }
        mImgOffScreenRenderer.onEGLContextToDestroy();
        mOnScreenRenderer.onEGLContextToDestroy();
    }

}
