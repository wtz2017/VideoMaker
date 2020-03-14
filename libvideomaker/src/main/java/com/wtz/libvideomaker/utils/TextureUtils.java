package com.wtz.libvideomaker.utils;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;

public class TextureUtils {

    public static int[] genTexture2D(int num) {
        return genTexture(GLES20.GL_TEXTURE_2D, num);
    }

    public static int[] genTextureOES(int num) {
        return genTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, num);
    }

    private static int[] genTexture(int type, int num) {
        // 创建 Texture 对象并初始化配置
        int[] ids = new int[num];
        GLES20.glGenTextures(num, ids, 0);

        for (int i = 0; i < num; i++) {
            if (ids[i] == 0) {
                throw new RuntimeException("genTexture texture " + i + " failed!");
            }
            GLES20.glBindTexture(type, ids[i]);
            GLES20.glTexParameteri(type, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
            GLES20.glTexParameteri(type, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
            GLES20.glTexParameteri(type, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(type, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        }

        // 解绑 Texture
        GLES20.glBindTexture(type, 0);
        return ids;
    }

}
