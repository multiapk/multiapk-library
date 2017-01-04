package com.mlibrary.patch.hotpatch;

import com.mlibrary.patch.MDynamicLib;
import com.mlibrary.patch.base.runtime.RuntimeArgs;
import com.mlibrary.patch.base.util.FileUtil;
import com.mlibrary.patch.base.util.LogUtil;
import com.mlibrary.patch.bundle.BundleManager;
import com.mlibrary.util.bspatch.MBSPatchUtil;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
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
          ........./app.version.1.1(bundleKey)/
          ......................../com.mctrip.modules.device.ios/
          ......................................./patch.version.1
          ......................................................./com.mctrip.modules.device.ios.patch
          ......................................................./synthetic
          ................................................................/com.mctrip.modules.device.ios.so
          ......................................./patch.version.2
          ......................................................./com.mctrip.modules.device.ios.patch
          ......................................................./com.mctrip.modules.device.ios.so
          ........./2_2(bundleKey)/

 */
@SuppressWarnings("ResultOfMethodCallIgnored")
public enum Hotpatch {
    instance;

    private final String TAG = Hotpatch.class.getName();
    private final String baseDirName = "hotpatch";
    private final String suffix_patch = ".patch";
    private final String split_flag = "_";
    private final String suffix_bundle = BundleManager.suffix;
    private File baseDir = null;
    private boolean isInitSuccess = false;

    Hotpatch() {
        baseDir = new File(MDynamicLib.getBaseDir(RuntimeArgs.androidApplication), baseDirName);
        if (!baseDir.exists())
            isInitSuccess = baseDir.mkdirs();
        //todo check 自检是否存在未合并的差分包，启动合并，下次启动生效
        LogUtil.w(TAG, "isInitSuccess:" + isInitSuccess);
    }

    public List<File> getModulesList(String bundleKey) {
        List<File> bundleDirList = new ArrayList<>();
        File tmpDir = new File(baseDir, bundleKey);
        ///hotpatch/1_1/com.mctrip.modules.device.android
        if (tmpDir.exists() && tmpDir.isDirectory())
            bundleDirList = Arrays.asList(tmpDir.listFiles());
        return bundleDirList;
    }

    public List<File> getModuleFileList(String bundleKey, String packageName) {
        List<File> moduleFileList = new ArrayList<>();
        List<File> modulesList = getModulesList(bundleKey);
        for (File moduleDir : modulesList) {
            ///hotpatch/1_1/com.mctrip.modules.device.android/com.mctrip.modules.device.android_1.so
            ///hotpatch/1_1/com.mctrip.modules.device.android/com.mctrip.modules.device.android_1.patch
            if (moduleDir.getName().equals(packageName)) {
                moduleFileList = Arrays.asList(moduleDir.listFiles());
                break;
            }
        }
        return moduleFileList;
    }

    public List<File> getModuleBundleFileList(String bundleKey, String packageName) {
        List<File> moduleBundleFileList = new ArrayList<>();
        List<File> modulesList = getModulesList(bundleKey);
        for (File moduleDir : modulesList) {
            ///hotpatch/1_1/com.mctrip.modules.device.android/com.mctrip.modules.device.android_1.so
            if (moduleDir.getName().equals(packageName)) {
                moduleBundleFileList = Arrays.asList(moduleDir.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return name.endsWith(suffix_bundle) && name.contains(split_flag);
                    }
                }));
                break;
            }
        }
        return moduleBundleFileList;
    }

    ///hotpatch/1_1/com.mctrip.modules.device.android/com.mctrip.modules.device.android_1.patch
    public void installPatch(String packageName, int patchVersion, File sourceFile) throws IOException {
        String bundleKey = MDynamicLib.getCurrentBundleKey(RuntimeArgs.androidApplication);
        File patchDir = new File(baseDir, bundleKey + File.separator + packageName);
        if (!patchDir.exists())
            patchDir.mkdirs();
        File downloadPatchFile = new File(patchDir, packageName + "_" + patchVersion + suffix_patch);

        if (downloadPatchFile.exists()) {
            LogUtil.w(TAG, "downloadPatchFile had exists!");
            return;
        }

        FileUtil.fileChannelCopy(sourceFile, downloadPatchFile);

        File baseBundleFile = BundleManager.instance.getBaseBundleFile(packageName);//从 apk 已经拷贝到本地的 so
        if (!baseBundleFile.exists()) {//使用 apk 里面的 so
            ZipFile zipFile = null;
            try {
                zipFile = new ZipFile(RuntimeArgs.androidApplication.getApplicationInfo().sourceDir);
                for (String bundleBasePath : BundleManager.getPathListByFilter(zipFile, BundleManager.bundleLibPath, BundleManager.suffix)) {
                    String bundlePackageName = bundleBasePath.substring(bundleBasePath.indexOf(BundleManager.bundleLibPath) + BundleManager.bundleLibPath.length(), bundleBasePath.indexOf(BundleManager.suffix)).replace("_", ".");
                    LogUtil.d(TAG, "bundleBasePath:" + bundleBasePath + ", packageName:" + packageName);
                    if (bundlePackageName.equals(packageName)) {
                        FileUtil.copyInputStreamToFile(zipFile.getInputStream(zipFile.getEntry(bundleBasePath)), baseBundleFile);
                        break;
                    }
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
        }
        if (!baseBundleFile.exists()) {
            throw new IOException("baseBundleFile not exists!");
        }

        File syntheticBundleFile = new File(downloadPatchFile.getPath().replaceAll(suffix_patch, suffix_bundle));
        if (syntheticBundleFile.exists()) {
            LogUtil.w(TAG, "syntheticBundleFile had exists!");
            return;
        }

        LogUtil.w(TAG, "合成差分包 start ****************************************");
        LogUtil.d(TAG, "合成前 baseBundleFile.exists?" + baseBundleFile.exists());
        LogUtil.d(TAG, "合成前 patchFile.exists?" + downloadPatchFile.exists());
        LogUtil.d(TAG, "合成前 syntheticBundleFile.exists?" + syntheticBundleFile.exists());
        try {
            MBSPatchUtil.bspatch(baseBundleFile.getPath(), syntheticBundleFile.getPath(), downloadPatchFile.getPath());
        } catch (Exception e) {
            LogUtil.e(TAG, "合成差分包失败", e);
        }
        LogUtil.d(TAG, "合成后 baseBundleFile.exists?" + baseBundleFile.exists());
        LogUtil.d(TAG, "合成后 downloadPatchFile.exists?" + downloadPatchFile.exists());
        LogUtil.d(TAG, "合成后 syntheticBundleFile.exists?" + syntheticBundleFile.exists());
        LogUtil.w(TAG, "合成差分包 end   ****************************************");
    }

    public File getLatestBundleFile(String packageName) {
        return getLatestBundleFile(MDynamicLib.getCurrentBundleKey(RuntimeArgs.androidApplication), packageName);
    }

    public File getLatestBundleFile(String bundleKey, String packageName) {
        List<File> moduleBundleFileList = getModuleBundleFileList(bundleKey, packageName);
        SortedSet<File> moduleBundleFileSet = new TreeSet<>(new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                String o1Version = o1.getName().split(split_flag)[1];
                String o2Version = o2.getName().split(split_flag)[1];
                try {
                    int _o1Version = Integer.parseInt(o1Version);
                    int _o2Version = Integer.parseInt(o2Version);
                    return _o1Version - _o2Version;
                } catch (NumberFormatException e) {
                    LogUtil.e(TAG, "patch version parse error!", e);
                }
                return 0;
            }
        });
        moduleBundleFileSet.addAll(moduleBundleFileList);
        File latestBundleFile = null;
        if (!moduleBundleFileSet.isEmpty())
            latestBundleFile = moduleBundleFileSet.last();
        LogUtil.w(TAG, "latest value:" + (latestBundleFile == null ? "null" : latestBundleFile.getPath()));
        return latestBundleFile;
    }
}