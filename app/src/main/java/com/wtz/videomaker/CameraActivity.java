package com.wtz.videomaker;

import android.Manifest;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.wtz.libvideomaker.camera.WeCameraView;
import com.wtz.libvideomaker.renderer.filters.WatermarkRenderer;
import com.wtz.libvideomaker.utils.LogUtils;
import com.wtz.libvideomaker.utils.ScreenUtils;
import com.wtz.videomaker.utils.PermissionChecker;
import com.wtz.videomaker.utils.PermissionHandler;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;


public class CameraActivity extends AppCompatActivity implements PermissionHandler.PermissionHandleListener,
        WeCameraView.OnCameraSizeChangedListener, View.OnClickListener, RadioGroup.OnCheckedChangeListener {
    private static final String TAG = CameraActivity.class.getSimpleName();

    private PermissionHandler mPermissionHandler;

    private View mContentView;
    private int mContentHeight;
    private boolean needHideNav;

    private WeCameraView mWeCameraView;

    private TextView mTitleView;
    private TextView mCameraSizeView;
    private Button mChangeCameraButton;
    private Button mSaveImageButton;

    private boolean isBackCamera = true;

    private int mImgMarkCorner = WatermarkRenderer.CORNER_LEFT_BOTTOM;
    private static final int IMG_MARK_SIZE_RESID = R.dimen.dp_60;
    private int mImgMarkMarginX = 0;
    private int mImgMarkMarginY = 0;
    private boolean addImgMarkMarginX = true;
    private boolean addImgMarkMarginY = true;

    private int mTextMarkCorner = WatermarkRenderer.CORNER_RIGHT_TOP;
    private static final int TEXT_MARK_SIZE_RESID = R.dimen.sp_12;
    private static final int TEXT_MARK_PADDING_X = R.dimen.dp_10;
    private static final int TEXT_MARK_PADDING_Y = R.dimen.dp_6;
    private static final int TEXT_MARK_MARIN = R.dimen.dp_15;

    private Handler mUIHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LogUtils.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScreenUtils.hideNavigationBar(CameraActivity.this); // 隐藏导航栏
        needHideNav = false;

        setContentView(R.layout.activity_camera);

        mContentView = findViewById(android.R.id.content);
        //监听 content 视图树的变化
        mContentView.getViewTreeObserver().addOnGlobalLayoutListener(mOnGlobalLayoutListener);

        mWeCameraView = findViewById(R.id.we_camera);
        mWeCameraView.setOnCameraSizeChangedListener(this);
        File savePath = new File(Environment.getExternalStorageDirectory(), "WePhotos");
        mWeCameraView.setSaveImageDir(savePath.getAbsolutePath());

        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.purple_ball);
        int imgSize = (int) (getResources().getDimension(IMG_MARK_SIZE_RESID) + 0.5f);
        mWeCameraView.setImageMark(bitmap, imgSize, imgSize,
                mImgMarkCorner, mImgMarkMarginX, mImgMarkMarginY);

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

        mTitleView = findViewById(R.id.tv_title);
        mCameraSizeView = findViewById(R.id.tv_preview_size);

        mChangeCameraButton = findViewById(R.id.btn_change_camera);
        mChangeCameraButton.setOnClickListener(this);
        mSaveImageButton = findViewById(R.id.btn_save_image);
        mSaveImageButton.setOnClickListener(this);

        ((RadioGroup) findViewById(R.id.rg_render_type)).setOnCheckedChangeListener(this);

        mUIHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mTitleView.setVisibility(View.GONE);
            }
        }, 5000);
        mUIHandler.post(mChangeImgMarkRunnable);

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
                    LogUtils.d(TAG, "ViewTreeObserver onGlobalLayout needHideNav=" + needHideNav);
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
        } else if (Manifest.permission.CAMERA.equals(permission)) {
            // do something
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

    private Runnable mChangeImgMarkRunnable = new Runnable() {
        @Override
        public void run() {
            mUIHandler.removeCallbacks(this);
            if (addImgMarkMarginX) {
                mImgMarkMarginX++;
                if (mImgMarkMarginX > 310) {
                    mImgMarkMarginX = 310;
                    addImgMarkMarginX = false;
                }
            } else {
                mImgMarkMarginX--;
                if (mImgMarkMarginX < 0) {
                    mImgMarkMarginX = 0;
                    addImgMarkMarginX = true;
                }
            }
            if (addImgMarkMarginY) {
                mImgMarkMarginY++;
                if (mImgMarkMarginY > 620) {
                    mImgMarkMarginY = 620;
                    addImgMarkMarginY = false;
                }
            } else {
                mImgMarkMarginY--;
                if (mImgMarkMarginY < 0) {
                    mImgMarkMarginY = 0;
                    addImgMarkMarginY = true;
                }
            }
            mWeCameraView.changeImageMarkPosition(mImgMarkCorner, mImgMarkMarginX, mImgMarkMarginY);
            mUIHandler.postDelayed(this, 10);
        }
    };

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_change_camera:
                changeCamera();
                break;

            case R.id.btn_save_image:
                saveImage();
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

    private void saveImage() {
        if (mWeCameraView != null) {
            mWeCameraView.takePhoto();
        }
    }

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
        mContentView.getViewTreeObserver().removeOnGlobalLayoutListener(mOnGlobalLayoutListener);
        mWeCameraView.release();
        mUIHandler.removeCallbacksAndMessages(null);
        if (mPermissionHandler != null) {
            mPermissionHandler.destroy();
            mPermissionHandler = null;
        }
        super.onDestroy();
    }

}
