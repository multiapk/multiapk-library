package com.mlibrary.patch.util;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import com.mlibrary.patch.framework.BundleCore;
import com.mlibrary.patch.hotpatch.HotPatchManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class MLibraryPatchUtil {
    public static final String TAG = "MLibraryPatch";
    private static final String KEY_LAST_BUNDLE = "KEY_LAST_BUNDLE";

    public static String defaultActivityWhileClassNotFound = null;

    public static void init(final Application application) {
        init(application, null, true);
    }

    public static void init(final Application application, String defaultActivityWhileClassNotFound, boolean isOpenLog) {
        if (application == null)
            return;
        MLibraryPatchUtil.defaultActivityWhileClassNotFound = defaultActivityWhileClassNotFound;
        try {
            LogUtil.d(TAG, "开始初始化 hotfix/bundle");
            BundleCore.getInstance().init(application, isOpenLog);

            String lastBundleKey = PreferencesUtil.getInstance(application).getString(KEY_LAST_BUNDLE);
            final String currentBundleKey = buildBundleKey(application);
            LogUtil.d(TAG, "lastBundleKey:" + lastBundleKey + " ,currentBundleKey:" + currentBundleKey);

            //Properties properties = new Properties();
            //properties.put("ctrip.android.view", "ctrip.android.view.HomeActivity"); // launch page

            boolean needReInitBundle = false;
            if (!TextUtils.equals(currentBundleKey, lastBundleKey)) {
                needReInitBundle = true;
                //properties.put("ctrip.bundle.init", "true");
                LogUtil.d(TAG, "删除 热修复 所有补丁");
                HotPatchManager.getInstance().purge();//清除
            }
            BundleCore.getInstance().startup(needReInitBundle);
            LogUtil.d(TAG, BundleCore.LIB_PATH + " 下所有的 bundle(so/apk) 之前" + (!needReInitBundle ? "已经" : "尚未") + "被初始化过");
            //BundleCore.getInstance().startup(properties);
            if (!needReInitBundle) {
                LogUtil.d(TAG, "非初次:加载 热修复 所有补丁");
                HotPatchManager.getInstance().run();
                LogUtil.d(TAG, "非初次:加载 " + BundleCore.LIB_PATH + " 下所有的 bundle(so/apk)");
                BundleCore.getInstance().run();
            } else {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            ZipFile zipFile = new ZipFile(application.getApplicationInfo().sourceDir);
                            List<String> bundleFiles = getBundleList(zipFile, BundleCore.LIB_PATH, ".so");
                            if (bundleFiles.size() > 0) {
                                processLibsBundles(zipFile, bundleFiles);
                                LogUtil.d(TAG, "保存最新的 bundleKey:" + currentBundleKey);
                                PreferencesUtil.getInstance(application).putString(KEY_LAST_BUNDLE, currentBundleKey);
                            } else {
                                LogUtil.e(TAG, BundleCore.LIB_PATH + " 下没有发现任何 bundle");
                            }
                            try {
                                zipFile.close();
                            } catch (IOException exception) {
                                exception.printStackTrace();
                            }
                            LogUtil.d(TAG, "初次:加载 " + BundleCore.LIB_PATH + " 下所有的 bundle(so/apk)");
                            BundleCore.getInstance().run();
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }
                }).start();
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private static String buildBundleKey(Application application) throws PackageManager.NameNotFoundException {
        PackageInfo packageInfo = application.getPackageManager().getPackageInfo(application.getPackageName(), PackageManager.GET_CONFIGURATIONS);
        return String.valueOf(packageInfo.versionCode) + "_" + packageInfo.versionName;
    }

    private static List<String> getBundleList(ZipFile zipFile, String str, String str2) {
        List<String> arrayList = new ArrayList<>();
        Enumeration entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            String name = ((ZipEntry) entries.nextElement()).getName();
            if (name.startsWith(str) && name.endsWith(str2))
                arrayList.add(name);
        }
        return arrayList;
    }

    private static void processLibsBundles(ZipFile zipFile, List<String> bundleList) {
        LogUtil.d(TAG, "processLibsBundles:start");
        LogUtil.d(TAG, "processLibsBundles:allBundles:" + BundleCore.getInstance().getBundles().toString());
        for (String bundleItem : bundleList) {
            String packageNameFromEntryName = bundleItem.substring(bundleItem.indexOf(BundleCore.LIB_PATH) + BundleCore.LIB_PATH.length(), bundleItem.indexOf(".so")).replace("_", ".");
            LogUtil.d(TAG, "bundleItem:" + bundleItem + " ,packageNameFromEntryName:" + packageNameFromEntryName);
            if (BundleCore.getInstance().getBundle(packageNameFromEntryName) == null) {
                LogUtil.w(TAG, "bundleItem 尚未被安装过，开始安装:" + packageNameFromEntryName);
                try {
                    BundleCore.getInstance().installBundle(packageNameFromEntryName, zipFile.getInputStream(zipFile.getEntry(bundleItem)));
                    LogUtil.d(TAG, "成功安装 bundleItem:" + packageNameFromEntryName);
                } catch (Exception exception) {
                    LogUtil.e(TAG, "无法安装 bundleItem:", exception);
                }
            } else {
                LogUtil.e(TAG, "bundleItem 已经被安装过了:" + packageNameFromEntryName);
            }
        }
        LogUtil.d(TAG, "processLibsBundles:allBundles:" + BundleCore.getInstance().getBundles().toString());
        LogUtil.d(TAG, "processLibsBundles:end");
    }
}
