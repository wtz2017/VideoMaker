package com.wtz.libvideomaker.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import java.lang.reflect.Method;

public class ScreenUtils {

    public static boolean isPortrait(Context context) {
        return context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT;
    }

    /**
     * 获取手机屏幕分辨率
     *
     * @return 手机屏幕分辨率
     */
    public static int[] getScreenPixels(Context context) {
        if (context == null) {
            return new int[]{0, 0};
        }

        int widthPixels;
        int heightPixels;

        WindowManager wm = (WindowManager) (context.getSystemService(Context.WINDOW_SERVICE));
        Display display = wm.getDefaultDisplay();
        DisplayMetrics dm = new DisplayMetrics();
        display.getMetrics(dm);
        widthPixels = dm.widthPixels;
        heightPixels = dm.heightPixels;

        if (Build.VERSION.SDK_INT >= 17) {
            try {
                Method method = display.getClass().getMethod("getRealMetrics", DisplayMetrics.class);
                method.invoke(display, dm);
            } catch (Exception e) {
                e.printStackTrace();
            }
            heightPixels = dm.heightPixels;
        } else {
            try {
                Method method = display.getClass().getMethod("getRawHeight");
                heightPixels = (Integer) method.invoke(display);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return new int[]{widthPixels, heightPixels};
    }

    public static DisplayMetrics getDisplayMetrics(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(dm);
        return dm;
    }

    /**
     * 获取设计像素对应的dip值
     *
     * @param designPx 以 1080p,320dpi,density2.0 为基础 UI 设计的像素大小
     * @return
     */
    public static float getDipByDesignPx(int designPx) {
//        float scale = 3.0f;//1080p,480dpi,density3.0
        float scale = 2.0f;//1080p,320dpi,density2.0
        return designPx / scale;
    }

    public static int dip2px(Context context, float dip) {
        float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dip * scale + 0.5f);
    }

    /**
     * 获取状态栏高度
     * @param context
     * @return
     */
    public static int getStatusBarHeight(Context context) {
        int result = 0;
        if (context == null) return result;

        Resources resources = context.getResources();
        if (resources == null) return result;

        int resourceId = resources.getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId);
        }
        return result;
    }

}
