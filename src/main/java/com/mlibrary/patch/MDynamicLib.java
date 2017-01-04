package com.mlibrary.patch;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.mlibrary.patch.base.util.LogUtil;
import com.mlibrary.patch.base.util.PreferencesUtil;
import com.mlibrary.patch.bundle.BundleManager;

import java.io.File;

public class MDynamicLib {
    public static final String TAG = MDynamicLib.class.getName();
    public static String defaultActivityWhileClassNotFound = null;

    public static void init(Application application) {
        init(application, null, true);
    }

    public static void init(final Application application, String defaultActivityWhileClassNotFound, boolean isOpenLog) {
        MDynamicLib.defaultActivityWhileClassNotFound = defaultActivityWhileClassNotFound;
        BundleManager.instance.init(application, isOpenLog);
    }

    public static File getBaseDir(Application androidApplication) {
        //String baseDir = androidApplication.getFilesDir().getAbsolutePath();
        //为了方便调试，暂时优先放到SDCard
        File baseDir = androidApplication.getExternalFilesDir(null);
        if (baseDir == null)
            baseDir = androidApplication.getFilesDir();
        return baseDir;
    }

    public static String getCurrentBundleKey(Application application) {
        String bundleKey = null;
        try {
            PackageInfo packageInfo = application.getPackageManager().getPackageInfo(application.getPackageName(), PackageManager.GET_CONFIGURATIONS);
            bundleKey = "app.version." + String.valueOf(packageInfo.versionCode) + "." + packageInfo.versionName;
        } catch (Exception e) {
            e.printStackTrace();
        }
        LogUtil.w(TAG, "[get] currentBundleKey==" + bundleKey);
        return bundleKey;
    }

    private static final String KEY_LAST_BUNDLE = "KEY_LAST_BUNDLE_KEY";

    public static void saveBundleKey(Application application, String bundleKey) {
        LogUtil.w(TAG, "saveBundleKey:" + bundleKey);
        PreferencesUtil.getInstance(application).putString(KEY_LAST_BUNDLE, bundleKey);
    }

    public static String getLastBundleKey(Application application) {
        String lastBundleKey = PreferencesUtil.getInstance(application).getString(KEY_LAST_BUNDLE);
        LogUtil.w(TAG, "[get] lastBundleKey==" + lastBundleKey);
        return lastBundleKey;
    }
}