package com.wtz.libvideomaker.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.text.TextUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.IntBuffer;

public class GLBitmapUtils {
    private static String TAG = GLBitmapUtils.class.getSimpleName();

    public static int UNCONSTRAINED = -1;
    private static int APP_HEAP_GROWTH_LIMIT;
    private static float MAX_BITMAP_BYTES_IN_APP_RATIO = 0.0625f;
    private static float MIN_IMAGE_SIDE_LENGTH_RATIO = 0.95f;

    public static Bitmap saveGLPixels(int x, int y, int width, int height) {
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

    /**
     * @param resId      资源ID
     * @param showWidth  实际需要展示的宽
     * @param showHeight 实际需要展示的高
     * @return
     */
    public static Bitmap decodeResource(Context context, int resId, int showWidth, int showHeight) {
        if (showWidth <= 0 || showHeight <= 0) {
            LogUtils.e(TAG, "decodeResource params is error: showWidth=" + showWidth + " showHeight=" + showHeight);
            return null;
        }
        try {
            // Get image size
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeResource(context.getResources(), resId, opts);

            setBitmapOptions(context, showWidth, showHeight, opts);

            return BitmapFactory.decodeResource(context.getResources(), resId, opts);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * @param filePath   文件路径
     * @param showWidth  实际需要展示的宽
     * @param showHeight 实际需要展示的高
     * @return
     */
    public static Bitmap decodeFile(Context context, String filePath, int showWidth, int showHeight) {
        if (TextUtils.isEmpty(filePath) || showWidth <= 0 || showHeight <= 0) {
            LogUtils.e(TAG, "decodeFile params is error: filePath=" + filePath
                    + " showWidth=" + showWidth + " showHeight=" + showHeight);
            return null;
        }
        try {
            // Get image size
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(filePath, opts);

            setBitmapOptions(context, showWidth, showHeight, opts);

            return BitmapFactory.decodeFile(filePath, opts);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * @param opts 必须是已经设置了图像实际宽高的 Options
     */
    private static void setBitmapOptions(Context context, int showWidth, int showHeight, BitmapFactory.Options opts) {
        // Get inSampleSize
        int minSideLength = (int) (Math.min(showWidth, showHeight) * MIN_IMAGE_SIDE_LENGTH_RATIO);
        if (APP_HEAP_GROWTH_LIMIT <= 0) {
            ActivityManager activityManager =
                    (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            APP_HEAP_GROWTH_LIMIT = activityManager.getMemoryClass();
        }
        int maxBytes = (int) (MAX_BITMAP_BYTES_IN_APP_RATIO * APP_HEAP_GROWTH_LIMIT * 1024 * 1024);
        int maxPixels = Math.min(maxBytes / 4, showWidth * showHeight);
        opts.inSampleSize = computeSampleSize(opts.outWidth, opts.outHeight, minSideLength, maxPixels);

        // Decode image
        opts.inJustDecodeBounds = false;
        opts.inInputShareable = true;
        opts.inPurgeable = true;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
    }

    public static int computeSampleSize(int bmpWidth, int bmpHeight, int minSideLength,
                                        int maxNumOfPixels) {
        int initialSize = computeInitialSampleSize(bmpWidth, bmpHeight, minSideLength, maxNumOfPixels);
        int roundedSize;

        //  Rounds up the sample size to a power of 2 or multiple of 8
        //  because BitmapFactory only honors sample size this way.
        if (initialSize <= 8) {
            roundedSize = nextPowerOf2(initialSize);
        } else {
            roundedSize = (initialSize + 7) / 8 * 8;
        }
        StringBuilder builder = new StringBuilder();
        float finalBytes = 4.0f * bmpWidth / roundedSize * bmpHeight / roundedSize / 1024 / 1024;
        builder.append("Image=").append(bmpWidth).append("x").append(bmpHeight).append(" Sample=")
                .append(roundedSize).append(" FinalBytes=").append(finalBytes).append("MB")
                .append(" MinSide=").append(minSideLength).append(" MaxPixels=").append(maxNumOfPixels)
                .append(" APP_HEAP_GROWTH_LIMIT=").append(APP_HEAP_GROWTH_LIMIT).append("MB");
        LogUtils.w(TAG, builder.toString());
        return roundedSize;
    }

    private static int computeInitialSampleSize(int w, int h, int minSideLength, int maxNumOfPixels) {
        if (maxNumOfPixels == UNCONSTRAINED && minSideLength == UNCONSTRAINED) return 1;

        int sample1 = (maxNumOfPixels == UNCONSTRAINED) ? 1
                : (int) Math.ceil(Math.sqrt((double) (w * h) / maxNumOfPixels));
        if (minSideLength == UNCONSTRAINED) {
            return sample1;
        }

        int sample2 = Math.min(w / minSideLength, h / minSideLength);
        LogUtils.w(TAG, "computeInitialSampleSize sample1=" + sample1 + " sample2=" + sample2);
        return Math.max(sample2, sample1);
    }

    public static int nextPowerOf2(int value) {
        int result = 1;
        while (result < value) {
            result <<= 1;
        }
        return result;
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
