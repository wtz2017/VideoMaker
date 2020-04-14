package com.wtz.libvideomaker.imagevideo;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;

import com.wtz.libvideomaker.egl.WeGLRenderer;
import com.wtz.libvideomaker.egl.WeGLSurfaceView;
import com.wtz.libvideomaker.renderer.OnScreenRenderer;
import com.wtz.libvideomaker.renderer.filters.WatermarkRenderer;
import com.wtz.libvideomaker.renderer.origins.ImgRenderer;
import com.wtz.libvideomaker.renderer.origins.SingleImgRenderer;
import com.wtz.libvideomaker.utils.LogUtils;


public class WeImageVideoView extends WeGLSurfaceView implements WeGLRenderer,
        ImgRenderer.OnSharedTextureChangedListener,
        WatermarkRenderer.OnMarkTextureChangedListener {

    private static final String TAG = WeImageVideoView.class.getSimpleName();

    private SingleImgRenderer mImgOffScreenRenderer;
    private WatermarkRenderer mWatermarkRenderer;
    private OnScreenRenderer mOnScreenRenderer;

    public void setScreenTextureChangeListener(OnScreenRenderer.ScreenTextureChangeListener listener) {
        mOnScreenRenderer.setScreenTextureChangeListener(listener);
    }

    public int getScreenTextureId() {
        return mOnScreenRenderer.getExternalTextureId();
    }

    public WeImageVideoView(Context context) {
        this(context, null);
    }

    public WeImageVideoView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WeImageVideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setRenderMode(RENDERMODE_WHEN_DIRTY);

        mImgOffScreenRenderer = new SingleImgRenderer(context);
        mImgOffScreenRenderer.setSharedTextureChangedListener(this);

        mWatermarkRenderer = new WatermarkRenderer(context);
        mWatermarkRenderer.setMarkTextureChangedListener(this);
        mWatermarkRenderer.setExternalTextureId(mImgOffScreenRenderer.getSharedTextureId());

        mOnScreenRenderer = new OnScreenRenderer(context, TAG);
        mOnScreenRenderer.setExternalTextureId(mWatermarkRenderer.getMarkTextureId());
    }

    @Override
    protected WeGLRenderer getRenderer() {
        return this;
    }

    @Override
    protected String getExternalLogTag() {
        return TAG;
    }

    public void setImageResource(int resId) {
        mImgOffScreenRenderer.setImageResource(resId);
        requestRender();
    }

    public void setImagePath(String path) {
        mImgOffScreenRenderer.setImagePath(path);
        requestRender();
    }

    public void setImageMark(Bitmap bitmap, int showWidth, int showHeight,
                             int corner, int marginX, int marginY) {
        mWatermarkRenderer.setImageMark(bitmap, showWidth, showHeight, corner, marginX, marginY);
    }

    public void setTextMark(String text, float textSizePixels, int paddingLeft, int paddingRight,
                            int paddingTop, int paddingBottom, int textColor, int bgColor,
                            int corner, int marginX, int marginY) {
        mWatermarkRenderer.setTextMark(text, textSizePixels, paddingLeft, paddingRight,
                paddingTop, paddingBottom, textColor, bgColor, corner, marginX, marginY);
    }

    public void changeImageMarkPosition(int corner, int marginX, int marginY) {
        mWatermarkRenderer.changeImageMarkPosition(corner, marginX, marginY);
    }

    public void changeTextMarkPosition(int corner, int marginX, int marginY) {
        mWatermarkRenderer.changeTextMarkPosition(corner, marginX, marginY);
    }

    @Override
    public void onSharedTextureChanged(int textureID) {
        mWatermarkRenderer.setExternalTextureId(textureID);
    }

    @Override
    public void onMarkTextureChanged(int textureID) {
        mOnScreenRenderer.setExternalTextureId(textureID);
    }

    @Override
    public void onEGLContextCreated() {
        LogUtils.d(TAG, "onEGLContextCreated");
        mImgOffScreenRenderer.onEGLContextCreated();
        mWatermarkRenderer.onEGLContextCreated();
        mOnScreenRenderer.onEGLContextCreated();
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        LogUtils.d(TAG, "onSurfaceChanged " + width + "x" + height);
        mImgOffScreenRenderer.onSurfaceChanged(width, height);
        mWatermarkRenderer.onSurfaceChanged(width, height);
        mOnScreenRenderer.onSurfaceChanged(width, height);
    }

    public void onActivityResume() {
        requestRender();
    }

    @Override
    public void onDrawFrame() {
        LogUtils.d(TAG, "onDrawFrame");
        mImgOffScreenRenderer.onDrawFrame();
        mWatermarkRenderer.onDrawFrame();
        mOnScreenRenderer.onDrawFrame();
    }

    @Override
    public void onEGLContextToDestroy() {
        LogUtils.d(TAG, "onEGLContextToDestroy");
        mImgOffScreenRenderer.onEGLContextToDestroy();
        mWatermarkRenderer.onEGLContextToDestroy();
        mOnScreenRenderer.onEGLContextToDestroy();
    }

    public void release() {
        mWatermarkRenderer.releaseMarkBitmap();
        mImgOffScreenRenderer.clearSourceImage();
    }

}
