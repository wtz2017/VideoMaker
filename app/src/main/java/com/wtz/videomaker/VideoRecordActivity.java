package com.wtz.videomaker;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.wtz.libvideomaker.camera.WeCameraView;
import com.wtz.libvideomaker.recorder.WeVideoRecorder;
import com.wtz.libvideomaker.renderer.OnScreenRenderer;
import com.wtz.libvideomaker.renderer.filters.WatermarkRenderer;
import com.wtz.libvideomaker.utils.LogUtils;
import com.wtz.libvideomaker.utils.ScreenUtils;
import com.wtz.videomaker.utils.DateTimeUtil;
import com.wtz.videomaker.utils.PermissionChecker;
import com.wtz.videomaker.utils.PermissionHandler;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;


public class VideoRecordActivity extends AppCompatActivity implements PermissionHandler.PermissionHandleListener,
        WeCameraView.OnCameraSizeChangedListener, View.OnClickListener, RadioGroup.OnCheckedChangeListener,
        OnScreenRenderer.ScreenTextureChangeListener {
    private static final String TAG = VideoRecordActivity.class.getSimpleName();

    private PermissionHandler mPermissionHandler;

    private Display mDisplay;

    private View mContentView;
    private int mContentHeight;
    private boolean needHideNav;

    private WeCameraView mWeCameraView;
    private WeVideoRecorder mWeVideoRecorder;
    private boolean isRecording;

    private Button mChangeCameraButton;
    private Button mRecordButton;

    private View mIndicatorLayout;
    private View mIndicatorLight;
    private TextView mIndicatorTime;

    private String mSaveVideoDir;

    private boolean isBackCamera = true;

    private int mTextMarkCorner = WatermarkRenderer.CORNER_RIGHT_TOP;
    private static final int TEXT_MARK_SIZE_RESID = R.dimen.sp_12;
    private static final int TEXT_MARK_PADDING_X = R.dimen.dp_10;
    private static final int TEXT_MARK_PADDING_Y = R.dimen.dp_6;
    private static final int TEXT_MARK_MARIN = R.dimen.dp_5;

    private static final int UPDATE_RECORD_INFO_INTERVAL = 500;
    private Handler mUIHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LogUtils.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScreenUtils.hideNavigationBar(VideoRecordActivity.this); // 隐藏导航栏
        needHideNav = false;

        setContentView(R.layout.activity_video_record);

        mContentView = findViewById(android.R.id.content);
        //监听 content 视图树的变化
        mContentView.getViewTreeObserver().addOnGlobalLayoutListener(mOnGlobalLayoutListener);

        mWeCameraView = findViewById(R.id.we_camera);
        mWeCameraView.setClearScreenOnDraw(false);// 缓解某些低端机型录制视频时闪屏问题
        mWeCameraView.setOnCameraSizeChangedListener(this);
        mWeCameraView.setScreenTextureChangeListener(this);
        String date = new SimpleDateFormat("yyyy/MM/dd").format(new Date());
        int textColor = Color.parseColor("#FFFF00");
        int textBgColor = Color.parseColor("#33DEDEDE");
        int textSize = (int) (getResources().getDimension(TEXT_MARK_SIZE_RESID) + 0.5f);
        int textPaddingX = (int) (getResources().getDimension(TEXT_MARK_PADDING_X) + 0.5f);
        int textPaddingY = (int) (getResources().getDimension(TEXT_MARK_PADDING_Y) + 0.5f);
        int textMargin = (int) (getResources().getDimension(TEXT_MARK_MARIN) + 0.5f);
        mWeCameraView.setTextMark("WeCamera " + date, textSize, textPaddingX,
                textPaddingX, textPaddingY, textPaddingY, textColor, textBgColor,
                mTextMarkCorner, textMargin, textMargin);

        File savePath = new File(Environment.getExternalStorageDirectory(), "WePhotos");
        mSaveVideoDir = savePath.getAbsolutePath();
        mWeVideoRecorder = new WeVideoRecorder(this);
        mWeVideoRecorder.setSaveVideoDir(mSaveVideoDir);

        mChangeCameraButton = findViewById(R.id.btn_change_camera);
        mChangeCameraButton.setOnClickListener(this);
        mRecordButton = findViewById(R.id.btn_record);
        mRecordButton.setOnClickListener(this);

        mIndicatorLayout = findViewById(R.id.ll_indicator_layout);
        mIndicatorLight = findViewById(R.id.v_record_indicator_light);
        mIndicatorTime = findViewById(R.id.tv_record_time);

        ((RadioGroup) findViewById(R.id.rg_render_type)).setOnCheckedChangeListener(this);

        mPermissionHandler = new PermissionHandler(this, this);
        mPermissionHandler.handleCommonPermission(Manifest.permission.CAMERA);
        mPermissionHandler.handleCommonPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    private ViewTreeObserver.OnGlobalLayoutListener mOnGlobalLayoutListener = new
            ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (mContentView.getHeight() != mContentHeight) {
                        needHideNav = true;
                        mContentHeight = mContentView.getHeight();
                    }
                }
            };

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
        LogUtils.w(TAG, "onPermissionResult " + permission + " state is " + state);
        if (Manifest.permission.CAMERA.equals(permission)) {
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
        } else if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission)) {
            if (state == PermissionChecker.PermissionState.USER_NOT_GRANTED) {
                LogUtils.e(TAG, "onPermissionResult " + permission + " state is USER_NOT_GRANTED!");
                finish();
            }
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
        // 熄屏再恢复后，导航栏会出来
        if (needHideNav) {
            ScreenUtils.hideNavigationBar(this);
            needHideNav = false;
        }
        mWeCameraView.onActivityResume();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        LogUtils.d(TAG, "onWindowFocusChanged hasFocus=" + hasFocus);
        if (hasFocus) {
            if (needHideNav) {
                ScreenUtils.hideNavigationBar(this);
                needHideNav = false;
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (MotionEvent.ACTION_DOWN == event.getAction()) {
            if (needHideNav) {
                ScreenUtils.hideNavigationBar(this);
                needHideNav = false;
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_change_camera:
                changeCamera();
                break;

            case R.id.btn_record:
                record();
                break;

        }
    }

    private void changeCamera() {
        if (isBackCamera) {
            isBackCamera = false;
            mWeCameraView.startFrontCamera();
        } else {
            isBackCamera = true;
            mWeCameraView.startBackCamera();
        }
    }

    private void record() {
        if (isRecording) {
            stopEncode();
        } else {
            startEncode();
        }
    }

    private void startEncode() {
        isRecording = true;
        fixCurrentDirection();

        mWeVideoRecorder.setExternalTextureId(mWeCameraView.getScreenTextureId());
        mWeVideoRecorder.startEncode(mWeCameraView.getSharedEGLContext(),
                mWeCameraView.getWidth(), mWeCameraView.getHeight());

        mRecordButton.setText(R.string.stop_record);
        mIndicatorLayout.setVisibility(View.VISIBLE);
        mUIHandler.post(mUpdateRecordInfoRunnable);
    }

    private void stopEncode() {
        isRecording = false;
        mWeVideoRecorder.stopEncode();

        mIndicatorLayout.setVisibility(View.GONE);
        mRecordButton.setText(R.string.start_record);

        resumeUserDirection();
    }

    private void fixCurrentDirection() {
        int currentAngle = getRotationAngle();
        switch (currentAngle) {
            case Surface.ROTATION_90:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                break;

            case Surface.ROTATION_270:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
                break;

            case Surface.ROTATION_0:
            default:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                break;
        }
    }

    private void resumeUserDirection() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
    }

    private int getRotationAngle() {
        if (mDisplay == null) {
            mDisplay = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        }
        return mDisplay.getRotation();
    }

    private Runnable mUpdateRecordInfoRunnable = new Runnable() {
        @Override
        public void run() {
            mUIHandler.removeCallbacks(this);
            if (mWeVideoRecorder != null) {
                String time = DateTimeUtil.changeRemainTimeToHms(mWeVideoRecorder.getEncodeTimeMills());
                mIndicatorTime.setText(time);
                if (mIndicatorLight.getVisibility() == View.INVISIBLE) {
                    mIndicatorLight.setVisibility(View.VISIBLE);
                } else {
                    mIndicatorLight.setVisibility(View.INVISIBLE);
                }
            }
            mUIHandler.postDelayed(this, UPDATE_RECORD_INFO_INTERVAL);
        }
    };

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        LogUtils.d(TAG, "onCheckedChanged " + checkedId);
        switch (checkedId) {
            case R.id.rb_normal:
                mWeCameraView.setPictureRenderType(WeCameraView.PictureRenderType.NORMAL);
                break;

            case R.id.rb_gray:
                mWeCameraView.setPictureRenderType(WeCameraView.PictureRenderType.GRAY);
                break;

            case R.id.rb_color_reverse:
                mWeCameraView.setPictureRenderType(WeCameraView.PictureRenderType.COLOR_REVERSE);
                break;
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mWeCameraView.onActivityConfigurationChanged(newConfig);
    }

    @Override
    public void onCameraSizeChanged(int surfaceWidth, int surfaceHeight, int previewWidth, int previewHeight) {
        if (mWeVideoRecorder != null) {
            mWeVideoRecorder.onVideoSizeChanged(surfaceWidth, surfaceHeight);
        }
    }

    @Override
    public void onScreenTextureChanged(int textureId) {
        LogUtils.d(TAG, "onScreenTextureChanged:" + textureId);
        if (mWeVideoRecorder != null) {
            mWeVideoRecorder.setExternalTextureId(textureId);
        }
    }

    @Override
    protected void onPause() {
        LogUtils.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onStop() {
        LogUtils.d(TAG, "onStop");
        stopEncode();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        LogUtils.d(TAG, "onDestroy");
        mContentView.getViewTreeObserver().removeOnGlobalLayoutListener(mOnGlobalLayoutListener);
        mUIHandler.removeCallbacksAndMessages(null);

        mWeVideoRecorder.release();
        mWeVideoRecorder = null;
        mWeCameraView.release();
        mWeCameraView = null;

        if (mPermissionHandler != null) {
            mPermissionHandler.destroy();
            mPermissionHandler = null;
        }
        super.onDestroy();
    }

}
