package com.wtz.libvideomaker.camera;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;

import com.wtz.libvideomaker.utils.LogUtils;

import java.util.List;

// TODO 使用 camera2
public class WeCamera implements AcceleFocusListener.OnFocusListener {
    private static final String TAG = WeCamera.class.getSimpleName();

    private int mCameraId;
    private Camera mCamera;// 要使用 android.hardware 包下的
    private SurfaceTexture mSurfaceTexture;
    private AcceleFocusListener mAccelerationListener;

    private static final int DEFAULT_SUFACE_WIDTH = 1280;
    private static final int DEFAULT_SUFACE_HEIGHT = 720;
    private int[] mPreviewSize = new int[2];

    public WeCamera(SurfaceTexture surfaceTexture, Context context) {
        this.mSurfaceTexture = surfaceTexture;
        mAccelerationListener = AcceleFocusListener.getInstance(context);
        mAccelerationListener.setCameraFocusListener(this);
    }

    public int[] startPreview(int cameraId, int surfaceWidth, int surfaceHeight) {
        try {
            mCameraId = cameraId;
            mCamera = Camera.open(cameraId);
            mCamera.setPreviewTexture(mSurfaceTexture);

            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setFlashMode("off");// 是否开启闪光灯，直播不需要开启
            parameters.setPreviewFormat(ImageFormat.NV21);// 使用YUV格式NV21
            setPreviewFPS(parameters);
            setPicAndPreviewSize(parameters, surfaceWidth, surfaceHeight);
            mCamera.setParameters(parameters);

            mCamera.startPreview();

            startAccelerationFocus();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        return mPreviewSize;
    }

    public int[] fitSurfaceSize(int surfaceWidth, int surfaceHeight) {
        stopAccelerationFocus();
        try {
            mCamera.stopPreview();// 测试发现小米4不用先停止预览，但华为某款手机就需要重新预览才有效

            Camera.Parameters parameters = mCamera.getParameters();
            setPicAndPreviewSize(parameters, surfaceWidth, surfaceHeight);
            mCamera.setParameters(parameters);

            mCamera.startPreview();

            startAccelerationFocus();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mPreviewSize;
    }

    private void setPreviewFPS(Camera.Parameters parameters) {
        List<int[]> fpsRanges = parameters.getSupportedPreviewFpsRange();
        int maxIndex = 0;
        int maxFps = 0;
        int[] fpsArray;
        int size = fpsRanges.size();
        for (int i = 0; i < size; i++) {
            fpsArray = fpsRanges.get(i);
            if (fpsArray[0] > maxFps) {
                maxFps = fpsArray[0];
                maxIndex = i;
            }
        }
        fpsArray = fpsRanges.get(maxIndex);
        parameters.setPreviewFpsRange(fpsArray[0], fpsArray[1]);
        LogUtils.w(TAG, "setPreviewFPS:" + (fpsArray[0] / 1000.0f) + "~" + (fpsArray[1] / 1000.0f));
    }

    private void setPicAndPreviewSize(Camera.Parameters parameters, int surfaceWidth, int surfaceHeight) {
        if (surfaceWidth <= 0) {
            surfaceWidth = DEFAULT_SUFACE_WIDTH;
        }
        if (surfaceHeight <= 0) {
            surfaceHeight = DEFAULT_SUFACE_HEIGHT;
        }
        // 设置最终生成的图片大小
        Camera.Size size = getFitSize(surfaceWidth, surfaceHeight, parameters.getSupportedPictureSizes());
        parameters.setPictureSize(size.width, size.height);

        // 设置预览大小
        size = getFitSize(surfaceWidth, surfaceHeight, parameters.getSupportedPreviewSizes());
        parameters.setPreviewSize(size.width, size.height);
        mPreviewSize[0] = size.width;
        mPreviewSize[1] = size.height;
        LogUtils.w(TAG, "setPreviewSize:" + mPreviewSize[0] + "x" + mPreviewSize[1]);
    }

    /**
     * 优先判断宽高比例最接近的，其次判断实际宽度最接近的
     */
    private Camera.Size getFitSize(int surfaceWidth, int surfaceHeight, List<Camera.Size> sizes) {
        if (surfaceWidth < surfaceHeight) {
            int temp = surfaceWidth;
            surfaceWidth = surfaceHeight;
            surfaceHeight = temp;
        }

        float surfaceRatio = 1.0f * surfaceWidth / surfaceHeight;
        Camera.Size fitSize = sizes.get(0);
        float minRatioDiff = Math.abs(1.0f * fitSize.width / fitSize.height - surfaceRatio);
        float minWidthDiff = Math.abs(1.0f * fitSize.width - surfaceWidth);

        float newRatioDiff;
        float newWidthDiff;
        Camera.Size newSize;
        int count = sizes.size();
        for (int i = 1; i < count; i++) {
            newSize = sizes.get(i);
            newRatioDiff = Math.abs(1.0f * newSize.width / newSize.height - surfaceRatio);
            if (newRatioDiff < minRatioDiff) {
                minRatioDiff = newRatioDiff;
                minWidthDiff = Math.abs(1.0f * newSize.width - surfaceWidth);
                fitSize = newSize;
            } else if (newRatioDiff == minRatioDiff) {
                newWidthDiff = Math.abs(1.0f * newSize.width - surfaceWidth);
                if (newWidthDiff < minWidthDiff) {
                    minWidthDiff = newWidthDiff;
                    fitSize = newSize;
                }
            }
        }

        return fitSize;
    }

    private void startAccelerationFocus() {
        // 只针对后置摄像头设置自动对焦模式，前置摄像头是 fixed，无法自动对焦
        if (mCameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
            mAccelerationListener.start();
            onFocus();
        }
    }

    @Override
    public void onFocus() {
        if (mCamera != null && !mAccelerationListener.isFocusLocked()) {
            mAccelerationListener.lockFocus();
            if (!startFocus()) {
                LogUtils.e(TAG, "startFocus failed");
                mAccelerationListener.unlockFocus();
            }
        }
    }

    private boolean startFocus() {
        try {
            mCamera.cancelAutoFocus(); // 先要取消掉进程中所有的聚焦功能
            Camera.Parameters parameters = mCamera.getParameters();
            // setMeteringRect(x, y);// 设置感光区域
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            mCamera.setParameters(parameters);
            mCamera.autoFocus(mAutoFocusCallback);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private Camera.AutoFocusCallback mAutoFocusCallback = new Camera.AutoFocusCallback() {
        @Override
        public void onAutoFocus(boolean success, Camera camera) {
            if (!success) {
                LogUtils.w(TAG, "onAutoFocus failed!");
            }
            mAccelerationListener.unlockFocus();
        }
    };

    private void stopAccelerationFocus() {
        mAccelerationListener.stop();
    }

    public void stopPreview() {
        stopAccelerationFocus();
        if (mCamera != null) {
            try {
                mCamera.cancelAutoFocus(); // 取消掉进程中所有的聚焦功能
                mCamera.stopPreview();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mCamera.release();
            mCamera = null;
        }
    }

    public int[] changeCamera(int cameraId, int surfaceWidth, int surfaceHeight) {
        stopPreview();
        return startPreview(cameraId, surfaceWidth, surfaceHeight);
    }

    public int[] getPreviewSize() {
        return mPreviewSize;
    }

}
