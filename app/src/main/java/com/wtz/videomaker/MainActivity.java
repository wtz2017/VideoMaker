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
        findViewById(R.id.btn_audio_record).setOnClickListener(this);
        findViewById(R.id.btn_camera).setOnClickListener(this);
        findViewById(R.id.btn_video_record).setOnClickListener(this);
        findViewById(R.id.btn_image_video).setOnClickListener(this);
        findViewById(R.id.btn_mix_audio).setOnClickListener(this);
        findViewById(R.id.btn_push_video).setOnClickListener(this);
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
            case R.id.btn_audio_record:
                startActivity(new Intent(MainActivity.this, AudioRecordActivity.class));
                break;
            case R.id.btn_mix_audio:
                startActivity(new Intent(MainActivity.this, MixAudioActivity.class));
                break;
            case R.id.btn_camera:
                startActivity(new Intent(MainActivity.this, CameraActivity.class));
                break;
            case R.id.btn_video_record:
                startActivity(new Intent(MainActivity.this, VideoRecordActivity.class));
                break;
            case R.id.btn_image_video:
                startActivity(new Intent(MainActivity.this, ImageVideoActivity.class));
                break;
            case R.id.btn_push_video:
                startActivity(new Intent(MainActivity.this, VideoPushActivity.class));
                break;
        }
    }

    @Override
    protected void onDestroy() {
        LogUtils.d(TAG, "onDestroy");
        super.onDestroy();
    }

}
