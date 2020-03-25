package com.wtz.libvideomaker;

import android.opengl.EGL14;
import android.view.Surface;

import com.wtz.libvideomaker.utils.LogUtils;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGL11;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

/**
 * It's written with reference to android.opengl.GLSurfaceView.EglHelper
 * <p>
 * OpenGL ES 定义了图形渲染的一套 API，而不是定义了一套 window 系统，为了容许 GLES 工作在各种平台，
 * 它需要与一个了解如何在操作系统上创建和访问 windows 的库来协作，在 Android 上，该库被称为 EGL。
 * <p>
 * 此类使用 EGL 配置 OpenGL ES 与显示设备及 Surface 相关联的环境，然后即可使用 OpenGL ES 指令绘制图形；
 * EGL不提供 lock/unlock 调用，而是先使用 GLES 发出绘制命令，再调用 eglSwapBuffer() 来提交当前帧即可。
 * <p>
 * 在你使用 GLES 任何功能前，你需要创建一个 EGLContext 上下文和一个 EGLSurface。
 * 此后 GLES 操作将应用于当前上下文中，该上下文通过线程局部存储来访问，而不是通过参数来传递。
 * 这就要求你很小心地保证：你的渲染代码只能在那个创建上下文的线程里执行。
 * <p>
 * Surface 代表了生产者侧的 BufferQueue，通常由 SurfaceFlinger 消费；
 * 当你在一个 Surface 上渲染时，其最终结果放在某个缓冲区中被送到消费者。
 * 你可以使用任何能够写入 BufferQueue 的机制来更新 surface：
 * - 使用 Surface-supplied Canvas 函数；
 * - 将一个 EGLSurface 连接到 Surface，从而提供一个绘图位置给 GLES，然后可使用 GLES 指令来绘图；
 * - 配置一个 MediaCodec 视频解码器来写入 Surface；
 * <p>
 * EGLSurface 可以是 EGL 分配的一块离屏（off-screen）缓冲，又称 pbuffer；
 * 也可以是操作系统分配的 window，通过函数 eglCreateWindowSurface() 创建，
 * 该函数接收一个 “window” 对象作为参数，在 Android 中，此对象的类型可能是
 * SurfaceView、SurfaceTexture 、SurfaceHolder 或者 Surface 中的任何一种，
 * 所有的这些类型底层都有一个 BufferQueue。
 * 当你调用该函数时，EGL创建一个新的 EGLSurface 对象，并将此对象连接到该窗口对象的 BufferQueue 的生产者上。
 * <p>
 * 一个 EGLSurface 同一时刻只能对应于一个 Surface，因为你只能将一个生产者连接到某个 BufferQueue；
 * 如果你销毁了 EGLSurface，它将从 BufferQueue 断开，这时候就可以容许其他生产者连接到该 BufferQueue了。
 * <p>
 * 一个指定的线程可以通过 eglMakeCurrent 来在多个 EGLSurface 间切换；
 * 一个 EGLSurface 一次只能作为一个线程的 "current"。
 * <p>
 * 注：以上概念参考 https://source.android.com/devices/graphics/architecture.html
 * <p>
 * 在 Android 4.2（API 17) 以前的版本没有 EGL14，只有 EGL10 和 EGL11，但大部分接口都一样
 */
public class WeEGLHelper {
    private static final String TAG = "WeEGLHelper";
    private String mExternalTag;

    private EGL10 mEGL;
    private EGLDisplay mEGLDisplay;
    private EGLContext mEGLContext;
    private EGLSurface mEGLSurface;

    public WeEGLHelper(String externalTag) {
        this.mExternalTag = externalTag + " ";
    }

    public void initEGL(Surface surface, EGLContext shareContext) {
        // Get an EGL instance
        mEGL = (EGL10) EGLContext.getEGL();

        // Get to the default display
        mEGLDisplay = mEGL.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        if (mEGLDisplay == EGL10.EGL_NO_DISPLAY) {
            throw new RuntimeException(exceptionPrefix() + "eglGetDisplay failed");
        }

        // We can now initialize EGL for that display
        int[] versionCodes = new int[2];
        if (!mEGL.eglInitialize(mEGLDisplay, versionCodes)) {
            throw new RuntimeException(exceptionPrefix() + "eglInitialize failed");
        }
        String versionStr = mEGL.eglQueryString(mEGLDisplay, EGL10.EGL_VERSION);
        LogUtils.i(TAG, mExternalTag + "egl Initialized versionCodes:" + versionCodes[0] + "," + versionCodes[1] + "; versionStr:" + versionStr);

        // EGL 有 3 种 Surface：
        // window - 用于屏上（onscreen）渲染
        // pbuffer - 用于离屏（offscreen）渲染
        // pixmap - 离屏渲染，但本地渲染 API 也可以访问
        // 这里我们选择 window 类型
        int[] configAttributes = new int[]{
                EGL10.EGL_RED_SIZE, 8,/* Color buffer 中 R 分量的颜色位数 */
                EGL10.EGL_GREEN_SIZE, 8,/* Color buffer 中 G 分量的颜色位数 */
                EGL10.EGL_BLUE_SIZE, 8,/* Color buffer 中 B 分量的颜色位数 */
                EGL10.EGL_ALPHA_SIZE, 8,/* Color buffer 中 A 分量的颜色位数 */
                EGL10.EGL_DEPTH_SIZE, 8,/* Depth(深度) buffer 中 Z 的位数 */
                EGL10.EGL_STENCIL_SIZE, 8,/* Stencil(模板) buffer 个数 */
                // EGL_RENDERABLE_TYPE 要与 eglCreateContext 设置的 EGL_CONTEXT_CLIENT_VERSION 版本一致，
                // 否则创建 Context 失败会报 EGL_BAD_CONFIG
                EGL10.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,/* OpenGL ES 2.0 版本 */
                EGL10.EGL_SURFACE_TYPE, EGL10.EGL_WINDOW_BIT,/* EGL 窗口支持的类型 */
                EGL10.EGL_NONE/* EGL 的 attr_list 以 EGL_NONE 结束 */
        };
        int[] configNumArray = new int[1];// 用来获取对应属性的配置数量
        // 设置上述属性
        if (!mEGL.eglChooseConfig(mEGLDisplay, configAttributes, null, 0,
                configNumArray)) {
            throw new IllegalArgumentException(exceptionPrefix() + "eglChooseConfig failed");
        }

        // 得到对应属性的配置数量
        int configsNum = configNumArray[0];
        if (configsNum <= 0) {
            throw new IllegalArgumentException(exceptionPrefix() + "No configs match configAttributes");
        }

        // 得到对应属性的配置
        EGLConfig[] configs = new EGLConfig[configsNum];
        if (!mEGL.eglChooseConfig(mEGLDisplay, configAttributes, configs, configsNum,
                configNumArray)) {
            throw new IllegalArgumentException(exceptionPrefix() + "eglChooseConfig#2 failed");
        }

        // 创建 EGLContext，使用主流 EGL 2.0 版本
        int[] versionList = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE};
        if (shareContext != null) {
            LogUtils.w(TAG, mExternalTag + "use shareContext " + shareContext);
            mEGLContext = mEGL.eglCreateContext(
                    mEGLDisplay, configs[0], shareContext, versionList);
        } else {
            mEGLContext = mEGL.eglCreateContext(
                    mEGLDisplay, configs[0], EGL10.EGL_NO_CONTEXT, versionList);
        }
        if (mEGLContext == null || mEGLContext == EGL10.EGL_NO_CONTEXT) {
            mEGLContext = null;
            throw new RuntimeException(exceptionPrefix() + "create EGLContext failed: " + getEglErrorString(mEGL.eglGetError()));
        }

        // 创建 WindowSurface
        mEGLSurface = mEGL.eglCreateWindowSurface(mEGLDisplay, configs[0], surface, null);
        if (mEGLSurface == null || mEGLSurface == EGL10.EGL_NO_SURFACE) {
            if (mEGL.eglGetError() == EGL10.EGL_BAD_NATIVE_WINDOW) {
                LogUtils.e(TAG, mExternalTag + "createWindowSurface returned EGL_BAD_NATIVE_WINDOW.");
            }
            throw new RuntimeException(exceptionPrefix() + "create EGLSurface failed!");
        }

        // Before we can issue GL commands,
        // we need to make sure the context is current and bound to a surface.
        if (!mEGL.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
            // Could not make the context current, probably because the underlying
            // SurfaceView surface has been destroyed.
            LogUtils.e(TAG, mExternalTag + "eglMakeCurrent error:" + getEglErrorString(mEGL.eglGetError()));
            throw new RuntimeException(exceptionPrefix() + "eglMakeCurrent failed!");
        }

        LogUtils.w(TAG, mExternalTag + "initEGL tid=" + android.os.Process.myTid() + ", mEGL=" + mEGL
                + ", display=" + mEGLDisplay + ", context=" + mEGLContext + ", surface=" + mEGLSurface);
    }

    public EGLContext getSharedEGLContext() {
        return mEGLContext;
    }

    /**
     * 将 Surface 缓冲区数据送给 FrameBuffer 显示
     */
    public int swapBuffers() {
        if (mEGL == null) {
            throw new IllegalStateException(
                    exceptionPrefix() + "invoke swapBuffers() but EGL instance is null!");
        }
        if (!mEGL.eglSwapBuffers(mEGLDisplay, mEGLSurface)) {
            LogUtils.e(TAG, mExternalTag + "eglSwapBuffers error: " + getEglErrorString(mEGL.eglGetError()));
            return mEGL.eglGetError();
        }
        return EGL10.EGL_SUCCESS;
    }

    private static String exceptionPrefix() {
        return TAG + " throwEglException tid=" + android.os.Process.myTid() + " ";
    }

    public void destroyEGL() {
        LogUtils.w(TAG, mExternalTag + "destroyEGL tid=" + android.os.Process.myTid() + ", mEGL=" + mEGL);
        if (mEGL == null) {
            return;
        }

        if (mEGLSurface != null && mEGLSurface != EGL10.EGL_NO_SURFACE) {
            mEGL.eglMakeCurrent(mEGLDisplay, EGL10.EGL_NO_SURFACE,
                    EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
            if (!mEGL.eglDestroySurface(mEGLDisplay, mEGLSurface)) {
                LogUtils.e(TAG, mExternalTag + "eglDestroySurface error:" + getEglErrorString(mEGL.eglGetError()));
            }
            mEGLSurface = null;
        }

        if (mEGLContext != null) {
            if (!mEGL.eglDestroyContext(mEGLDisplay, mEGLContext)) {
                LogUtils.e(TAG, mExternalTag + "eglDestroyContext error:" + getEglErrorString(mEGL.eglGetError()));
            }
            mEGLContext = null;
        }

        if (mEGLDisplay != null) {
            if (!mEGL.eglTerminate(mEGLDisplay)) {
                LogUtils.e(TAG, mExternalTag + "eglTerminate error:" + getEglErrorString(mEGL.eglGetError()));
            }
            mEGLDisplay = null;
        }

        mEGL = null;
    }

    public static String getEglErrorString(int error) {
        switch (error) {
            case EGL10.EGL_SUCCESS:
                return "EGL_SUCCESS";
            case EGL10.EGL_NOT_INITIALIZED:
                return "EGL_NOT_INITIALIZED";
            case EGL10.EGL_BAD_ACCESS:
                return "EGL_BAD_ACCESS";
            case EGL10.EGL_BAD_ALLOC:
                return "EGL_BAD_ALLOC";
            case EGL10.EGL_BAD_ATTRIBUTE:
                return "EGL_BAD_ATTRIBUTE";
            case EGL10.EGL_BAD_CONFIG:
                return "EGL_BAD_CONFIG";
            case EGL10.EGL_BAD_CONTEXT:
                return "EGL_BAD_CONTEXT";
            case EGL10.EGL_BAD_CURRENT_SURFACE:
                return "EGL_BAD_CURRENT_SURFACE";
            case EGL10.EGL_BAD_DISPLAY:
                return "EGL_BAD_DISPLAY";
            case EGL10.EGL_BAD_MATCH:
                return "EGL_BAD_MATCH";
            case EGL10.EGL_BAD_NATIVE_PIXMAP:
                return "EGL_BAD_NATIVE_PIXMAP";
            case EGL10.EGL_BAD_NATIVE_WINDOW:
                return "EGL_BAD_NATIVE_WINDOW";
            case EGL10.EGL_BAD_PARAMETER:
                return "EGL_BAD_PARAMETER";
            case EGL10.EGL_BAD_SURFACE:
                return "EGL_BAD_SURFACE";
            case EGL11.EGL_CONTEXT_LOST:
                return "EGL_CONTEXT_LOST";
            default:
                return getHex(error);
        }
    }

    private static String getHex(int value) {
        return "0x" + Integer.toHexString(value);
    }

}
