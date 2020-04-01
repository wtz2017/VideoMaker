package com.wtz.libvideomaker.utils;

import android.graphics.Bitmap;
import android.opengl.GLES20;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.IntBuffer;

public class GLBitmapUtils {

    public static Bitmap savePixels(int x, int y, int width, int height) {
        int originArray[] = new int[width * (y + height)];
        int resultArray[] = new int[width * height];
        IntBuffer originBuffer = IntBuffer.wrap(originArray);
        originBuffer.position(0);

        // 读取 OpenGL 像素
        GLES20.glReadPixels(x, 0, width, y + height,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, originBuffer);

        int originPixel;
        int pb;
        int pr;
        int resultPixel;
        for (int i = 0, k = 0; i < height; i++, k++) {
            // Remember, that OpenGL bitmap is incompatible with Android bitmap
            // and so, some correction need.
            for (int j = 0; j < width; j++) {
                // ABGR
                originPixel = originArray[i * width + j];
                pr = (originPixel << 16) & 0x00ff0000;
                pb = (originPixel >> 16) & 0xff;
                // ARGB
                resultPixel = (originPixel & 0xff00ff00) | pr | pb;
                // 整体像素上下翻转排列
                resultArray[(height - k - 1) * width + j] = resultPixel;
            }
        }

        Bitmap sb = Bitmap.createBitmap(resultArray, width, height, Bitmap.Config.ARGB_8888);
        return sb;
    }

    public static void saveBitmap(Bitmap bitmap, String pathName) {
        BufferedOutputStream bos = null;
        try {
            File target = new File(pathName);
            checkAndMkDirs(target.getParentFile());
            bos = new BufferedOutputStream(new FileOutputStream(target));
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
            bos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (bos != null) {
                try {
                    bos.close();
                } catch (IOException e) {
                }
            }
        }
    }

    public static boolean checkAndMkDirs(File folder) {
        if (folder == null) {
            return false;
        }

        return (folder.exists() && folder.isDirectory()) ? true : folder.mkdirs();
    }

}
