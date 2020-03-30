package com.wtz.videomaker;

import android.Manifest;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.wtz.libvideomaker.camera.WeCameraView;
import com.wtz.libvideomaker.utils.LogUtils;
import com.wtz.videomaker.utils.PermissionChecker;
import com.wtz.videomaker.utils.PermissionHandler;


public class CameraActivity extends AppCompatActivity implements PermissionHandler.PermissionHandleListener,
        WeCameraView.OnCameraSizeChangedListener, View.OnClickListener {
    private static final String TAG = CameraActivity.class.getSimpleName();

    private PermissionHandler mPermissionHandler;

    private WeCameraView mWeCameraView;

    private TextView mTitleView;
    private TextView mCameraSizeView;
    private Button mChangeCameraButton;

    private boolean isBackCamera = true;

    private Handler mUIHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LogUtils.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        mWeCameraView = findViewById(R.id.we_camera);
        mWeCameraView.setOnCameraSizeChangedListener(this);

        mTitleView = findViewById(R.id.tv_title);
        mCameraSizeView = findViewById(R.id.tv_preview_size);
        mChangeCameraButton = findViewById(R.id.btn_change_camera);
        mChangeCameraButton.setOnClickListener(this);

        mUIHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mTitleView.setVisibility(View.GONE);
            }
        }, 5000);

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
            isBackCamera = true;
        } else if (state == PermissionChecker.PermissionState.UNKNOWN) {
            LogUtils.e(TAG, "onPermissionResult " + permission + " state is UNKNOWN!");
            mWeCameraView.startBackCamera();
            isBackCamera = true;
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
    public void onClick(View v) {
        if (isBackCamera) {
            isBackCamera = false;
            mWeCameraView.startFrontCamera();
        } else {
            isBackCamera = true;
            mWeCameraView.startBackCamera();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mWeCameraView.onActivityConfigurationChanged(newConfig);
    }

    @Override
    public void onCameraSizeChanged(int surfaceWidth, int surfaceHeight, int previewWidth, int previewHeight) {
        final StringBuilder builder = new StringBuilder();
        builder.append("Surface size:");
        builder.append(surfaceWidth);
        builder.append("x");
        builder.append(surfaceHeight);
        builder.append("; ratio:");
        builder.append(surfaceWidth * 1.0f / surfaceHeight);
        builder.append("\n");

        builder.append("Preview size:");
        builder.append(previewWidth);
        builder.append("x");
        builder.append(previewHeight);
        builder.append("; ratio:");
        builder.append(previewWidth * 1.0f / previewHeight);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mCameraSizeView.setText(builder);
            }
        });
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
        mUIHandler.removeCallbacksAndMessages(null);
        if (mPermissionHandler != null) {
            mPermissionHandler.destroy();
            mPermissionHandler = null;
        }
        super.onDestroy();
    }

}
