package com.wtz.videomaker;

import android.content.Context;
import android.opengl.GLES20;
import android.util.AttributeSet;
import android.util.Log;

import com.wtz.libvideomaker.WeGLSurfaceView;

public class MySurfaceView extends WeGLSurfaceView implements WeGLSurfaceView.WeRenderer {
    private static final String TAG = "MySurfaceView";

    public MySurfaceView(Context context) {
        this(context, null);
    }

    public MySurfaceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MySurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
    }

    @Override
    protected WeRenderer getRenderer() {
        return this;
    }

    @Override
    public void onEGLContextCreated() {
        Log.d(TAG, "onEGLContextCreated");
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        Log.d(TAG, "onSurfaceChanged " + width + "x" + height);
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame() {
        Log.d(TAG, "onDrawFrame");
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glClearColor(1f, 0, 1f, 1f);
    }

    @Override
    public void onEGLContextToDestroy() {
        Log.d(TAG, "onEGLContextToDestroy");
    }
}
