package com.mlibrary.patch.hotpatch;

import android.text.TextUtils;

import com.mlibrary.patch.base.runtime.RuntimeArgs;
import com.mlibrary.patch.base.util.FileUtil;
import com.mlibrary.patch.base.util.LogUtil;
import com.mlibrary.patch.bundle.BundleManager;
import com.mlibrary.util.bspatch.MBSPatchUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipFile;

/*
 上传参数: bundleKey: versionCode_versionName

 下发参数: {
              bundleKey: versionCode_versionName,
              patchList: [
                  {
                      patchUrl="https://www.ctrip.com/com.mctrip.modules.device.ios_1.patch",
                      packageName:"com.mctrip.modules.device.ios"
                      patchVersion:1
                      patchMd5:""
                      syntheticMd5:""
                  },
                  {
                      patchUrl="https://www.ctrip.com/com.mctrip.modules.device.android_1.patch",
                      packageName:"com.mctrip.modules.device.android"
                      patchVersion:1
                      patchMd5:""
                      syntheticMd5:""
                  },
              ]
          }
 本地目录: /hotpatch
          ........./1_1(bundleKey)/
          ......................../com.mctrip.modules.device.ios/
          ......................................./com.mctrip.modules.device.ios_1.patch
          ......................................./com.mctrip.modules.device.ios_1.so
          ......................................./com.mctrip.modules.device.ios_2.patch
          ......................................./com.mctrip.modules.device.ios_2.so
          ......................................./com.mctrip.modules.device.ios_3.patch
          ......................................./com.mctrip.modules.device.ios_3.so
          ......................../com.mctrip.modules.device.android/
          ......................................./com.mctrip.modules.device.android_1.patch
          ......................................./com.mctrip.modules.device.android_1.so
          ......................................./com.mctrip.modules.device.android_2.patch
          ......................................./com.mctrip.modules.device.android_2.so
          ......................................./com.mctrip.modules.device.android_3.patch
          ......................................./com.mctrip.modules.device.android_3.so
          ........./2_2(bundleKey)/
          ......................../com.mctrip.modules.device.ios/
          ......................................./com.mctrip.modules.device.ios_1.patch
          ......................................./com.mctrip.modules.device.ios_1.so
          ......................................./com.mctrip.modules.device.ios_2.patch
          ......................................./com.mctrip.modules.device.ios_2.so
          ......................................./com.mctrip.modules.device.ios_3.patch
          ......................................./com.mctrip.modules.device.ios_3.so

 */
@SuppressWarnings("ResultOfMethodCallIgnored")
public class BundleHotPatch {
    private static final String TAG = BundleHotPatch.class.getName();
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
        File baseBundleFile = new File(baseBundleDir, packageName + BundleManager.suffix_bundle_in_local);

        InputStream inputStream = null;
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(RuntimeArgs.androidApplication.getApplicationInfo().sourceDir);
            LogUtil.d(TAG, "sourceDir" + RuntimeArgs.androidApplication.getApplicationInfo().sourceDir);
            List<String> bundleList = BundleManager.getPathListByFilter(zipFile, BundleManager.bundleLibPath, BundleManager.suffix_bundle_in_local);
            if (bundleList.size() > 0) {
                for (String bundleItem : bundleList) {
                    LogUtil.d(TAG, "--------------------------------");
                    LogUtil.d(TAG, "bundleItem:" + bundleItem);
                    String packageNameFromEntryName = bundleItem.substring(bundleItem.indexOf(BundleManager.bundleLibPath) + BundleManager.bundleLibPath.length(), bundleItem.indexOf(BundleManager.suffix_bundle_in_local)).replace("_", ".");
                    LogUtil.d(TAG, "packageNameFromEntryName:" + packageNameFromEntryName);
                    if (packageNameFromEntryName.equals(packageName)) {
                        inputStream = zipFile.getInputStream(zipFile.getEntry(bundleItem));
                        FileUtil.copyInputStreamToFile(inputStream, baseBundleFile);
                        break;
                    }
                }
            } else {
                LogUtil.w(TAG, "find no bundles at " + BundleManager.bundleLibPath);
            }
        } catch (Exception e) {
            LogUtil.e(TAG, "zip exception", e);
        } finally {
            try {
                if (zipFile != null)
                    zipFile.close();
            } catch (Exception e) {
                LogUtil.e(TAG, "zip close failure", e);
            }
        }

        if (!baseBundleFile.exists())
            throw new IllegalStateException("baseBundle is null ! packageName:" + packageName);
        //delete(packageName);

        //add new
        FileUtil.copyInputStreamToFile(new FileInputStream(downloadPatchFile), hotPatchDownloadFile);
        LogUtil.w(TAG, "合成差分包 start ****************************************");
        LogUtil.d(TAG, "合成前 baseBundleFile.exists?" + baseBundleFile.exists());
        LogUtil.d(TAG, "合成前 downloadPatchFile.exists?" + downloadPatchFile.exists());
        LogUtil.d(TAG, "合成前 hotPatchSyntheticBundleFile.exists?" + hotPatchSyntheticBundleFile.exists());
        try {
            MBSPatchUtil.bspatch(baseBundleFile.getPath(), hotPatchSyntheticBundleFile.getPath(), downloadPatchFile.getPath());
        } catch (Exception e) {
            LogUtil.e(TAG, "合成差分包失败", e);
        }
        LogUtil.d(TAG, "合成后 baseBundleFile.exists?" + baseBundleFile.exists());
        LogUtil.d(TAG, "合成后 downloadPatchFile.exists?" + downloadPatchFile.exists());
        LogUtil.d(TAG, "合成后 hotPatchSyntheticBundleFile.exists?" + hotPatchSyntheticBundleFile.exists());
        LogUtil.w(TAG, "合成差分包 end   ****************************************");
    }

    public static File getHotPatchBaseDir(String packageName) {
        File hotPatchBaseDir = new File(BundleManager.instance.getBundlesDir(), packageName + "/hotPatch");
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
        return new File(hotPatchBaseDir, packageName + BundleManager.suffix_bundle_in_local);
    }

    public static File getSyntheticBundle(String packageName) {
        return new File(getHotPatchBaseDir(packageName), packageName + BundleManager.suffix_bundle_in_local);
    }

    public static void delete(String packageName) {
        FileUtil.deleteDirectory(getHotPatchBaseDir(packageName));
    }
}
