package com.wtz.libvideomaker.renderer;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.opengl.GLES20;

import com.wtz.libvideomaker.R;
import com.wtz.libvideomaker.utils.GLBitmapUtils;
import com.wtz.libvideomaker.utils.LogUtils;
import com.wtz.libvideomaker.utils.ShaderUtil;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class OnScreenRenderer extends BaseRender {
    
    private static final String TAG = OnScreenRenderer.class.getSimpleName();

    private Context mContext;
    private String mTag;

    private int mSurfaceWidth;
    private int mSurfaceHeight;

    private int mVertexShaderHandle;
    private int mFragmentShaderHandle;
    private int mProgramHandle;

    private static final int BYTES_PER_FLOAT = 4;

    /**
     * 在使用 VBO (顶点缓冲对象)之前，对象数据存储在客户端内存中，每次渲染时将其传输到 GPU 中。
     * 随着我们的场景越来越复杂，有更多的物体和三角形，这会给 GPU 和内存增加额外的成本。
     * 使用 VBO，信息将被传输一次，然后渲染器将从该图形存储器缓存中得到数据。
     * 要注意的是：VBO 必须创建在一个有效的 OpenGL 上下文中，即在 GLThread 中创建。
     */
    private int[] mVBOIds;// 顶点缓冲区对象 ID 数组
    private int mVertexCoordBytes;
    private int mTextureCoordBytes;

    /* ---------- 顶点坐标配置：start ---------- */
    // java 层顶点坐标
    private float[] mVertexCoordData;

    // 每个顶点坐标大小
    private static final int VERTEX_COORD_DATA_SIZE = 2;

    // Native 层存放顶点坐标缓冲区
    private FloatBuffer mVertexCoordBuffer;

    // 用来传入顶点坐标的句柄
    private int mVertexCoordHandle;
    /* ---------- 顶点坐标配置：end ---------- */

    /* ---------- 纹理坐标配置：start ---------- */
    // java 层纹理坐标
    private float[] mTextureCoordData;

    // 每个纹理坐标大小
    private static final int TEXTURE_COORD_DATA_SIZE = 2;

    // Native 层存放纹理坐标缓冲区
    private FloatBuffer mTextureCoordBuffer;

    // 用来传入纹理坐标的句柄
    private int mTextureCoordHandle;
    /* ---------- 纹理坐标配置：end ---------- */

    // 用来传入纹理内容到片元着色器的句柄
    private int mTextureUniformHandle;

    // 外部传入的纹理内容句柄
    private int mExternalTextureId;

    public interface ScreenTextureChangeListener {
        void onScreenTextureChanged(int textureId);
    }

    private ScreenTextureChangeListener mScreenTextureChangeListener;

    public void setScreenTextureChangeListener(ScreenTextureChangeListener listener) {
        this.mScreenTextureChangeListener = listener;
    }

    public OnScreenRenderer(Context mContext, String tag) {
        this.mContext = mContext;
        this.mTag = tag;
    }

    public void setExternalTextureId(int id) {
        LogUtils.d(TAG, mTag + " setExternalTextureId " + id);
        this.mExternalTextureId = id;
        if (mScreenTextureChangeListener != null) {
            mScreenTextureChangeListener.onScreenTextureChanged(mExternalTextureId);
        }
    }

    public int getExternalTextureId() {
        return mExternalTextureId;
    }

    @Override
    public void onEGLContextCreated() {
        LogUtils.d(TAG, mTag + " onEGLContextCreated");
        initShaderProgram();
        initCoordinatesData();
    }

    private void initShaderProgram() {
        // 创建着色器程序
        String vertexSource = ShaderUtil.readRawText(mContext, R.raw.vertex_onscreen_shader);
        String fragmentSource = ShaderUtil.readRawText(mContext, R.raw.fragment_normal_texture2d_shader);
        int[] shaderIDs = ShaderUtil.createAndLinkProgram(vertexSource, fragmentSource);
        mVertexShaderHandle = shaderIDs[0];
        mFragmentShaderHandle = shaderIDs[1];
        mProgramHandle = shaderIDs[2];
        if (mProgramHandle <= 0) {
            throw new RuntimeException("initShaderProgram Error: createAndLinkProgram failed.");
        }

        // 获取顶点着色器和片元着色器中的变量句柄
        mVertexCoordHandle = GLES20.glGetAttribLocation(mProgramHandle, "a_Position");
        mTextureCoordHandle = GLES20.glGetAttribLocation(mProgramHandle, "a_TexCoordinate");
        mTextureUniformHandle = GLES20.glGetUniformLocation(mProgramHandle, "u_Texture");
    }

    private void initCoordinatesData() {
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
        // 顶点坐标，决定图像内容最终显示的位置区域
        mVertexCoordData = new float[]{
                -1f, -1f,
                1f, -1f,
                -1f, 1f,
                1f, 1f
        };
        mVertexCoordBuffer = ByteBuffer
                .allocateDirect(mVertexCoordData.length * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(mVertexCoordData);
        mVertexCoordBuffer.position(0);
        mVertexCoordData = null;

        // 纹理坐标（窗口、FBO），决定图像内容选取的区域部分和摆放方向
        // 窗口纹理坐标，上下左右四角要与顶点坐标一一对应起来
        mTextureCoordData = new float[]{
                0f, 1f,
                1f, 1f,
                0f, 0f,
                1f, 0f
        };
        mTextureCoordBuffer = ByteBuffer
                .allocateDirect(mTextureCoordData.length * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(mTextureCoordData);
        mTextureCoordBuffer.position(0);
        mTextureCoordData = null;

        // 创建 VBO
        mVBOIds = new int[1];// 顶点与纹理共用一个 VBO
        GLES20.glGenBuffers(1, mVBOIds, 0);
        // 绑定 VBO
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVBOIds[0]);
        // 为 VBO 分配内存
        mVertexCoordBytes = mVertexCoordBuffer.capacity() * BYTES_PER_FLOAT;
        mTextureCoordBytes = mTextureCoordBuffer.capacity() * BYTES_PER_FLOAT;
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,
                mVertexCoordBytes + mTextureCoordBytes,/* 这个缓冲区应该包含的字节数 */
                null,/* 初始化整个内存数据，这里先不设置 */
                GLES20.GL_STATIC_DRAW /* 这个缓冲区不会动态更新 */
        );
        // 为 VBO 设置数据
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER,
                0, mVertexCoordBytes, mVertexCoordBuffer);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER,
                mVertexCoordBytes, mTextureCoordBytes, mTextureCoordBuffer);
        // 解绑 VBO
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        LogUtils.d(TAG, mTag + " onSurfaceChanged " + width + "x" + height);
        super.onSurfaceChanged(width, height);
        this.mSurfaceWidth = width;
        this.mSurfaceHeight = height;
    }

    @Override
    public void onDrawFrame() {
        // 清屏
        if (canClearScreenOnDraw || forceClearScreenOnce) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glClearColor(0f, 0f, 0f, 1.0f);
            if (forceClearScreenOnce) {
                forceClearScreenOnce = false;
            }
        }

        // 使用程序对象 mProgramHandle 作为当前渲染状态的一部分
        GLES20.glUseProgram(mProgramHandle);

        // 准备设置坐标，先绑定 VBO ---------->
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVBOIds[0]);

        // 设置顶点坐标
        GLES20.glEnableVertexAttribArray(mVertexCoordHandle);
        GLES20.glVertexAttribPointer(mVertexCoordHandle, VERTEX_COORD_DATA_SIZE,
                GLES20.GL_FLOAT, false, 8,
                0 /* 此处为 VBO 中的数据偏移地址 */
        );

        // 设置纹理坐标
        GLES20.glEnableVertexAttribArray(mTextureCoordHandle);
        GLES20.glVertexAttribPointer(mTextureCoordHandle, TEXTURE_COORD_DATA_SIZE,
                GLES20.GL_FLOAT, false, 8,
                mVertexCoordBytes /* 此处为 VBO 中的数据偏移地址 */
        );

        // 所有坐标设置完成后，解绑 VBO <----------
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        // 将纹理单元激活，并绑定到指定纹理对象数据
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mExternalTextureId);

        // 将纹理数据传入到片元着色器 Uniform 变量中
        // glUniformX 用于更改 uniform 变量或数组的值，要更改的 uniform 变量的位置由 location 指定，
        GLES20.glUniform1i(mTextureUniformHandle, 0);// 诉纹理标准采样器在着色器中使用纹理单元 0

        // 开始渲染图形：按照绑定的顶点坐标数组从第 1 个开始画 4 个点，一共 2 个三角形，组成一个矩形
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        // 解绑 Texture
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    public void takePhoto(String pathName) {
        Bitmap bitmap = GLBitmapUtils.savePixels(0, 0, mSurfaceWidth, mSurfaceHeight);
        GLBitmapUtils.saveBitmap(bitmap, pathName);

        // 注意：以 Environment.getExternalStorageDirectory() 为开头的路径才会通知图库扫描有效
        Uri contentUri = Uri.fromFile(new File(pathName));
        Intent i = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, contentUri);
        mContext.sendBroadcast(i);
    }

    @Override
    public void onEGLContextToDestroy() {
        LogUtils.d(TAG, mTag + " onEGLContextToDestroy");
        if (mVertexCoordBuffer != null) {
            mVertexCoordBuffer.clear();
            mVertexCoordBuffer = null;
        }
        if (mTextureCoordBuffer != null) {
            mTextureCoordBuffer.clear();
            mTextureCoordBuffer = null;
        }
        if (mProgramHandle > 0) {
            GLES20.glDetachShader(mProgramHandle, mVertexShaderHandle);
            GLES20.glDeleteShader(mVertexShaderHandle);
            mVertexShaderHandle = 0;

            GLES20.glDetachShader(mProgramHandle, mFragmentShaderHandle);
            GLES20.glDeleteShader(mFragmentShaderHandle);
            mFragmentShaderHandle = 0;

            GLES20.glDeleteProgram(mProgramHandle);
            mProgramHandle = 0;
        }
        if (mVBOIds != null) {
            GLES20.glDeleteBuffers(mVBOIds.length, mVBOIds, 0);
            mVBOIds = null;
        }
    }

}
