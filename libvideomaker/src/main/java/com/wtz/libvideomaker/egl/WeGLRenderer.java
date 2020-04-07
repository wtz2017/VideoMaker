package com.wtz.libvideomaker.egl;

/**
 * 接口方法在 WeGLThread 中回调
 */
public interface WeGLRenderer {

    /**
     * The renderer only renders
     * when the surface is created, or when requestRender() is called.
     */
    int RENDERMODE_WHEN_DIRTY = 0;

    /**
     * The renderer is called
     * continuously to re-render the scene.
     */
    int RENDERMODE_CONTINUOUSLY = 1;

    void onEGLContextCreated();

    void onSurfaceChanged(int width, int height);

    void onDrawFrame();

    void onEGLContextToDestroy();

}
