package com.wtz.libvideomaker.renderer.filters;

import android.content.Context;
import android.opengl.Matrix;

import com.wtz.libvideomaker.R;

public class LuminanceFilterRenderer extends FilterRenderer {

    private static final String TAG = LuminanceFilterRenderer.class.getSimpleName();

    private float[] mPositionMatrix;// 用来保存位置变换矩阵数值的数组

    public LuminanceFilterRenderer(Context mContext) {
        super(mContext, TAG);
    }

    @Override
    public void onEGLContextCreated() {
        // 创建位置转换矩阵(4x4)返回值存储的数组
        mPositionMatrix = new float[16];

        super.onEGLContextCreated();
    }

    @Override
    protected int getVertexShaderResId() {
        return R.raw.vertex_offscreen_shader;
    }

    @Override
    protected int getFragmentShaderResId() {
        return R.raw.fragment_luminance_texture2d_shader;
    }

    @Override
    protected float[] getVertexCoordData() {
        return new float[]{
                -1f, -1f,
                1f, -1f,
                -1f, 1f,
                1f, 1f
        };
    }

    @Override
    protected float[] getTextureCoordData() {
        return new float[]{
                0f, 1f,
                1f, 1f,
                0f, 0f,
                1f, 0f
        };
    }

    @Override
    protected int getVertexDrawCount() {
        return 4;
    }

    @Override
    protected void changePositionMatrix(int width, int height) {
        // 初始化单位矩阵
        Matrix.setIdentityM(mPositionMatrix, 0);

        // 沿 x 轴旋转 180 度以解决FBO与纹理上下颠倒的问题
        // rotateM(float[] m, int mOffset, float a, float x, float y, float z)
        //  * @param a angle to rotate in degrees
        //  * @param x、y、z： 是否需要沿着 X、Y、Z 轴旋转， 0 不旋转，1f 需要旋转
        Matrix.rotateM(mPositionMatrix, 0, 180f, 1f, 0, 0);
    }

    @Override
    protected float[] getPositionMatrix() {
        return mPositionMatrix;
    }

    @Override
    public void onEGLContextToDestroy() {
        super.onEGLContextToDestroy();
        mPositionMatrix = null;
    }

}
