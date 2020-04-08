package com.wtz.libvideomaker.renderer.filters;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;

import com.wtz.libvideomaker.R;
import com.wtz.libvideomaker.renderer.BaseRender;
import com.wtz.libvideomaker.utils.LogUtils;
import com.wtz.libvideomaker.utils.ShaderUtil;
import com.wtz.libvideomaker.utils.TextUtils;
import com.wtz.libvideomaker.utils.TextureUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class WatermarkRenderer extends BaseRender {

    private static final String TAG = WatermarkRenderer.class.getSimpleName();

    public static final int CORNER_RIGHT_BOTTOM = 0;
    public static final int CORNER_LEFT_BOTTOM = 1;
    public static final int CORNER_RIGHT_TOP = 2;
    public static final int CORNER_LEFT_TOP = 3;

    private Context mContext;
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

    /**
     * 使用 FBO 离屏渲染
     */
    private int mFBOId;

    /* ---------- 顶点坐标配置：start ---------- */
    boolean needUpdateVertex;

    // java 层顶点坐标
    private float[] mVertexCoordData;

    // 每个顶点坐标大小
    private static final int VERTEX_COORD_DATA_SIZE = 2;

    // Native 层存放顶点坐标缓冲区
    private FloatBuffer mVertexCoordBuffer;

    // 用来传入顶点坐标的句柄
    private int mVertexCoordHandle;

    // 用来传入顶点位置矩阵数值的句柄
    private int mPosMatrixUnifHandle;

    // 图像水印顶点坐标
    private int mImageOffsetBytes;
    private float mImageLeftBottomX;
    private float mImageLeftBottomY;
    private float mImageRightBottomX;
    private float mImageRightBottomY;
    private float mImageLeftTopX;
    private float mImageLeftTopY;
    private float mImageRightTopX;
    private float mImageRightTopY;

    // 文字水印顶点坐标
    private int mTextOffsetBytes;
    private float mTextLeftBottomX;
    private float mTextLeftBottomY;
    private float mTextRightBottomX;
    private float mTextRightBottomY;
    private float mTextLeftTopX;
    private float mTextLeftTopY;
    private float mTextRightTopX;
    private float mTextRightTopY;

    private float[] mDefaultPositionMatrix;// 用来保存位置变换矩阵数值的数组
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

    // 图片水印纹理内容句柄
    private int mImageMarkTextureId;
    private Bitmap mImageMarkBitmap;
    private int mImageMarkShowWidth;
    private int mImageMarkShowHeight;
    private int mImageMarkCorner;
    private int mImageMarkMarginX;
    private int mImageMarkMarginY;
    private boolean isImageMarkChanged;

    // 文字水印纹理内容句柄
    private int mTextMarkTextureId;
    private Bitmap mTextMarkBitmap;
    private int mTextMarkShowWidth;
    private int mTextMarkShowHeight;
    private int mTextMarkCorner;
    private int mTextMarkMarginX;
    private int mTextMarkMarginY;
    private boolean isTextMarkChanged;

    // 准备输出的纹理内容句柄
    private int[] mOutputTextureIds;
    private int[] mOldOutputTextureIds;

    public interface OnMarkTextureChangedListener {
        void onMarkTextureChanged(int textureID);
    }

    private OnMarkTextureChangedListener mMarkTextureChangedListener;

    public WatermarkRenderer(Context mContext) {
        this.mContext = mContext;
    }

    public void setExternalTextureId(int id) {
        LogUtils.d(TAG, "setExternalTextureId " + id);
        this.mExternalTextureId = id;
    }

    public void setMarkTextureChangedListener(OnMarkTextureChangedListener listener) {
        this.mMarkTextureChangedListener = listener;
    }

    public int getMarkTextureId() {
        return mOutputTextureIds != null ? mOutputTextureIds[0] : 0;
    }

    @Override
    public void onEGLContextCreated() {
        LogUtils.d(TAG, "onEGLContextCreated");
        // 这两句可以开启纹理的透明度渲染
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        initShaderProgram();
        initCoordinatesData();
        initFBO();

        if (mImageMarkBitmap != null) {
            isImageMarkChanged = true;
        }
        if (mTextMarkBitmap != null) {
            isTextMarkChanged = true;
        }
    }

    private void initShaderProgram() {
        // 创建着色器程序
        String vertexSource = ShaderUtil.readRawText(mContext, R.raw.vertex_offscreen_shader);
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
        // 顶点坐标
        updateVertexCoordBuffer();

        // 创建位置转换矩阵(4x4)返回值存储的数组
        mDefaultPositionMatrix = new float[16];

        // 纹理坐标（窗口、FBO），决定图像内容选取的区域部分和摆放方向
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
                GLES20.GL_DYNAMIC_DRAW /* 这个缓冲区会动态更新 */
        );
        // 为 VBO 设置数据
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER,
                0, mVertexCoordBytes, mVertexCoordBuffer);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER,
                mVertexCoordBytes, mTextureCoordBytes, mTextureCoordBuffer);
        // 解绑 VBO
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    private void updateVertexVBO() {
        updateVertexCoordBuffer();
        // 绑定 VBO
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVBOIds[0]);
        // 更新顶点坐标数据
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER,
                0, mVertexCoordBytes, mVertexCoordBuffer);
        // 解绑 VBO
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    private void updateVertexCoordBuffer() {
        // 顶点坐标，决定图像内容最终显示的位置区域
        mVertexCoordData = new float[]{
                // input texture
                -1f, -1f,
                1f, -1f,
                -1f, 1f,
                1f, 1f,

                // image texture
                mImageLeftBottomX, mImageLeftBottomY,
                mImageRightBottomX, mImageRightBottomY,
                mImageLeftTopX, mImageLeftTopY,
                mImageRightTopX, mImageRightTopY,

                // text texture
                mTextLeftBottomX, mTextLeftBottomY,
                mTextRightBottomX, mTextRightBottomY,
                mTextLeftTopX, mTextLeftTopY,
                mTextRightTopX, mTextRightTopY,
        };
        mImageOffsetBytes = 8 * BYTES_PER_FLOAT;
        mTextOffsetBytes = 16 * BYTES_PER_FLOAT;
        if (mVertexCoordBuffer != null) {
            mVertexCoordBuffer.clear();
        }
        mVertexCoordBuffer = ByteBuffer
                .allocateDirect(mVertexCoordData.length * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(mVertexCoordData);
        mVertexCoordBuffer.position(0);
        mVertexCoordData = null;
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

    @Override
    public void onSurfaceChanged(int width, int height) {
        LogUtils.d(TAG, "onSurfaceChanged " + width + "x" + height);
        mSurfaceWidth = width;
        mSurfaceHeight = height;
        GLES20.glViewport(0, 0, width, height);
        changeDefaultPositionMatrix(width, height);
        bindTextureToFBO(width, height);
        if (mImageMarkBitmap != null) {
            changeImageMarkPosition(mImageMarkCorner, mImageMarkMarginX, mImageMarkMarginY);
        }
        if (mTextMarkBitmap != null) {
            changeTextMarkPosition(mTextMarkCorner, mTextMarkMarginX, mTextMarkMarginY);
        }
    }

    private void changeDefaultPositionMatrix(int width, int height) {
        // 初始化单位矩阵
        Matrix.setIdentityM(mDefaultPositionMatrix, 0);

        // 沿 x 轴旋转 180 度以解决FBO与纹理上下颠倒的问题
        // rotateM(float[] m, int mOffset, float a, float x, float y, float z)
        //  * @param a angle to rotate in degrees
        //  * @param x、y、z： 是否需要沿着 X、Y、Z 轴旋转， 0 不旋转，1f 需要旋转
        Matrix.rotateM(mDefaultPositionMatrix, 0, 180f, 1f, 0, 0);
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

        if (mMarkTextureChangedListener != null) {
            mMarkTextureChangedListener.onMarkTextureChanged(mOutputTextureIds[0]);
        }
    }

    public void setImageMark(Bitmap bitmap, int showWidth, int showHeight,
                             int corner, int marginX, int marginY) {
        mImageMarkBitmap = bitmap;
        mImageMarkShowWidth = showWidth;
        mImageMarkShowHeight = showHeight;
        mImageMarkCorner = corner;
        mImageMarkMarginX = marginX;
        mImageMarkMarginY = marginY;

        changeImageMarkPosition(corner, marginX, marginY);

        isImageMarkChanged = true;
    }

    public void setTextMark(String text, float textSizePixels, int paddingLeft, int paddingRight,
                            int paddingTop, int paddingBottom, int textColor, int bgColor,
                            int corner, int marginX, int marginY) {
        mTextMarkBitmap = TextUtils.drawText(text, textSizePixels, paddingLeft, paddingRight,
                paddingTop, paddingBottom, textColor, bgColor);
        mTextMarkShowWidth = mTextMarkBitmap.getWidth();
        mTextMarkShowHeight = mTextMarkBitmap.getHeight();
        mTextMarkCorner = corner;
        mTextMarkMarginX = marginX;
        mTextMarkMarginY = marginY;

        changeTextMarkPosition(corner, marginX, marginY);

        isTextMarkChanged = true;
    }

    public void changeImageMarkPosition(int corner, int marginX, int marginY) {
        if (mSurfaceWidth == 0) {
            return;
        }
        float[] position = getMarkPosition(mImageMarkShowWidth, mImageMarkShowHeight,
                corner, marginX, marginY);
        mImageLeftBottomX = position[0];
        mImageLeftBottomY = position[1];
        mImageRightBottomX = position[2];
        mImageRightBottomY = position[3];
        mImageLeftTopX = position[4];
        mImageLeftTopY = position[5];
        mImageRightTopX = position[6];
        mImageRightTopY = position[7];

        needUpdateVertex = true;
    }

    public void changeTextMarkPosition(int corner, int marginX, int marginY) {
        if (mSurfaceWidth == 0) {
            return;
        }
        float[] position = getMarkPosition(mTextMarkShowWidth, mTextMarkShowHeight,
                corner, marginX, marginY);
        mTextLeftBottomX = position[0];
        mTextLeftBottomY = position[1];
        mTextRightBottomX = position[2];
        mTextRightBottomY = position[3];
        mTextLeftTopX = position[4];
        mTextLeftTopY = position[5];
        mTextRightTopX = position[6];
        mTextRightTopY = position[7];

        needUpdateVertex = true;
    }

    /**
     * 获取水印在顶点坐标系中的位置
     *
     * @param showWidth
     * @param showHeight
     * @param corner
     * @param marginX
     * @param marginY
     * @return position
     * position[0] = leftBottomX
     * position[1] = leftBottomY
     * position[2] = rightBottomX
     * position[3] = rightBottomY
     * position[4] = leftTopX
     * position[5] = leftTopY
     * position[6] = rightTopX
     * position[7] = rightTopY
     */
    private float[] getMarkPosition(int showWidth, int showHeight,
                                    int corner, int marginX, int marginY) {
        int targetFullWidth = 2;
        int targetFullHeight = 2;

        float targetMarginX = targetFullWidth * marginX * 1.0f / mSurfaceWidth;
        float targetMarginY = targetFullHeight * marginY * 1.0f / mSurfaceHeight;
        float targetShowWidth = targetFullWidth * showWidth * 1.0f / mSurfaceWidth;
        float targetShowHeight = targetFullHeight * showHeight * 1.0f / mSurfaceHeight;
        float leftBottomX = 0, leftBottomY = 0, rightBottomX = 0, rightBottomY = 0,
                leftTopX = 0, leftTopY = 0, rightTopX = 0, rightTopY = 0;
        switch (corner) {
            case CORNER_RIGHT_BOTTOM:
                rightBottomX = 1 - targetMarginX;
                rightBottomY = -1 + targetMarginY;
                leftBottomX = rightBottomX - targetShowWidth;
                leftBottomY = rightBottomY;
                leftTopX = leftBottomX;
                leftTopY = leftBottomY + targetShowHeight;
                rightTopX = rightBottomX;
                rightTopY = leftTopY;
                break;

            case CORNER_LEFT_BOTTOM:
                leftBottomX = -1 + targetMarginX;
                leftBottomY = -1 + targetMarginY;
                rightBottomX = leftBottomX + targetShowWidth;
                rightBottomY = leftBottomY;
                rightTopX = rightBottomX;
                rightTopY = rightBottomY + targetShowHeight;
                leftTopX = leftBottomX;
                leftTopY = rightTopY;
                break;

            case CORNER_RIGHT_TOP:
                rightTopX = 1 - targetMarginX;
                rightTopY = 1 - targetMarginY;
                rightBottomX = rightTopX;
                rightBottomY = rightTopY - targetShowHeight;
                leftBottomX = rightBottomX - targetShowWidth;
                leftBottomY = rightBottomY;
                leftTopX = leftBottomX;
                leftTopY = rightTopY;
                break;

            case CORNER_LEFT_TOP:
                leftTopX = -1 + targetMarginX;
                leftTopY = 1 - targetMarginY;
                rightTopX = leftTopX + targetShowWidth;
                rightTopY = leftTopY;
                rightBottomX = rightTopX;
                rightBottomY = rightTopY - targetShowHeight;
                leftBottomX = leftTopX;
                leftBottomY = rightBottomY;
                break;
        }
        float[] position = new float[8];
        position[0] = leftBottomX;
        position[1] = leftBottomY;
        position[2] = rightBottomX;
        position[3] = rightBottomY;
        position[4] = leftTopX;
        position[5] = leftTopY;
        position[6] = rightTopX;
        position[7] = rightTopY;
        return position;
    }

    private void updateImageMark() {
        //释放旧的纹理
        if (mImageMarkTextureId != 0) {
            GLES20.glDeleteTextures(1, new int[]{mImageMarkTextureId}, 0);
            mImageMarkTextureId = 0;
        }
        if (mImageMarkBitmap == null) {
            return;
        }
        // 创建新的纹理
        int[] textureIds = TextureUtils.genTexture2D(1);
        mImageMarkTextureId = textureIds[0];
        LogUtils.d(TAG, "updateImageMark texture ID=" + mImageMarkTextureId);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mImageMarkTextureId);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, mImageMarkBitmap, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    private void updateTextMark() {
        // 释放旧的纹理
        if (mTextMarkTextureId != 0) {
            GLES20.glDeleteTextures(1, new int[]{mTextMarkTextureId}, 0);
            mTextMarkTextureId = 0;
        }
        if (mTextMarkBitmap == null) {
            return;
        }
        // 创建新的纹理
        int[] textureIds = TextureUtils.genTexture2D(1);
        mTextMarkTextureId = textureIds[0];
        LogUtils.d(TAG, "updateTextMark texture ID=" + mTextMarkTextureId);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextMarkTextureId);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, mTextMarkBitmap, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    @Override
    public void onDrawFrame() {
        if (mExternalTextureId == 0) {
            return;
        }

        // 检查更新水印
        needUpdateVertex = needUpdateVertex || isImageMarkChanged || isTextMarkChanged;
        if (needUpdateVertex) {
            needUpdateVertex = false;
            updateVertexVBO();
        }
        if (isImageMarkChanged) {
            isImageMarkChanged = false;
            updateImageMark();
        }
        if (isTextMarkChanged) {
            isTextMarkChanged = false;
            updateTextMark();
        }

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

        // 开始绘制多纹理
        drawTexture(0, mExternalTextureId, mDefaultPositionMatrix);
        if (mImageMarkTextureId != 0) {
            drawTexture(mImageOffsetBytes, mImageMarkTextureId, mDefaultPositionMatrix);
        }
        if (mTextMarkTextureId != 0) {
            drawTexture(mTextOffsetBytes, mTextMarkTextureId, mDefaultPositionMatrix);
        }

        // 解绑 Texture
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        // 解绑 VBO
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        // 解绑 FBO，从而可以恢复到屏上渲染
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    private void drawTexture(int VertexOffsetBytes, int textureId, float[] positionMatrix) {
        if (textureId == 0) {
            return;
        }

        // 启用顶点坐标传值句柄
        GLES20.glEnableVertexAttribArray(mVertexCoordHandle);
        // 设置顶点坐标
        GLES20.glVertexAttribPointer(mVertexCoordHandle, VERTEX_COORD_DATA_SIZE,
                GLES20.GL_FLOAT, false, 8,
                VertexOffsetBytes /* 此处为 VBO 中的数据偏移地址 */
        );
        // 设置位置矩阵，transpose 指明是否要转置矩阵，必须为 GL_FALSE
        GLES20.glUniformMatrix4fv(mPosMatrixUnifHandle, 1, false,
                positionMatrix, 0);

        // 启用纹理坐标传值句柄
        GLES20.glEnableVertexAttribArray(mTextureCoordHandle);
        // 设置纹理坐标
        GLES20.glVertexAttribPointer(mTextureCoordHandle, TEXTURE_COORD_DATA_SIZE,
                GLES20.GL_FLOAT, false, 8,
                mVertexCoordBytes /* 此处为 VBO 中的数据偏移地址 */
        );

        // 将纹理单元激活，并绑定到指定纹理对象数据
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        // 将纹理数据传入到片元着色器 Uniform 变量中
        GLES20.glUniform1i(mTextureUniformHandle, 0);// 诉纹理标准采样器在着色器中使用纹理单元 0

        // 开始渲染图形：按照绑定的顶点坐标数组从第 1 个开始画 n 个点，
        // 3 个点组成一个三角形，4个点一共 2 个三角形，组成一个矩形
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
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
        if (mImageMarkTextureId != 0) {
            GLES20.glDeleteTextures(1, new int[]{mImageMarkTextureId}, 0);
            mImageMarkTextureId = 0;
        }
        if (mTextMarkTextureId != 0) {
            GLES20.glDeleteTextures(1, new int[]{mTextMarkTextureId}, 0);
            mTextMarkTextureId = 0;
        }
        if (mVBOIds != null) {
            GLES20.glDeleteBuffers(mVBOIds.length, mVBOIds, 0);
            mVBOIds = null;
        }
        mDefaultPositionMatrix = null;
    }

    /**
     * 单独调用，不放在 onEGLContextToDestroy 中，以便在恢复后能继续渲染水印
     */
    public void releaseMarkBitmap() {
        mImageMarkBitmap = null;
        isImageMarkChanged = true;
        mTextMarkBitmap = null;
        isTextMarkChanged = true;
    }

}
