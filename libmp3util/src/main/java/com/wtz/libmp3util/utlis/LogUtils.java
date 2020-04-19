package com.wtz.libmp3util.utlis;

import android.util.Log;

public class LogUtils {

    public static boolean canPrintLog = true;

    public static void v(String tag, String msg) {
        if (canPrintLog) {
            Log.v(tag, msg);
        }
    }

    public static void d(String tag, String msg) {
        if (canPrintLog) {
            Log.d(tag, msg);
        }
    }

    public static void i(String tag, String msg) {
        if (canPrintLog) {
            Log.i(tag, msg);
        }
    }

    public static void w(String tag, String msg) {
        if (canPrintLog) {
            Log.w(tag, msg);
        }
    }

    public static void e(String tag, String msg) {
        if (canPrintLog) {
            Log.e(tag, msg);
        }
    }

    public static void printStackTrace(Throwable e) {
        if (canPrintLog && e != null) {
            e.printStackTrace();
        }
    }

}
