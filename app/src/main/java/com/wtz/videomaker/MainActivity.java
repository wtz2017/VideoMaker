package com.wtz.videomaker;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.wtz.libvideomaker.utils.LogUtils;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "app.MainActivity";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LogUtils.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btn_multi_surface).setOnClickListener(this);
        findViewById(R.id.btn_single_surface).setOnClickListener(this);
        findViewById(R.id.btn_camera).setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_multi_surface:
                startActivity(new Intent(MainActivity.this, MultiSurfaceActivity.class));
                break;
            case R.id.btn_single_surface:
                startActivity(new Intent(MainActivity.this, SingleSurfaceActivity.class));
                break;
            case R.id.btn_camera:
                startActivity(new Intent(MainActivity.this, CameraActivity.class));
                break;
        }
    }

    @Override
    protected void onDestroy() {
        LogUtils.d(TAG, "onDestroy");
        super.onDestroy();
    }

}
