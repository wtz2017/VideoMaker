package com.wtz.videomaker;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.wtz.libvideomaker.camera.WeCameraView;
import com.wtz.libvideomaker.utils.LogUtils;
import com.wtz.videomaker.utils.PermissionChecker;
import com.wtz.videomaker.utils.PermissionHandler;


public class CameraActivity extends AppCompatActivity implements PermissionHandler.PermissionHandleListener {
    private static final String TAG = CameraActivity.class.getSimpleName();

    private PermissionHandler mPermissionHandler;

    private WeCameraView mWeCameraView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LogUtils.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        mWeCameraView = findViewById(R.id.we_camera);

        mPermissionHandler = new PermissionHandler(this, this);
        mPermissionHandler.handleCommonPermission(Manifest.permission.CAMERA);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        LogUtils.d(TAG, "onRequestPermissionsResult requestCode=" + requestCode);
        mPermissionHandler.handleActivityRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        LogUtils.d(TAG, "onActivityResult requestCode=" + requestCode + ", resultCode=" + resultCode
                + ", data=" + data);
        mPermissionHandler.handleActivityResult(requestCode);
    }

    @Override
    public void onPermissionResult(String permission, PermissionChecker.PermissionState state) {
        if (state == PermissionChecker.PermissionState.ALLOWED) {
            mWeCameraView.startBackCamera();
        } else if (state == PermissionChecker.PermissionState.UNKNOWN) {
            LogUtils.e(TAG, "onPermissionResult " + permission + " state is UNKNOWN!");
            mWeCameraView.startBackCamera();
        } else if (state == PermissionChecker.PermissionState.USER_NOT_GRANTED) {
            LogUtils.e(TAG, "onPermissionResult " + permission + " state is USER_NOT_GRANTED!");
            finish();
        } else {
            LogUtils.w(TAG, "onPermissionResult " + permission + " state is " + state);
        }
    }

    @Override
    protected void onStart() {
        LogUtils.d(TAG, "onStart");
        super.onStart();
    }

    @Override
    protected void onResume() {
        LogUtils.d(TAG, "onResume");
        super.onResume();
        mWeCameraView.onActivityResume();
    }

    @Override
    protected void onPause() {
        LogUtils.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onStop() {
        LogUtils.d(TAG, "onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        LogUtils.d(TAG, "onDestroy");
        if (mPermissionHandler != null) {
            mPermissionHandler.destroy();
            mPermissionHandler = null;
        }
        super.onDestroy();
    }

}
