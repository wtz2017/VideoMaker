package com.wtz.videomaker;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;

import com.wtz.libvideomaker.OffScreenRenderer;
import com.wtz.libvideomaker.ScreenRenderer;
import com.wtz.libvideomaker.WeGLSurfaceView;

public class MySurfaceView extends WeGLSurfaceView implements WeGLSurfaceView.WeRenderer {
    private static final String TAG = "MySurfaceView";

    private OffScreenRenderer mOffScreenRenderer;
    private ScreenRenderer mScreenRenderer;

    public MySurfaceView(Context context) {
        this(context, null);
    }

    public MySurfaceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MySurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        mOffScreenRenderer = new OffScreenRenderer(context, R.drawable.carry_up);
        mScreenRenderer = new ScreenRenderer(context);
    }

    @Override
    protected WeRenderer getRenderer() {
        return this;
    }

    @Override
    public void onEGLContextCreated() {
        Log.d(TAG, "onEGLContextCreated");
        mOffScreenRenderer.onEGLContextCreated();
        mScreenRenderer.setExternalTextureId(mOffScreenRenderer.getOutputTextureId());
        mScreenRenderer.onEGLContextCreated();
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        Log.d(TAG, "onSurfaceChanged " + width + "x" + height);
        mOffScreenRenderer.onSurfaceChanged(width, height);
        mScreenRenderer.onSurfaceChanged(width, height);
    }

    @Override
    public void onDrawFrame() {
        Log.d(TAG, "onDrawFrame");
        mOffScreenRenderer.onDrawFrame();
        mScreenRenderer.onDrawFrame();
    }

    @Override
    public void onEGLContextToDestroy() {
        Log.d(TAG, "onEGLContextToDestroy");
        mOffScreenRenderer.onEGLContextToDestroy();
        mScreenRenderer.onEGLContextToDestroy();
    }

}
