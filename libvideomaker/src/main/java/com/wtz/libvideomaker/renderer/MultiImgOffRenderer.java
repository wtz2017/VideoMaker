package com.wtz.libvideomaker.renderer;

import android.content.Context;
import android.opengl.Matrix;

public class MultiImgOffRenderer extends OffScreenImgRenderer {

    private static final String TAG = MultiImgOffRenderer.class.getSimpleName();

    private int[] mSourceImageResIds;

    private float[] mPositionMatrix;// 用来保存位置变换矩阵数值的数组

    public MultiImgOffRenderer(Context mContext, int[] sourceImageResIds) {
        super(mContext, TAG);
        this.mSourceImageResIds = sourceImageResIds;
    }

    @Override
    public void onEGLContextCreated() {
        // 创建位置转换矩阵(4x4)返回值存储的数组
        mPositionMatrix = new float[16];

        super.onEGLContextCreated();
    }

    @Override
    protected float[] getVertexCoordData() {
        /*
         *        归一化顶点坐标系                窗口纹理坐标系             FBO 纹理坐标系
         *               y                                                    y
         *               ↑                          ┆                         ↑
         * (-1,1)------(0,1)------(1,1)        ---(0,0)------(1,0)-->x   ---(0,1)------(1,1)
         *    ┆          ┆          ┆               ┆          ┆              ┆          ┆
         *    ┆          ┆          ┆               ┆          ┆              ┆          ┆
         * (-1,0)------(0,0)------(1,0)-->x         ┆          ┆              ┆          ┆
         *    ┆          ┆          ┆               ┆          ┆              ┆          ┆
         *    ┆          ┆          ┆               ┆          ┆              ┆          ┆
         * (-1,-1)-----(0,-1)-----(1,-1)          (0,1)------(1,1)       ---(0,0)------(1,0)-->x
         *                                          ↓                         ┆
         *                                          y
         */
        return new float[]{
                // 视口左上角 1/4 区域
                -1f, 0f,
                0f, 0f,
                -1f, 1f,
                0f, 1f,

                // 视口右上角 1/4 区域
                0f, 0f,
                1f, 0f,
                0f, 1f,
                1f, 1f,

                // 视口左下角 1/4 区域
                -1f, -1f,
                0f, -1f,
                -1f, 0f,
                0f, 0f,

                // 视口右下角 1/4 区域
                0f, -1f,
                1f, -1f,
                0f, 0f,
                1f, 0f,

                // 整个视口中心 1/16 区域
                -0.25f, -0.25f,
                0.25f, -0.25f,
                -0.25f, 0.25f,
                0.25f, 0.25f,
        };
    }

    @Override
    protected int getVertexDrawOffsetBytes(int sourceImgIndex) {
        // 最多支持5个索引，此后循环
        int index = sourceImgIndex % 5;
        // 每一组为一个矩形，4个点，8个浮点数值，每个浮点数值4字节
        return index * 8 * 4;
    }

    @Override
    protected int getVertexDrawCount(int sourceImgIndex) {
        // 每组4个点一共2个三角形，组成一个矩形
        return 4;
    }

    @Override
    protected int[] getSourceImageResIds() {
        return mSourceImageResIds;
    }

    @Override
    protected void onSourceImageLoaded(int[][] mSourceTextureInfos) {
    }

    @Override
    protected void changePositionMatrix(int width, int height) {
        // 初始化单位矩阵
        Matrix.setIdentityM(mPositionMatrix, 0);

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
