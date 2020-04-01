package com.wtz.libvideomaker.camera;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.text.TextUtils;
import android.util.AttributeSet;

import com.wtz.libvideomaker.egl.WeGLSurfaceView;
import com.wtz.libvideomaker.renderer.GrayScreenRenderer;
import com.wtz.libvideomaker.renderer.NormalScreenRenderer;
import com.wtz.libvideomaker.renderer.OffScreenCameraRenderer;
import com.wtz.libvideomaker.renderer.OnScreenRenderer;
import com.wtz.libvideomaker.renderer.ReverseScreenRenderer;
import com.wtz.libvideomaker.utils.LogUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class WeCameraView extends WeGLSurfaceView implements WeGLSurfaceView.WeRenderer,
        OffScreenCameraRenderer.OnSharedTextureChangedListener,
        OffScreenCameraRenderer.SurfaceTextureListener {
    private static final String TAG = WeCameraView.class.getSimpleName();

    private int mCameraViewWidth;
    private int mCameraViewHeight;

    boolean isTakingPhoto;
    private String mSaveImageDir;
    private static final String PHOTO_PREFIX = "WePhoto_";
    private static final String PHOTO_SUFFIX = ".jpg";
    private final SimpleDateFormat mSimpleDateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");

    private WeCamera mCamera;
    private int mCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;

    private OffScreenCameraRenderer mOffScreenCameraRenderer;
    private OnScreenRenderer mNormalScreenRenderer;
    private OnScreenRenderer mGrayScreenRenderer;
    private OnScreenRenderer mReverseScreenRenderer;
    private OnScreenRenderer mDrawScreenRenderer;

    public enum PictureRenderType {
        NORMAL, GRAY, COLOR_REVERSE
    }

    public interface OnCameraSizeChangedListener {
        void onCameraSizeChanged(int surfaceWidth, int surfaceHeight,
                                 int previewWidth, int previewHeight);
    }

    private OnCameraSizeChangedListener mOnCameraSizeChangedListener;

    public void setOnCameraSizeChangedListener(OnCameraSizeChangedListener listener) {
        this.mOnCameraSizeChangedListener = listener;
    }

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

        mOffScreenCameraRenderer = new OffScreenCameraRenderer(context, this);
        mOffScreenCameraRenderer.setSharedTextureChangedListener(this);

        mNormalScreenRenderer = new NormalScreenRenderer(context);
        mGrayScreenRenderer = new GrayScreenRenderer(context);
        mReverseScreenRenderer = new ReverseScreenRenderer(context);
        mDrawScreenRenderer = mNormalScreenRenderer;

        int textureId = mOffScreenCameraRenderer.getSharedTextureId();
        mNormalScreenRenderer.setExternalTextureId(textureId);
        mGrayScreenRenderer.setExternalTextureId(textureId);
        mReverseScreenRenderer.setExternalTextureId(textureId);
    }

    public void setPictureRenderType(PictureRenderType type) {
        switch (type) {
            case NORMAL:
                mDrawScreenRenderer = mNormalScreenRenderer;
                break;

            case GRAY:
                mDrawScreenRenderer = mGrayScreenRenderer;
                break;

            case COLOR_REVERSE:
                mDrawScreenRenderer = mReverseScreenRenderer;
                break;
        }
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
        mNormalScreenRenderer.setExternalTextureId(textureID);
        mGrayScreenRenderer.setExternalTextureId(textureID);
        mReverseScreenRenderer.setExternalTextureId(textureID);
    }

    @Override
    protected WeRenderer getRenderer() {
        return this;
    }

    @Override
    public void onEGLContextCreated() {
        LogUtils.d(TAG, "onEGLContextCreated");
        mOffScreenCameraRenderer.onEGLContextCreated();
        mNormalScreenRenderer.onEGLContextCreated();
        mGrayScreenRenderer.onEGLContextCreated();
        mReverseScreenRenderer.onEGLContextCreated();
    }

    @Override
    public void onSurfaceTextureCreated(SurfaceTexture surfaceTexture) {
        LogUtils.d(TAG, "onEGLContextCreated");
        mCamera = new WeCamera(surfaceTexture, getContext());
        int[] size = mCamera.startPreview(mCameraId, mCameraViewWidth, mCameraViewHeight);
        if (size != null) {
            mOffScreenCameraRenderer.initCameraParams(mCameraId, size[0], size[1]);
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
                mOffScreenCameraRenderer.onCameraChanged(mCameraId, size[0], size[1]);
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
        mOffScreenCameraRenderer.onOrientationChanged();
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
        mOffScreenCameraRenderer.onSurfaceChanged(width, height);
        mNormalScreenRenderer.onSurfaceChanged(width, height);
        mGrayScreenRenderer.onSurfaceChanged(width, height);
        mReverseScreenRenderer.onSurfaceChanged(width, height);

        int[] size = mCamera.fitSurfaceSize(width, height);
        if (size != null) {
            mOffScreenCameraRenderer.onCameraChanged(mCameraId, size[0], size[1]);
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
        mOffScreenCameraRenderer.onDrawFrame();
        mDrawScreenRenderer.onDrawFrame();
        if (isTakingPhoto) {
            isTakingPhoto = false;
            mDrawScreenRenderer.takePhoto(getPhotoPathName());
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
        mOffScreenCameraRenderer.onEGLContextToDestroy();
        mNormalScreenRenderer.onEGLContextToDestroy();
        mGrayScreenRenderer.onEGLContextToDestroy();
        mReverseScreenRenderer.onEGLContextToDestroy();
    }

}
