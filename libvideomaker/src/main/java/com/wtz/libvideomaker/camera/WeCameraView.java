package com.wtz.libvideomaker.camera;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;

import com.wtz.libvideomaker.egl.WeGLSurfaceView;
import com.wtz.libvideomaker.renderer.NormalScreenRenderer;
import com.wtz.libvideomaker.renderer.OffScreenCameraRenderer;
import com.wtz.libvideomaker.renderer.OnScreenRenderer;
import com.wtz.libvideomaker.utils.LogUtils;

public class WeCameraView extends WeGLSurfaceView implements WeGLSurfaceView.WeRenderer,
        OffScreenCameraRenderer.OnSharedTextureChangedListener,
        OffScreenCameraRenderer.SurfaceTextureListener, Handler.Callback {
    private static final String TAG = WeCameraView.class.getSimpleName();

    private WeCamera mCamera;
    private int mCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;

    private OffScreenCameraRenderer mOffScreenCameraRenderer;
    private OnScreenRenderer mOnScreenRenderer;

    private Handler mUIHandler;
    /**
     * 脏模式渲染前提下，旋转屏幕导致大概率出现不回调 onFrameAvailable，以至于不再渲染预览画面
     * 直接改成持续渲染模式可以解决此问题，但是性能不如脏模式渲染，于是取此两者折衷的方案：
     * 在旋转屏幕时连续请求渲染几秒，根据实际经验调节此值，这样能在不影响性能的情况下解决问题
     * 此消息用于在 onSurfaceChanged（旋转屏幕）时主动发起连续几秒的渲染请求
     */
    private static final int MSG_REQUEST_RENDER = 1;
    private static final int REQ_RENDER_INTERVAL_MILLS = 100;
    private static final int MAX_REQ_RENDER_DURATION_MILLS = 5000;
    private long mStartReqRenderTime;

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
        mUIHandler = new Handler(Looper.getMainLooper(), this);
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

    public void stopPreview() {
        if (mCamera != null) {
            mCamera.stopPreview();
        }
    }

    public void onActivityResume() {
        requestRender();
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_REQUEST_RENDER:
                mUIHandler.removeMessages(MSG_REQUEST_RENDER);
                if (System.currentTimeMillis() - mStartReqRenderTime <= MAX_REQ_RENDER_DURATION_MILLS) {
                    requestRender();
                    mUIHandler.sendEmptyMessageDelayed(MSG_REQUEST_RENDER, REQ_RENDER_INTERVAL_MILLS);
                }
                break;
        }
        return false;
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        LogUtils.d(TAG, "onSurfaceChanged " + width + "x" + height);
        mOffScreenCameraRenderer.onSurfaceChanged(width, height);
        mOnScreenRenderer.onSurfaceChanged(width, height);

        mStartReqRenderTime = System.currentTimeMillis();
        mUIHandler.removeMessages(MSG_REQUEST_RENDER);
        mUIHandler.sendEmptyMessageDelayed(MSG_REQUEST_RENDER, REQ_RENDER_INTERVAL_MILLS);
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
        mUIHandler.removeCallbacksAndMessages(null);
        mUIHandler = null;
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera = null;
        }
        mOffScreenCameraRenderer.onEGLContextToDestroy();
        mOnScreenRenderer.onEGLContextToDestroy();
    }

}
