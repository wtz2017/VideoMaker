package com.wtz.libvideomaker.recorder;

import android.content.Context;
import android.content.Intent;
import android.media.MediaFormat;
import android.net.Uri;

import com.wtz.libvideomaker.egl.WeGLRenderer;
import com.wtz.libvideomaker.egl.WeGLVideoEncoder;
import com.wtz.libvideomaker.renderer.OnScreenRenderer;
import com.wtz.libvideomaker.utils.LogUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.microedition.khronos.egl.EGLContext;

public class WeVideoRecorder extends WeGLVideoEncoder implements WeGLRenderer {
    private static final String TAG = WeVideoRecorder.class.getSimpleName();

    private Context mContext;
    private OnScreenRenderer mOnScreenRenderer;

    private String mSaveVideoDir;
    private String mVideoPathName;
    private static final String VIDEO_PREFIX = "We_VID_";
    private static final String VIDEO_SUFFIX = ".mp4";
    private final SimpleDateFormat mSimpleDateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");

    public WeVideoRecorder(Context context) {
        this.mContext = context;
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        mOnScreenRenderer = new OnScreenRenderer(context, TAG);
    }

    public void setExternalTextureId(int id) {
        mOnScreenRenderer.setExternalTextureId(id);
    }

    public void setSaveVideoDir(String dir) {
        LogUtils.d(TAG, "setSaveVideoDir: " + dir);
        this.mSaveVideoDir = dir;
    }

    @Override
    protected WeGLRenderer getRenderer() {
        return this;
    }

    @Override
    protected String getExternalLogTag() {
        return TAG;
    }

    public void startEncode(EGLContext context, int videoWidth, int videoHeight) {
        mVideoPathName = getVideoPathName();
        super.startEncode(context, mVideoPathName, MediaFormat.MIMETYPE_VIDEO_AVC, videoWidth, videoHeight);
    }

    private String getVideoPathName() {
        String time = mSimpleDateFormat.format(new Date());
        return new File(mSaveVideoDir, VIDEO_PREFIX + time + VIDEO_SUFFIX).getAbsolutePath();
    }

    @Override
    public void onEGLContextCreated() {
        mOnScreenRenderer.onEGLContextCreated();
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        mOnScreenRenderer.onSurfaceChanged(width, height);
    }

    @Override
    public void onDrawFrame() {
        mOnScreenRenderer.onDrawFrame();
    }

    @Override
    public void stopEncode() {
        super.stopEncode();
        // 注意：以 Environment.getExternalStorageDirectory() 为开头的路径才会通知图库扫描有效
        Uri contentUri = Uri.fromFile(new File(mVideoPathName));
        Intent i = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, contentUri);
        mContext.sendBroadcast(i);
    }

    @Override
    public void onEGLContextToDestroy() {
        mOnScreenRenderer.onEGLContextToDestroy();
    }

}
