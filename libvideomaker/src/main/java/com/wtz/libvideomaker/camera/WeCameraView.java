package com.wtz.libvideomaker.camera;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.text.TextUtils;
import android.util.AttributeSet;

import com.wtz.libvideomaker.egl.WeGLSurfaceView;
import com.wtz.libvideomaker.egl.WeGLRenderer;
import com.wtz.libvideomaker.renderer.OnScreenRenderer;
import com.wtz.libvideomaker.renderer.filters.FilterRenderer;
import com.wtz.libvideomaker.renderer.filters.GrayFilterRenderer;
import com.wtz.libvideomaker.renderer.filters.ReverseFilterRenderer;
import com.wtz.libvideomaker.renderer.filters.WatermarkRenderer;
import com.wtz.libvideomaker.renderer.origins.CameraRenderer;
import com.wtz.libvideomaker.utils.LogUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class WeCameraView extends WeGLSurfaceView implements WeGLRenderer,
        CameraRenderer.OnSharedTextureChangedListener, CameraRenderer.SurfaceTextureListener,
        FilterRenderer.OnFilterTextureChangedListener, WatermarkRenderer.OnMarkTextureChangedListener {
    private static final String TAG = WeCameraView.class.getSimpleName();

    private static final int RENDER_FPS = 30;//大部分摄像头最高30fps，FPS过高会导致部分低端机型渲染闪屏

    private int mCameraViewWidth;
    private int mCameraViewHeight;

    boolean isTakingPhoto;
    private String mSaveImageDir;
    private static final String PHOTO_PREFIX = "We_IMG_";
    private static final String PHOTO_SUFFIX = ".jpg";
    private final SimpleDateFormat mSimpleDateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");

    private WeCamera mCamera;
    private int mCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;

    private CameraRenderer mCameraRenderer;
    private FilterRenderer mFilterRenderer;
    private FilterRenderer mGrayFilterRenderer;
    private FilterRenderer mReverseFilterRenderer;
    private WatermarkRenderer mWatermarkRenderer;
    private OnScreenRenderer mOnScreenRenderer;

    public enum PictureRenderType {
        NORMAL, GRAY, COLOR_REVERSE
    }

    public interface OnCameraSizeChangedListener {
        void onCameraSizeChanged(int surfaceWidth, int surfaceHeight,
                                 int previewWidth, int previewHeight);
    }

    private OnCameraSizeChangedListener mOnCameraSizeChangedListener;

    public WeCameraView(Context context) {
        this(context, null);
    }

    public WeCameraView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WeCameraView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
//        setRenderMode(RENDERMODE_WHEN_DIRTY);
        // 暂时用持续渲染模式解决脏模式下有时 onFrameAvailable 不回调导致不渲染的 BUG
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        setRenderFps(RENDER_FPS);

        mCameraRenderer = new CameraRenderer(context, this);
        mCameraRenderer.setClearScreenOnDraw(false);//
        mCameraRenderer.setSharedTextureChangedListener(this);

        mGrayFilterRenderer = new GrayFilterRenderer(context);
        mGrayFilterRenderer.setFilterTextureChangedListener(this);
        mReverseFilterRenderer = new ReverseFilterRenderer(context);
        mReverseFilterRenderer.setFilterTextureChangedListener(this);

        mWatermarkRenderer = new WatermarkRenderer(context);
        mWatermarkRenderer.setMarkTextureChangedListener(this);
        mWatermarkRenderer.setExternalTextureId(mCameraRenderer.getSharedTextureId());

        mOnScreenRenderer = new OnScreenRenderer(context, TAG);
        mOnScreenRenderer.setExternalTextureId(mWatermarkRenderer.getMarkTextureId());
    }

    public void setClearScreenOnDraw(boolean clearScreen) {
        mCameraRenderer.setClearScreenOnDraw(clearScreen);
        mGrayFilterRenderer.setClearScreenOnDraw(clearScreen);
        mReverseFilterRenderer.setClearScreenOnDraw(clearScreen);
        mWatermarkRenderer.setClearScreenOnDraw(clearScreen);
        mOnScreenRenderer.setClearScreenOnDraw(clearScreen);
    }

    public void setOnCameraSizeChangedListener(OnCameraSizeChangedListener listener) {
        this.mOnCameraSizeChangedListener = listener;
    }

    public void setScreenTextureChangeListener(OnScreenRenderer.ScreenTextureChangeListener listener) {
        mOnScreenRenderer.setScreenTextureChangeListener(listener);
    }

    public int getScreenTextureId() {
        return mOnScreenRenderer.getExternalTextureId();
    }

    public void setPictureRenderType(PictureRenderType type) {
        switch (type) {
            case NORMAL:
                mFilterRenderer = null;
                break;

            case GRAY:
                mFilterRenderer = mGrayFilterRenderer;
                break;

            case COLOR_REVERSE:
                mFilterRenderer = mReverseFilterRenderer;
                break;
        }
        if (mFilterRenderer == null) {
            mWatermarkRenderer.setExternalTextureId(mCameraRenderer.getSharedTextureId());
        } else {
            mFilterRenderer.setExternalTextureId(mCameraRenderer.getSharedTextureId());
            mWatermarkRenderer.setExternalTextureId(mFilterRenderer.getFilterTextureId());
        }
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

    public void setSaveImageDir(String imageDir) {
        LogUtils.d(TAG, "setSaveImageDir: " + imageDir);
        this.mSaveImageDir = imageDir;
    }

    @Override
    protected String getExternalLogTag() {
        return TAG;
    }

    @Override
    public void onSharedTextureChanged(int textureID) {
        if (mFilterRenderer != null) {
            mFilterRenderer.setExternalTextureId(textureID);
        } else {
            mWatermarkRenderer.setExternalTextureId(textureID);
        }
    }

    @Override
    public void onFilterTextureChanged(FilterRenderer renderer, int textureID) {
        if (mFilterRenderer != renderer) {
            return;
        }
        mWatermarkRenderer.setExternalTextureId(mFilterRenderer.getFilterTextureId());
    }

    @Override
    public void onMarkTextureChanged(int textureID) {
        mOnScreenRenderer.setExternalTextureId(textureID);
    }

    @Override
    protected WeGLRenderer getRenderer() {
        return this;
    }

    @Override
    public void onEGLContextCreated() {
        LogUtils.d(TAG, "onEGLContextCreated");
        mCameraRenderer.onEGLContextCreated();
        mGrayFilterRenderer.onEGLContextCreated();
        mReverseFilterRenderer.onEGLContextCreated();
        mWatermarkRenderer.onEGLContextCreated();
        mOnScreenRenderer.onEGLContextCreated();
    }

    @Override
    public void onSurfaceTextureCreated(SurfaceTexture surfaceTexture) {
        LogUtils.d(TAG, "onEGLContextCreated");
        mCamera = new WeCamera(surfaceTexture, getContext());
        int[] size = mCamera.startPreview(mCameraId, mCameraViewWidth, mCameraViewHeight);
        if (size != null) {
            mCameraRenderer.initCameraParams(mCameraId, size[0], size[1]);
        } else {
            // 相机打开失败
            LogUtils.e(TAG, "onSurfaceTextureCreated camera open failed! id=" + mCameraId);
        }
    }

    public void startBackCamera() {
        startCamera(Camera.CameraInfo.CAMERA_FACING_BACK);
    }

    public void startFrontCamera() {
        startCamera(Camera.CameraInfo.CAMERA_FACING_FRONT);
    }

    private void startCamera(int cameraId) {
        this.mCameraId = cameraId;
        if (mCamera != null) {
            int[] size = mCamera.changeCamera(mCameraId, mCameraViewWidth, mCameraViewHeight);
            if (size != null) {
                mCameraRenderer.onCameraChanged(mCameraId, size[0], size[1]);
            } else {
                // 相机打开失败
                LogUtils.e(TAG, "changeCamera open failed! id=" + mCameraId);
            }
        }
    }

    public void stopPreview() {
        if (mCamera != null) {
            mCamera.stopPreview();
        }
    }

    public void onActivityResume() {
        requestRender();
    }

    public void onActivityConfigurationChanged(Configuration newConfig) {
        LogUtils.d(TAG, "onActivityConfigurationChanged " + newConfig);
        mCameraRenderer.onOrientationChanged();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        LogUtils.d(TAG, "onSizeChanged " + w + "x" + h);
        super.onSizeChanged(w, h, oldw, oldh);
        mCameraViewWidth = w;
        mCameraViewHeight = h;
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        LogUtils.d(TAG, "onSurfaceChanged " + width + "x" + height);
        mCameraRenderer.onSurfaceChanged(width, height);
        mGrayFilterRenderer.onSurfaceChanged(width, height);
        mReverseFilterRenderer.onSurfaceChanged(width, height);
        mWatermarkRenderer.onSurfaceChanged(width, height);
        mOnScreenRenderer.onSurfaceChanged(width, height);

        int[] size = mCamera.fitSurfaceSize(width, height);
        if (size != null) {
            mCameraRenderer.onCameraChanged(mCameraId, size[0], size[1]);
            if (mOnCameraSizeChangedListener != null) {
                mOnCameraSizeChangedListener.onCameraSizeChanged(width, height, size[0], size[1]);
            }
        }
    }

    @Override
    public void onFrameAvailable() {
//        requestRender();//脏模式渲染才需要主动刷新
    }

    public void takePhoto() {
        if (mCamera == null || TextUtils.isEmpty(mSaveImageDir)) {
            return;
        }
        isTakingPhoto = true;
    }

    @Override
    public void onDrawFrame() {
        mCameraRenderer.onDrawFrame();
        if (mFilterRenderer != null) {
            mFilterRenderer.onDrawFrame();
        }
        mWatermarkRenderer.onDrawFrame();
        mOnScreenRenderer.onDrawFrame();
        if (isTakingPhoto) {
            isTakingPhoto = false;
            mOnScreenRenderer.takePhoto(getPhotoPathName());
        }
    }

    private String getPhotoPathName() {
        String time = mSimpleDateFormat.format(new Date());
        return new File(mSaveImageDir, PHOTO_PREFIX + time + PHOTO_SUFFIX).getAbsolutePath();
    }

    @Override
    public void onEGLContextToDestroy() {
        LogUtils.d(TAG, "onEGLContextToDestroy");
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera = null;
        }
        mCameraRenderer.onEGLContextToDestroy();
        mGrayFilterRenderer.onEGLContextToDestroy();
        mReverseFilterRenderer.onEGLContextToDestroy();
        mWatermarkRenderer.onEGLContextToDestroy();
        mOnScreenRenderer.onEGLContextToDestroy();
    }

    public void release() {
        mWatermarkRenderer.releaseMarkBitmap();
    }

}
