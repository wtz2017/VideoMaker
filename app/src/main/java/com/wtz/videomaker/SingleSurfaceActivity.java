package com.wtz.videomaker;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.wtz.libvideomaker.utils.LogUtils;
import com.wtz.videomaker.surfaceview.MultiImgSurfaceView;


public class SingleSurfaceActivity extends AppCompatActivity {
    private static final String TAG = "SingleSurfaceActivity";

    private MultiImgSurfaceView mMainSurfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LogUtils.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_single_surface);
        mMainSurfaceView = findViewById(R.id.main_surface_view);
    }

    @Override
    protected void onDestroy() {
        LogUtils.d(TAG, "onDestroy");
        mMainSurfaceView.clearSourceImage();
        mMainSurfaceView = null;
        super.onDestroy();
    }

}
