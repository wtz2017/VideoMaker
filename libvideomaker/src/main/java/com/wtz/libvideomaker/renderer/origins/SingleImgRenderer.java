package com.wtz.libvideomaker.renderer.origins;

import android.content.Context;
import android.opengl.Matrix;
import android.text.TextUtils;

public class SingleImgRenderer extends ImgRenderer {

    private static final String TAG = SingleImgRenderer.class.getSimpleName();

    private int mSurfaceWidth;
    private int mSurfaceHeight;
    private int mSourceImageWidth;
    private int mSourceImageHeight;

    private int[] mResIds;
    private String[] mPaths;

    private float[] mPositionMatrix;// 用来保存位置变换矩阵数值的数组

    public SingleImgRenderer(Context mContext) {
        super(mContext, TAG);
    }

    @Override
    public void onEGLContextCreated() {
        // 创建位置转换矩阵(4x4)返回值存储的数组
        mPositionMatrix = new float[16];

        super.onEGLContextCreated();
    }

    @Override
    protected float[] getVertexCoordData() {
        return getDefaultVertexCoordData();
    }

    @Override
    protected int getVertexDrawOffsetBytes(int sourceImgIndex) {
        return 0;
    }

    @Override
    protected int getVertexDrawCount(int sourceImgIndex) {
        // 4个点一共 2 个三角形，组成一个矩形
        return 4;
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        super.onSurfaceChanged(width, height);
        this.mSurfaceWidth = width;
        this.mSurfaceHeight = height;
    }

    public void setImageResource(int resId) {
        if (mResIds == null) {
            mResIds = new int[]{resId};
        } else {
            mResIds[0] = resId;
        }
        setImageResources(mResIds);
    }

    public void setImagePath(String path) {
        if (TextUtils.isEmpty(path)) return;

        if (mPaths == null) {
            mPaths = new String[]{path};
        } else {
            mPaths[0] = path;
        }
        setImagePaths(mPaths);
    }

    @Override
    protected void onSourceImageLoaded(int[][] mSourceBitmapInfos) {
        mSourceImageWidth = mSourceBitmapInfos[0][0];
        mSourceImageHeight = mSourceBitmapInfos[0][1];
        changePositionMatrix(mSurfaceWidth, mSurfaceHeight);
    }

    @Override
    protected void changePositionMatrix(int width, int height) {
        // 初始化单位矩阵
        Matrix.setIdentityM(mPositionMatrix, 0);

        if (mSourceImageWidth > 0 && mSourceImageHeight > 0) {
            // 设置正交投影
            float imageRatio = mSourceImageWidth * 1.0f / mSourceImageHeight;
            float containerRatio = width * 1.0f / height;
            if (containerRatio >= imageRatio) {
                // 容器比图像更宽一些，横向居中展示
                float imageNormalWidth = 1 - (-1);
                float containerNormalWidth = width / (height * imageRatio) * imageNormalWidth;
                Matrix.orthoM(mPositionMatrix, 0,
                        -containerNormalWidth / 2, containerNormalWidth / 2,
                        -1f, 1f,
                        -1f, 1f);
            } else {
                // 容器比图像更高一些，纵向居中展示
                float imageNormalHeight = 1 - (-1);
                float containerNormalHeight = height / (width / imageRatio) * imageNormalHeight;
                Matrix.orthoM(mPositionMatrix, 0,
                        -1, 1,
                        -containerNormalHeight / 2, containerNormalHeight / 2,
                        -1f, 1f);
            }
        }

        // 沿 x 轴旋转 180 度
        // rotateM(float[] m, int mOffset, float a, float x, float y, float z)
        //  * @param a angle to rotate in degrees
        //  * @param x、y、z： 是否需要沿着 X、Y、Z 轴旋转， 0 不旋转，1f 需要旋转
        Matrix.rotateM(mPositionMatrix, 0, 180f, 1f, 0, 0);
    }

    @Override
    protected float[] getPositionMatrix(int sourceImgIndex) {
        return mPositionMatrix;
    }

    @Override
    public void onEGLContextToDestroy() {
        super.onEGLContextToDestroy();
        mPositionMatrix = null;
    }

}
