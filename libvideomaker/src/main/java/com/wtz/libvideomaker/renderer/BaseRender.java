package com.wtz.libvideomaker.renderer;

import com.wtz.libvideomaker.egl.WeGLRenderer;

public abstract class BaseRender implements WeGLRenderer {

    protected boolean canClearScreenOnDraw = true;

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

}
