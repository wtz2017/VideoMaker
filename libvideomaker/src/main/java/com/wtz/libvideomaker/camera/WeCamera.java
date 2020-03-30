package com.wtz.libvideomaker.camera;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;

import java.util.List;

// TODO 使用 camera2
public class WeCamera {

    private Camera mCamera;// 要使用 android.hardware 包下的
    private SurfaceTexture mSurfaceTexture;

    private static final int DEFAULT_SUFACE_WIDTH = 1280;
    private static final int DEFAULT_SUFACE_HEIGHT = 720;
    private int[] mPreviewSize = new int[2];

    public WeCamera(SurfaceTexture surfaceTexture) {
        this.mSurfaceTexture = surfaceTexture;
    }

    public int[] startPreview(int cameraId, int surfaceWidth, int surfaceHeight) {
        try {
            mCamera = Camera.open(cameraId);
            mCamera.setPreviewTexture(mSurfaceTexture);

            Camera.Parameters parameters = mCamera.getParameters();
            // 是否开启闪光灯，直播不需要开启
            parameters.setFlashMode("off");
            // 使用YUV格式NV21
            parameters.setPreviewFormat(ImageFormat.NV21);
            setPicAndPreviewSize(parameters, surfaceWidth, surfaceHeight);
            mCamera.setParameters(parameters);

            mCamera.startPreview();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        return mPreviewSize;
    }

    public int[] fitSurfaceSize(int surfaceWidth, int surfaceHeight) {
        try {
            mCamera.stopPreview();// 测试发现小米4不用先停止预览，但华为某款手机就需要重新预览才有效

            Camera.Parameters parameters = mCamera.getParameters();
            setPicAndPreviewSize(parameters, surfaceWidth, surfaceHeight);
            mCamera.setParameters(parameters);

            mCamera.startPreview();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mPreviewSize;
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

    public void stopPreview() {
        if (mCamera != null) {
            try {
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
