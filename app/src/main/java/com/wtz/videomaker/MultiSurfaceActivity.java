package com.wtz.videomaker;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.wtz.libvideomaker.renderer.origins.ImgRenderer;
import com.wtz.libvideomaker.utils.LogUtils;
import com.wtz.videomaker.surfaceview.GraySurfaceView;
import com.wtz.videomaker.surfaceview.LuminanceSurfaceView;
import com.wtz.videomaker.surfaceview.SingleImgSurfaceView;
import com.wtz.videomaker.surfaceview.ReverseSurfaceView;

import javax.microedition.khronos.egl.EGLContext;


public class MultiSurfaceActivity extends AppCompatActivity
        implements ImgRenderer.OnSharedTextureChangedListener,
        SingleImgSurfaceView.OnEGLContextCreatedListener, SingleImgSurfaceView.OnEGLContextToDestroyListener, ImgRenderer.OnNewImageDrawnListener {
    private static final String TAG = "MultiSurfaceActivity";

    private FrameLayout mFiltersLayout1;
    private LinearLayout mFiltersLayout2;

    private SingleImgSurfaceView mMainSurfaceView;

    private GraySurfaceView mGraySurfaceView;
    private ReverseSurfaceView mReverseSurfaceView;
    private LuminanceSurfaceView mLuminanceSurfaceView;

    private boolean isResume = false;

    private Handler mUIHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LogUtils.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multi_surface);

        mFiltersLayout1 = findViewById(R.id.fl_filter_layout1);
        mFiltersLayout2 = findViewById(R.id.ll_filter_layout2);

        mMainSurfaceView = findViewById(R.id.main_surface_view);
        mMainSurfaceView.setOnEGLContextCreatedListener(this);
        mMainSurfaceView.setOnEGLContextToDestroyListener(this);
        mMainSurfaceView.setSharedTextureChangedListener(this);
        mMainSurfaceView.setOnNewImageDrawnListener(this);

        int sharedTextureId = mMainSurfaceView.getSharedTextureId();
        mGraySurfaceView = new GraySurfaceView(this);
        mGraySurfaceView.setExternalTextureId(sharedTextureId);

        mReverseSurfaceView = new ReverseSurfaceView(this);
        mReverseSurfaceView.setExternalTextureId(sharedTextureId);

        mLuminanceSurfaceView = new LuminanceSurfaceView(this);
        mLuminanceSurfaceView.setExternalTextureId(sharedTextureId);
    }

    @Override
    protected void onStart() {
        LogUtils.w(TAG, "onStart");
        super.onStart();
    }

    @Override
    protected void onResume() {
        LogUtils.w(TAG, "onResume");
        super.onResume();
        isResume = true;
    }

    @Override
    protected void onPause() {
        LogUtils.w(TAG, "onPause");
        isResume = false;
        super.onPause();
    }

    @Override
    protected void onStop() {
        LogUtils.w(TAG, "onStop");
        super.onStop();
    }

    @Override
    public void onEGLContextCreated(final EGLContext eglContext) {
        LogUtils.w(TAG, "onEGLContextCreated");
        mGraySurfaceView.importEGLContext(eglContext, null);
        mReverseSurfaceView.importEGLContext(eglContext, null);
        mLuminanceSurfaceView.importEGLContext(eglContext, null);

        mUIHandler.postDelayed(mAddFilterRunnable, 10);
    }

    private Runnable mAddFilterRunnable = new Runnable() {
        @Override
        public void run() {
            if (isResume) {
                // 避免错误：java.lang.IllegalArgumentException:
                // Make sure the SurfaceView or associated SurfaceHolder has a valid Surface
                addFilterView();
            } else {
                mUIHandler.removeCallbacks(this);
                mUIHandler.postDelayed(this, 10);
            }
        }
    };

    private void addFilterView() {
        LogUtils.w(TAG, "addFilterView");
        mFiltersLayout1.removeAllViews();
        mFiltersLayout2.removeAllViews();

        int margin = (int) (getResources().getDimension(R.dimen.common_inteval) + 0.5f);

        ViewGroup.LayoutParams lp1 = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        mGraySurfaceView.setUsability(true);
        mFiltersLayout1.addView(mGraySurfaceView, lp1);

        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        lp2.weight = 1;
        lp2.rightMargin = margin;
        mReverseSurfaceView.setUsability(true);
        mFiltersLayout2.addView(mReverseSurfaceView, lp2);

        LinearLayout.LayoutParams lp3 = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        lp3.weight = 1;
        lp3.rightMargin = 0;
        mLuminanceSurfaceView.setUsability(true);
        mFiltersLayout2.addView(mLuminanceSurfaceView, lp3);
    }

    @Override
    public void onSharedTextureChanged(int textureID) {
        if (mGraySurfaceView != null) {
            mGraySurfaceView.setExternalTextureId(textureID);
        }
        if (mReverseSurfaceView != null) {
            mReverseSurfaceView.setExternalTextureId(textureID);
        }
        if (mLuminanceSurfaceView != null) {
            mLuminanceSurfaceView.setExternalTextureId(textureID);
        }
    }

    @Override
    public void onNewImageDrawn() {
        if (mGraySurfaceView != null) {
            mGraySurfaceView.requestRender();
        }
        if (mReverseSurfaceView != null) {
            mReverseSurfaceView.requestRender();
        }
        if (mLuminanceSurfaceView != null) {
            mLuminanceSurfaceView.requestRender();
        }
    }

    @Override
    public void onEGLContextToDestroy() {
        LogUtils.w(TAG, "onEGLContextToDestroy");
        mUIHandler.removeCallbacksAndMessages(mAddFilterRunnable);
        if (mGraySurfaceView != null) {
            mGraySurfaceView.setUsability(false);
        }
        if (mReverseSurfaceView != null) {
            mReverseSurfaceView.setUsability(false);
        }
        if (mLuminanceSurfaceView != null) {
            mLuminanceSurfaceView.setUsability(false);
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mFiltersLayout1.removeAllViews();
                mFiltersLayout2.removeAllViews();
                LogUtils.w(TAG, "onEGLContextToDestroy removeAllViews done");
            }
        });
    }

    @Override
    protected void onDestroy() {
        LogUtils.d(TAG, "onDestroy");
        mUIHandler.removeCallbacksAndMessages(null);
        mMainSurfaceView.clearSourceImage();
        mGraySurfaceView.importEGLContext(null, null);
        mReverseSurfaceView.importEGLContext(null, null);
        mLuminanceSurfaceView.importEGLContext(null, null);
        mFiltersLayout1.removeAllViews();
        mFiltersLayout2.removeAllViews();
        super.onDestroy();
    }

}
