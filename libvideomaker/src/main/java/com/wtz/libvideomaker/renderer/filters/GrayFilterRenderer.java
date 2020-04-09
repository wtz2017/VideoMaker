package com.wtz.libvideomaker.renderer.filters;

import android.content.Context;

import com.wtz.libvideomaker.R;

public class GrayFilterRenderer extends FilterRenderer {

    private static final String TAG = GrayFilterRenderer.class.getSimpleName();

    private float[] mPositionMatrix;// 用来保存位置变换矩阵数值的数组

    public GrayFilterRenderer(Context mContext) {
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
        return R.raw.we_vidmk_vertex_offscreen_shader;
    }

    @Override
    protected int getFragmentShaderResId() {
        return R.raw.we_vidmk_fragment_gray_texture2d_shader;
    }

    @Override
    protected float[] getVertexCoordData() {
        return getDefaultVertexCoordData();
    }

    @Override
    protected float[] getTextureCoordData() {
        return getDefaultTextureCoordData();
    }

    @Override
    protected int getVertexDrawCount() {
        return 4;
    }

    @Override
    protected void changePositionMatrix(int width, int height) {
        defaultPositionMatrixChange(mPositionMatrix);
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
