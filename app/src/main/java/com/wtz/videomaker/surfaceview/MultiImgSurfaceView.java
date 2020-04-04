package com.wtz.videomaker.surfaceview;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;

import com.wtz.libvideomaker.egl.WeGLSurfaceView;
import com.wtz.libvideomaker.renderer.OnScreenRenderer;
import com.wtz.libvideomaker.renderer.origins.ImgRenderer;
import com.wtz.libvideomaker.renderer.origins.MultiImgRenderer;
import com.wtz.videomaker.R;

public class MultiImgSurfaceView extends WeGLSurfaceView implements WeGLSurfaceView.WeRenderer, ImgRenderer.OnSharedTextureChangedListener {
    private static final String TAG = MultiImgSurfaceView.class.getSimpleName();

    private ImgRenderer mImgOffScreenRenderer;
    private OnScreenRenderer mOnScreenRenderer;

    public MultiImgSurfaceView(Context context) {
        this(context, null);
    }

    public MultiImgSurfaceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MultiImgSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setRenderMode(RENDERMODE_WHEN_DIRTY);

        mImgOffScreenRenderer = new MultiImgRenderer(context, new int[] {
                R.drawable.tree, R.drawable.sunflower, R.drawable.lotus,
                R.drawable.carry_up, R.drawable.happy
        });
        mImgOffScreenRenderer.setSharedTextureChangedListener(this);

        mOnScreenRenderer = new OnScreenRenderer(context, TAG);
        mOnScreenRenderer.setExternalTextureId(mImgOffScreenRenderer.getSharedTextureId());
    }

    @Override
    protected String getExternalLogTag() {
        return TAG;
    }

    @Override
    public void onSharedTextureChanged(int textureID) {
        mOnScreenRenderer.setExternalTextureId(textureID);
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
        mImgOffScreenRenderer.onEGLContextToDestroy();
        mOnScreenRenderer.onEGLContextToDestroy();
    }

}
