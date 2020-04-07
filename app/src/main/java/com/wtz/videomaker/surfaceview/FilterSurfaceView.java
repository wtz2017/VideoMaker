package com.wtz.videomaker.surfaceview;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;

import com.wtz.libvideomaker.egl.WeGLSurfaceView;
import com.wtz.libvideomaker.egl.WeGLRenderer;
import com.wtz.libvideomaker.renderer.OnScreenRenderer;
import com.wtz.libvideomaker.renderer.filters.FilterRenderer;

public abstract class FilterSurfaceView extends WeGLSurfaceView
        implements WeGLRenderer, FilterRenderer.OnFilterTextureChangedListener {

    private FilterRenderer mFilterRender;
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

        mFilterRender = createFilterRenderer(context);
        mFilterRender.setFilterTextureChangedListener(this);

        mOnScreenRenderer = new OnScreenRenderer(context, getExternalLogTag());
        mOnScreenRenderer.setExternalTextureId(mFilterRender.getFilterTextureId());
    }

    protected abstract FilterRenderer createFilterRenderer(Context context);

    public void setExternalTextureId(int id) {
        mFilterRender.setExternalTextureId(id);
    }

    @Override
    protected WeGLRenderer getRenderer() {
        return this;
    }

    @Override
    public void onEGLContextCreated() {
        Log.d(getExternalLogTag(), "onEGLContextCreated");
        mFilterRender.onEGLContextCreated();
        mOnScreenRenderer.onEGLContextCreated();
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        Log.d(getExternalLogTag(), "onSurfaceChanged " + width + "x" + height);
        mFilterRender.onSurfaceChanged(width, height);
        mOnScreenRenderer.onSurfaceChanged(width, height);
    }

    @Override
    public void onFilterTextureChanged(FilterRenderer renderer, int textureID) {
        mOnScreenRenderer.setExternalTextureId(textureID);
    }

    @Override
    public void onDrawFrame() {
        Log.d(getExternalLogTag(), "onDrawFrame");
        mFilterRender.onDrawFrame();
        mOnScreenRenderer.onDrawFrame();
    }

    @Override
    public void onEGLContextToDestroy() {
        Log.d(getExternalLogTag(), "onEGLContextToDestroy");
        mFilterRender.onEGLContextToDestroy();
        mOnScreenRenderer.onEGLContextToDestroy();
    }

}
