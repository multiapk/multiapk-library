package com.mlibrary.patch;

import android.app.Application;

import com.mlibrary.patch.bundle.BundleManager;

public class MDynamicLib {
    public static final String TAG = MDynamicLib.class.getName();
    public static String defaultActivityWhileClassNotFound = null;

    public static void init(Application application) {
        init(application, null, true);
    }

    public static void init(final Application application, String defaultActivityWhileClassNotFound, boolean isOpenLog) {
        MDynamicLib.defaultActivityWhileClassNotFound = defaultActivityWhileClassNotFound;
        BundleManager.getInstance().init(application, isOpenLog);
    }
}