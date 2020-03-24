package com.wtz.videomaker;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.wtz.libvideomaker.utils.LogUtils;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "app.MainActivity";

    private TextView mTitleView;
    private Handler mUIHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LogUtils.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTitleView = findViewById(R.id.tv_title);

        mUIHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mTitleView.setVisibility(View.GONE);
            }
        }, 6000);
    }

    @Override
    protected void onDestroy() {
        LogUtils.d(TAG, "onDestroy");
        mUIHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
