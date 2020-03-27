package com.wtz.libvideomaker.camera;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;

public class WeCamera {

    private Camera mCamera;// 要使用 android.hardware 包下的
    private SurfaceTexture mSurfaceTexture;

    public WeCamera(SurfaceTexture surfaceTexture) {
        this.mSurfaceTexture = surfaceTexture;
    }

    public boolean startPreview(int cameraId) {
        try {
            mCamera = Camera.open(cameraId);
            mCamera.setPreviewTexture(mSurfaceTexture);
            Camera.Parameters parameters = mCamera.getParameters();
            // 是否开启闪光灯，直播不需要开启
            parameters.setFlashMode("off");
            // 使用YUV格式NV21
            parameters.setPreviewFormat(ImageFormat.NV21);
            // 设置最终生成的图片大小
            Camera.Size pictureSize = parameters.getSupportedPictureSizes().get(0);
            parameters.setPictureSize(pictureSize.width, pictureSize.height
            );
            // 设置预览大小
            Camera.Size previewSize = parameters.getSupportedPreviewSizes().get(0);
            parameters.setPreviewSize(previewSize.width, previewSize.height);
            mCamera.setParameters(parameters);

            mCamera.startPreview();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
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

    public void changeCamera(int cameraId) {
        stopPreview();
        startPreview(cameraId);
    }

}
