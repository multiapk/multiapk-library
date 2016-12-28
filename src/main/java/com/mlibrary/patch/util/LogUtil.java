package com.mlibrary.patch.util;

import android.util.Log;

public class LogUtil {
    private static boolean isDebugging = false;

    public static void setDebugAble(boolean isDebug) {
        isDebugging = isDebug;
    }

    public static void v(String tag, String msg) {
        v(tag, msg, null);
    }

    public static void v(String tag, String msg, Throwable tr) {
        if (isDebugging) {
            Log.v(tag, msg, tr);
        }
    }

    public static void d(String tag, String msg) {
        d(tag, msg, null);
    }

    public static void d(String tag, String msg, Throwable tr) {
        if (isDebugging) {
            Log.d(tag, msg, tr);
        }
    }

    public static void i(String tag, String msg) {
        i(tag, msg, null);
    }

    public static void i(String tag, String msg, Throwable tr) {
        if (isDebugging) {
            Log.i(tag, msg, tr);
        }
    }

    public static void w(String tag, String msg) {
        w(tag, msg, null);
    }

    public static void w(String tag, String msg, Throwable tr) {
        if (isDebugging) {
            Log.w(tag, msg, tr);
        }
    }

    public static void e(String tag, String msg) {
        e(tag, msg, null);
    }

    public static void e(String tag, String msg, Throwable tr) {
        if (isDebugging) {
            Log.e(tag, msg, tr);
        }
    }
}
