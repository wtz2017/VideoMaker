package com.wtz.libvideomaker.camera;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.AttributeSet;

import com.wtz.libvideomaker.egl.WeGLSurfaceView;
import com.wtz.libvideomaker.renderer.NormalScreenRenderer;
import com.wtz.libvideomaker.renderer.OffScreenCameraRenderer;
import com.wtz.libvideomaker.renderer.OnScreenRenderer;
import com.wtz.libvideomaker.utils.LogUtils;

public class WeCameraView extends WeGLSurfaceView implements WeGLSurfaceView.WeRenderer, OffScreenCameraRenderer.OnSharedTextureChangedListener, OffScreenCameraRenderer.SurfaceTextureListener {
    private static final String TAG = WeCameraView.class.getSimpleName();

    private WeCamera mCamera;
    private int mCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;

    private OffScreenCameraRenderer mOffScreenCameraRenderer;
    private OnScreenRenderer mOnScreenRenderer;

    public WeCameraView(Context context) {
        this(context, null);
    }

    public WeCameraView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WeCameraView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setRenderMode(RENDERMODE_WHEN_DIRTY);

        mOffScreenCameraRenderer = new OffScreenCameraRenderer(context, this);
        mOffScreenCameraRenderer.setSharedTextureChangedListener(this);

        mOnScreenRenderer = new NormalScreenRenderer(context);
        mOnScreenRenderer.setExternalTextureId(mOffScreenCameraRenderer.getSharedTextureId());
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
        LogUtils.d(TAG, "onEGLContextCreated");
        mOffScreenCameraRenderer.onEGLContextCreated();
        mOnScreenRenderer.onEGLContextCreated();
    }

    @Override
    public void onSurfaceTextureCreated(SurfaceTexture surfaceTexture) {
        LogUtils.d(TAG, "onEGLContextCreated");
        mCamera = new WeCamera(surfaceTexture);
        mCamera.startPreview(mCameraId);
    }

    public void startBackCamera() {
        mCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
        changeCamera(mCameraId);
    }

    public void startFrontCamera() {
        mCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
        changeCamera(mCameraId);
    }

    private void changeCamera(int cameraId) {
        this.mCameraId = cameraId;
        if (mCamera != null) {
            mCamera.changeCamera(cameraId);
        }
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        LogUtils.d(TAG, "onSurfaceChanged " + width + "x" + height);
        mOffScreenCameraRenderer.onSurfaceChanged(width, height);
        mOnScreenRenderer.onSurfaceChanged(width, height);
    }

    @Override
    public void onFrameAvailable() {
        requestRender();
    }

    @Override
    public void onDrawFrame() {
        mOffScreenCameraRenderer.onDrawFrame();
        mOnScreenRenderer.onDrawFrame();
    }

    @Override
    public void onEGLContextToDestroy() {
        LogUtils.d(TAG, "onEGLContextToDestroy");
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera = null;
        }
        mOffScreenCameraRenderer.onEGLContextToDestroy();
        mOnScreenRenderer.onEGLContextToDestroy();
    }

}
