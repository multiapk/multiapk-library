package com.mlibrary.patch.bundle.hotpatch;

import android.text.TextUtils;
import android.util.Log;

import com.mlibrary.patch.MDynamicLib;
import com.mlibrary.patch.bundle.BundleManager;
import com.mlibrary.patch.base.runtime.RuntimeArgs;
import com.mlibrary.patch.util.FileUtil;
import com.mlibrary.patch.util.LogUtil;
import com.mlibrary.util.bspatch.MBSPatchUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipFile;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class BundleHotPatch {
    private static final String TAG = MDynamicLib.TAG + ":BundleHotPatch";
    private static final String PATCH_SUFFIX = ".patch";

    public static void copyDownloadPatchToLocal(String packageName, File downloadPatchFile) throws Exception {
        if (downloadPatchFile == null || !downloadPatchFile.exists() || TextUtils.isEmpty(packageName))
            throw new IllegalStateException("arguments is unCorrect ! packageName:" + packageName);

        File hotPatchBaseDir = getHotPatchBaseDir(packageName);
        File hotPatchSyntheticBundleFile = getSyntheticBundle(hotPatchBaseDir, packageName);
        if (hotPatchSyntheticBundleFile.exists()) {
            hotPatchSyntheticBundleFile.delete();
            hotPatchSyntheticBundleFile.createNewFile();
        }
        File hotPatchDownloadDir = getHotPatchDownloadDir(hotPatchBaseDir);
        File hotPatchDownloadFile = getDownloadPatchFile(hotPatchDownloadDir, packageName);

        File baseBundleDir = new File(hotPatchBaseDir, "baseBundle");
        if (!baseBundleDir.exists())
            baseBundleDir.mkdirs();
        File baseBundleFile = new File(baseBundleDir, packageName + BundleManager.BUNDLE_SUFFIX);

        InputStream inputStream = null;
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(RuntimeArgs.androidApplication.getApplicationInfo().sourceDir);
            LogUtil.d(TAG, "copyDownloadPatchToLocal:sourceDir" + RuntimeArgs.androidApplication.getApplicationInfo().sourceDir);
            List<String> bundleList = BundleManager.getBundleList(zipFile, BundleManager.BUNDLE_LIB_PATH, BundleManager.BUNDLE_SUFFIX);
            if (bundleList.size() > 0) {
                for (String bundleItem : bundleList) {
                    LogUtil.d(TAG, "copyDownloadPatchToLocal:--------------------------------");
                    LogUtil.d(TAG, "copyDownloadPatchToLocal:bundleItem:" + bundleItem);
                    String packageNameFromEntryName = bundleItem.substring(bundleItem.indexOf(BundleManager.BUNDLE_LIB_PATH) + BundleManager.BUNDLE_LIB_PATH.length(), bundleItem.indexOf(BundleManager.BUNDLE_SUFFIX)).replace("_", ".");
                    LogUtil.d(TAG, "copyDownloadPatchToLocal:packageNameFromEntryName:" + packageNameFromEntryName);
                    if (packageNameFromEntryName.equals(packageName)) {
                        inputStream = zipFile.getInputStream(zipFile.getEntry(bundleItem));
                        FileUtil.copyInputStreamToFile(inputStream, baseBundleFile);
                        break;
                    }
                }
            } else {
                LogUtil.w(TAG, "find no bundles at " + BundleManager.BUNDLE_LIB_PATH);
            }
        } catch (Exception e) {
            Log.e(TAG, "zip exception", e);
        } finally {
            try {
                if (zipFile != null)
                    zipFile.close();
            } catch (Exception e) {
                Log.e(TAG, "zip close failure", e);
            }
        }
        Log.e(TAG, "copyDownloadPatchToLocal:inputStream==null?" + (inputStream == null));

        if (!baseBundleFile.exists())
            throw new IllegalStateException("baseBundle is null ! packageName:" + packageName);
        //delete(packageName);

        //add new
        FileUtil.copyInputStreamToFile(new FileInputStream(downloadPatchFile), hotPatchDownloadFile);
        Log.w(TAG, "合成差分包 start ****************************************");
        Log.d(TAG, "合成前 baseBundleFile.exists?" + baseBundleFile.exists());
        Log.d(TAG, "合成前 downloadPatchFile.exists?" + downloadPatchFile.exists());
        Log.d(TAG, "合成前 hotPatchSyntheticBundleFile.exists?" + hotPatchSyntheticBundleFile.exists());
        try {
            new MBSPatchUtil().bspatch(baseBundleFile.getPath(), hotPatchSyntheticBundleFile.getPath(), downloadPatchFile.getPath());
        } catch (Exception e) {
            Log.e(TAG, "合成差分包失败", e);
        }
        Log.d(TAG, "合成后 baseBundleFile.exists?" + baseBundleFile.exists());
        Log.d(TAG, "合成后 downloadPatchFile.exists?" + downloadPatchFile.exists());
        Log.d(TAG, "合成后 hotPatchSyntheticBundleFile.exists?" + hotPatchSyntheticBundleFile.exists());
        Log.w(TAG, "合成差分包 end   ****************************************");
    }

    public static File getHotPatchBaseDir(String packageName) {
        File hotPatchBaseDir = new File(BundleManager.getInstance().getBundlesDir() + packageName + "/hotPatch");
        if (!hotPatchBaseDir.exists())
            hotPatchBaseDir.mkdirs();
        return hotPatchBaseDir;
    }

    public static File getHotPatchDownloadDir(File hotPatchBaseDir) {
        File hotPatchDownloadDir = new File(hotPatchBaseDir, "download");
        if (!hotPatchDownloadDir.exists())
            hotPatchDownloadDir.mkdirs();
        return hotPatchDownloadDir;
    }

    public static File getDownloadPatchFile(File hotPatchDownloadDir, String packageName) {
        return new File(hotPatchDownloadDir, packageName + PATCH_SUFFIX);
    }

    public static File getSyntheticBundle(File hotPatchBaseDir, String packageName) {
        return new File(hotPatchBaseDir, packageName + BundleManager.BUNDLE_SUFFIX);
    }

    public static File getSyntheticBundle(String packageName) {
        return new File(getHotPatchBaseDir(packageName), packageName + BundleManager.BUNDLE_SUFFIX);
    }

    public static void delete(String packageName) {
        FileUtil.deleteDirectory(getHotPatchBaseDir(packageName));
    }
}
