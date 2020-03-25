package com.wtz.videomaker.surfaceview;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;

import com.wtz.libvideomaker.WeGLSurfaceView;
import com.wtz.libvideomaker.renderer.OnScreenRenderer;

public abstract class FilterSurfaceView extends WeGLSurfaceView implements WeGLSurfaceView.WeRenderer {

    private OnScreenRenderer mOnScreenRenderer;

    public FilterSurfaceView(Context context) {
        this(context, null);
    }

    public FilterSurfaceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FilterSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setRenderMode(RENDERMODE_WHEN_DIRTY);

        mOnScreenRenderer = createRenderer(context);
    }

    protected abstract OnScreenRenderer createRenderer(Context context);

    public void setExternalTextureId(int id) {
        mOnScreenRenderer.setExternalTextureId(id);
    }

    @Override
    protected WeRenderer getRenderer() {
        return this;
    }

    @Override
    public void onEGLContextCreated() {
        Log.d(getExternalLogTag(), "onEGLContextCreated");
        mOnScreenRenderer.onEGLContextCreated();
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        Log.d(getExternalLogTag(), "onSurfaceChanged " + width + "x" + height);
        mOnScreenRenderer.onSurfaceChanged(width, height);
    }

    @Override
    public void onDrawFrame() {
        Log.d(getExternalLogTag(), "onDrawFrame");
        mOnScreenRenderer.onDrawFrame();
    }

    @Override
    public void onEGLContextToDestroy() {
        Log.d(getExternalLogTag(), "onEGLContextToDestroy");
        mOnScreenRenderer.onEGLContextToDestroy();
    }

}
