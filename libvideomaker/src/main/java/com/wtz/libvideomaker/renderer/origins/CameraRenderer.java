package com.wtz.libvideomaker.renderer.origins;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import com.wtz.libvideomaker.R;
import com.wtz.libvideomaker.renderer.BaseRender;
import com.wtz.libvideomaker.utils.LogUtils;
import com.wtz.libvideomaker.utils.ShaderUtil;
import com.wtz.libvideomaker.utils.TextureUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class CameraRenderer extends BaseRender implements SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = CameraRenderer.class.getSimpleName();

    private Context mContext;

    private Display mDisplay;
    private int mRotationAngle;
    private int mSurfaceWidth;
    private int mSurfaceHeight;
    private int mSourceImageWidth;
    private int mSourceImageHeight;
    private int mSourceRotatedWidth;
    private int mSourceRotatedHeight;

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

    /**
     * 使用 FBO 离屏渲染
     */
    private int mFBOId;

    /* ---------- 顶点坐标配置：start ---------- */
    // java 层顶点坐标
    private float[] mVertexCoordData;

    // 每个顶点坐标大小
    private static final int VERTEX_COORD_DATA_SIZE = 2;

    // Native 层存放顶点坐标缓冲区
    private FloatBuffer mVertexCoordBuffer;

    // 用来传入顶点坐标的句柄
    private int mVertexCoordHandle;

    // 用来保存位置变换矩阵数值的数组
    private float[] mPositionMatrix;
    // 用来保存投影矩阵数值的数组
    private float[] mProjectionMatrix;

    // 用来传入顶点位置矩阵数值的句柄
    private int mPosMatrixUnifHandle;
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

    // Camera preview
    private int mCameraTextureID;
    private SurfaceTexture mCameraSurfaceTexture;
    private int mCameraId;

    // 准备输出的纹理内容句柄
    private int[] mOutputTextureIds;
    private int[] mOldOutputTextureIds;

    public interface OnSharedTextureChangedListener {
        void onSharedTextureChanged(int textureID);
    }

    public interface SurfaceTextureListener {
        void onSurfaceTextureCreated(SurfaceTexture surfaceTexture);

        void onFrameAvailable();
    }

    private OnSharedTextureChangedListener mSharedTextureChangedListener;
    private SurfaceTextureListener mSurfaceTextureListener;

    public CameraRenderer(Context mContext, SurfaceTextureListener listener) {
        this.mContext = mContext;
        this.mSurfaceTextureListener = listener;
    }

    public void setSharedTextureChangedListener(OnSharedTextureChangedListener listener) {
        this.mSharedTextureChangedListener = listener;
    }

    public int getSharedTextureId() {
        return mOutputTextureIds != null ? mOutputTextureIds[0] : 0;
    }

    @Override
    public void onEGLContextCreated() {
        LogUtils.d(TAG, "onEGLContextCreated");
        initShaderProgram();
        initCoordinatesData();
        initFBO();
        initCameraSurface();
    }

    private void initShaderProgram() {
        // 创建着色器程序
        String vertexSource = ShaderUtil.readRawText(mContext, R.raw.vertex_offscreen_shader);
        String fragmentSource = ShaderUtil.readRawText(mContext, R.raw.fragment_camera_oes_shader);
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
        mPosMatrixUnifHandle = GLES20.glGetUniformLocation(mProgramHandle, "u_PositionMatrix");
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
        mVertexCoordData = getDefaultVertexCoordData();
        mVertexCoordBuffer = ByteBuffer
                .allocateDirect(mVertexCoordData.length * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(mVertexCoordData);
        mVertexCoordBuffer.position(0);
        mVertexCoordData = null;

        // 创建位置转换矩阵(4x4)返回值存储的数组
        mPositionMatrix = new float[16];
        mProjectionMatrix = new float[16];

        // 纹理坐标（窗口、FBO），决定图像内容选取的区域部分和摆放方向
        // FBO 纹理坐标，上下左右四角要与顶点坐标一一对应起来
        // 这里使用窗口坐标，并使用矩阵旋转来代替 FBO 坐标翻转
        mTextureCoordData = getDefaultTextureCoordData();
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

    private void initFBO() {
        // 创建 FBO
        int[] fboIds = new int[1];
        GLES20.glGenBuffers(1, fboIds, 0);
        if (fboIds[0] == 0) {
            throw new RuntimeException("initFBO glGenBuffers failed!");
        }
        mFBOId = fboIds[0];

        // 初始化要绑定的纹理
        mOutputTextureIds = new int[]{0};
        mOldOutputTextureIds = new int[]{0};
    }

    private void initCameraSurface() {
        int[] ids = TextureUtils.genTextureOES(1);
        mCameraTextureID = ids[0];
        mCameraSurfaceTexture = new SurfaceTexture(mCameraTextureID);
        mCameraSurfaceTexture.setOnFrameAvailableListener(this);
        mSurfaceTextureListener.onSurfaceTextureCreated(mCameraSurfaceTexture);
    }

    public void initCameraParams(int cameraId, int previewWidth, int previewHeight) {
        LogUtils.w(TAG, "initCameraParams id=" + mCameraId + "; preview size=" + previewWidth + "x" + previewHeight);
        saveCameraParams(cameraId, previewWidth, previewHeight);
        mRotationAngle = getRotationAngle();
    }

    public void onCameraChanged(int cameraId, int previewWidth, int previewHeight) {
        LogUtils.w(TAG, "onCameraChanged id=" + cameraId + "; preview size=" + previewWidth + "x" + previewHeight);
        saveCameraParams(cameraId, previewWidth, previewHeight);
        changePositionMatrix();
    }

    private void saveCameraParams(int cameraId, int previewWidth, int previewHeight) {
        this.mCameraId = cameraId;
        this.mSourceImageWidth = previewWidth;
        this.mSourceImageHeight = previewHeight;
    }

    public void onOrientationChanged() {
        mRotationAngle = getRotationAngle();
        LogUtils.d(TAG, "onOrientationChanged angle=" + mRotationAngle);
        changePositionMatrix();
    }

    private int getRotationAngle() {
        if (mDisplay == null) {
            mDisplay = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        }
        return mDisplay.getRotation();
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        LogUtils.d(TAG, "onSurfaceChanged " + width + "x" + height);
        mSurfaceWidth = width;
        mSurfaceHeight = height;
        GLES20.glViewport(0, 0, width, height);
        changePositionMatrix();
        bindTextureToFBO(width, height);
    }

    private void changePositionMatrix() {
        if (mPositionMatrix == null ||mProjectionMatrix == null) {
            return;
        }
        // 初始化单位矩阵
        Matrix.setIdentityM(mPositionMatrix, 0);
        Matrix.setIdentityM(mProjectionMatrix, 0);

        // 旋转角度
        // rotateM(float[] m, int mOffset, float a, float x, float y, float z)
        //  * @param a angle to rotate in degrees，正值逆时针，负值顺时针
        //  * @param x、y、z： 是否需要沿着 X、Y、Z 轴旋转， 0 不旋转，1f 需要旋转
        switch (mRotationAngle) {
            case Surface.ROTATION_0:
                if (mCameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    Matrix.rotateM(mPositionMatrix, 0, 90f, 0, 0, 1f);
                    Matrix.rotateM(mPositionMatrix, 0, 180, 1, 0, 0);
                    setRotatedImgWH(true);
                } else {
                    Matrix.rotateM(mPositionMatrix, 0, 90f, 0f, 0f, 1f);
                    setRotatedImgWH(true);
                }

                break;
            case Surface.ROTATION_90:
                if (mCameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    Matrix.rotateM(mPositionMatrix, 0, 180, 0, 0, 1);
                    Matrix.rotateM(mPositionMatrix, 0, 180, 0, 1, 0);
                    setRotatedImgWH(false);
                } else {
                    Matrix.rotateM(mPositionMatrix, 0, 180f, 0f, 0f, 1f);
                    setRotatedImgWH(false);
                }
                break;
            case Surface.ROTATION_180:
                if (mCameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    Matrix.rotateM(mPositionMatrix, 0, 90f, 0.0f, 0f, 1f);
                    Matrix.rotateM(mPositionMatrix, 0, 180f, 0.0f, 1f, 0f);
                    setRotatedImgWH(true);
                } else {
                    Matrix.rotateM(mPositionMatrix, 0, -90, 0f, 0f, 1f);
                    setRotatedImgWH(true);
                }
                break;
            case Surface.ROTATION_270:
                if (mCameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    Matrix.rotateM(mPositionMatrix, 0, 180f, 0.0f, 1f, 0f);
                    setRotatedImgWH(false);
                } else {
                    Matrix.rotateM(mPositionMatrix, 0, 0f, 0f, 0f, 1f);
                    setRotatedImgWH(false);
                }
                break;
        }

        // 正交投影
        float imageRatio = mSourceRotatedWidth * 1.0f / mSourceRotatedHeight;
        float containerRatio = mSurfaceWidth * 1.0f / mSurfaceHeight;
        if (containerRatio >= imageRatio) {
            // 容器比图像更宽一些，横向居中展示
            float imageNormalWidth = 1 - (-1);
            float containerNormalWidth = mSurfaceWidth / (mSurfaceHeight * imageRatio) * imageNormalWidth;
            Matrix.orthoM(mProjectionMatrix, 0,
                    -containerNormalWidth / 2, containerNormalWidth / 2,
                    -1f, 1f,
                    -1f, 1f);
        } else {
            // 容器比图像更高一些，纵向居中展示
            float imageNormalHeight = 1 - (-1);
            float containerNormalHeight = mSurfaceHeight / (mSurfaceWidth / imageRatio) * imageNormalHeight;
            Matrix.orthoM(mProjectionMatrix, 0,
                    -1, 1,
                    -containerNormalHeight / 2, containerNormalHeight / 2,
                    -1f, 1f);
        }

        // 注意：旋转矩阵在左、投影矩阵在右
        Matrix.multiplyMM(mPositionMatrix, 0, mPositionMatrix, 0, mProjectionMatrix, 0);
    }

    /**
     * 设置旋转后的显示图像宽高，这会影响后续正交投影的判断
     *
     * @param swap 是否交换原始图像宽高，对于沿 Z 轴旋转了 90 或 270 度场景需要交换
     */
    private void setRotatedImgWH(boolean swap) {
        if (swap) {
            mSourceRotatedWidth = mSourceImageHeight;
            mSourceRotatedHeight = mSourceImageWidth;
        } else {
            mSourceRotatedWidth = mSourceImageWidth;
            mSourceRotatedHeight = mSourceImageHeight;
        }
    }

    private void bindTextureToFBO(int width, int height) {
        mOldOutputTextureIds[0] = mOutputTextureIds[0];

        /* ------ 1.创建要附加到 FBO 的纹理对象，此纹理将最终作为输出供外部使用 ------ */
        int[] textureIds = TextureUtils.genTexture2D(1);
        mOutputTextureIds[0] = textureIds[0];
        LogUtils.d(TAG, "bindTextureToFBO texture current=" + textureIds[0] + ",old=" + mOldOutputTextureIds[0]);
        // 绑定刚创建的附加纹理对象
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mOutputTextureIds[0]);
        // 为此纹理分配内存
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width,
                height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        // 解绑纹理
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        /* ------ 2.把纹理附加到 FBO ------ */
        // 绑定 FBO
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFBOId);
        // 把刚创建的纹理对象附加到 FBO
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, mOutputTextureIds[0], 0);
        int ret = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (ret != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Attach texture to FBO failed! FramebufferStatus:" + ret);
        }
        // 解绑 FBO
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        /* ------ 3.释放旧的纹理 ------ */
        if (mOldOutputTextureIds[0] != 0) {
            GLES20.glDeleteTextures(1, mOldOutputTextureIds, 0);
        }

        if (mSharedTextureChangedListener != null) {
            mSharedTextureChangedListener.onSharedTextureChanged(mOutputTextureIds[0]);
        }
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        mSurfaceTextureListener.onFrameAvailable();
    }

    @Override
    public void onDrawFrame() {
        // 绑定到 FBO 从而离屏渲染
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFBOId);

        // 清屏
        if (canClearScreenOnDraw) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glClearColor(0f, 0f, 0f, 1.0f);
        }

        // 使用程序对象 mProgramHandle 作为当前渲染状态的一部分
        GLES20.glUseProgram(mProgramHandle);

        // 准备设置坐标，绑定 VBO
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVBOIds[0]);

        // 启用顶点坐标传值句柄
        GLES20.glEnableVertexAttribArray(mVertexCoordHandle);
        // 设置顶点坐标
        GLES20.glVertexAttribPointer(mVertexCoordHandle, VERTEX_COORD_DATA_SIZE,
                GLES20.GL_FLOAT, false, 8,
                0 /* 此处为 VBO 中的数据偏移地址 */
        );
        // 设置位置矩阵，transpose 指明是否要转置矩阵，必须为 GL_FALSE
        GLES20.glUniformMatrix4fv(mPosMatrixUnifHandle, 1, false,
                mPositionMatrix, 0);

        // 启用纹理坐标传值句柄
        GLES20.glEnableVertexAttribArray(mTextureCoordHandle);
        // 设置纹理坐标
        GLES20.glVertexAttribPointer(mTextureCoordHandle, TEXTURE_COORD_DATA_SIZE,
                GLES20.GL_FLOAT, false, 8,
                mVertexCoordBytes /* 此处为 VBO 中的数据偏移地址 */
        );

        // 将纹理单元激活，并绑定到指定纹理对象数据
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mCameraTextureID);
        mCameraSurfaceTexture.updateTexImage();
        // 将纹理数据传入到片元着色器 Uniform 变量中
        GLES20.glUniform1i(mTextureUniformHandle, 0);// 诉纹理标准采样器在着色器中使用纹理单元 0

        // 开始渲染图形：按照绑定的顶点坐标数组从第 1 个开始画 n 个点，
        // 3 个点组成一个三角形，4个点一共 2 个三角形，组成一个矩形
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        // 解绑 Texture
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);

        // 解绑 VBO
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        // 解绑 FBO，从而可以恢复到屏上渲染
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    @Override
    public void onEGLContextToDestroy() {
        LogUtils.d(TAG, "onEGLContextToDestroy");
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
        if (mOutputTextureIds != null) {
            GLES20.glDeleteTextures(1, mOutputTextureIds, 0);
            mOutputTextureIds = null;
        }
        if (mOldOutputTextureIds != null) {
            GLES20.glDeleteTextures(1, mOldOutputTextureIds, 0);
            mOldOutputTextureIds = null;
        }
        if (mCameraSurfaceTexture != null) {
            mCameraSurfaceTexture.release();
            mCameraSurfaceTexture = null;
        }
        if (mCameraTextureID != 0) {
            GLES20.glDeleteTextures(1, new int[]{mCameraTextureID}, 0);
            mCameraTextureID = 0;
        }
        if (mVBOIds != null) {
            GLES20.glDeleteBuffers(mVBOIds.length, mVBOIds, 0);
            mVBOIds = null;
        }
        mPositionMatrix = null;
        mProjectionMatrix = null;
        mDisplay = null;
    }

}
