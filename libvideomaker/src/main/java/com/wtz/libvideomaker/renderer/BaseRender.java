package com.wtz.libvideomaker.renderer;

import android.opengl.GLES20;

import com.wtz.libvideomaker.egl.WeGLRenderer;

public abstract class BaseRender implements WeGLRenderer {

    protected boolean canClearScreenOnDraw = true;
    protected boolean forceClearScreenOnce = false;

    public void setClearScreenOnDraw(boolean clearScreen) {
        canClearScreenOnDraw = clearScreen;
    }

    protected float[] getDefaultVertexCoordData() {
        return new float[]{
                // 整个视口区域
                -1f, -1f,
                1f, -1f,
                -1f, 1f,
                1f, 1f
        };
    }

    protected float[] getDefaultTextureCoordData() {
        return new float[]{
                // 默认选取纹理全部区域
                0f, 1f,
                1f, 1f,
                0f, 0f,
                1f, 0f
        };
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        forceClearScreenOnce = true;
    }

}
