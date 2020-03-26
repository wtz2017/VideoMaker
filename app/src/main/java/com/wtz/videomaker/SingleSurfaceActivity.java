package com.wtz.videomaker;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.wtz.libvideomaker.utils.LogUtils;


public class SingleSurfaceActivity extends AppCompatActivity {
    private static final String TAG = "SingleSurfaceActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LogUtils.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_single_surface);
    }

    @Override
    protected void onDestroy() {
        LogUtils.d(TAG, "onDestroy");
        super.onDestroy();
    }

}
