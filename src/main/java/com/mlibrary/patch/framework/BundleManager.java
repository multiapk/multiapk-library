package com.mlibrary.patch.framework;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import com.mlibrary.patch.MDynamicLib;
import com.mlibrary.patch.hack.AndroidHack;
import com.mlibrary.patch.hack.SysHacks;
import com.mlibrary.patch.hotpatch.HotPatchManager;
import com.mlibrary.patch.runtime.InstrumentationHook;
import com.mlibrary.patch.runtime.ResourcesHook;
import com.mlibrary.patch.runtime.RuntimeArgs;
import com.mlibrary.patch.util.FileUtil;
import com.mlibrary.patch.util.LogUtil;
import com.mlibrary.patch.util.PreferencesUtil;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class BundleManager {
    public static final String TAG = MDynamicLib.TAG + ":BundleManager";

    public static final String LIB_PATH = "assets/baseres/";

    private final Map<String, Bundle> bundles = new ConcurrentHashMap<>();
    private String storageLocation;
    private long nextBundleID = 1;

    private static BundleManager instance;

    private BundleManager() {
    }

    public static BundleManager getInstance() {
        if (instance == null) {
            synchronized (BundleManager.class) {
                if (instance == null)
                    instance = new BundleManager();
            }
        }
        return instance;
    }

    public void init(Application application, boolean isOpenLog) {
        LogUtil.setDebugAble(isOpenLog);
        LogUtil.w(TAG, "======================================================================");
        LogUtil.w(TAG, "******** init start **************************************************");
        LogUtil.w(TAG, "======================================================================");
        try {
            SysHacks.defineAndVerify();
            RuntimeArgs.androidApplication = application;
            RuntimeArgs.delegateResources = application.getResources();
            AndroidHack.injectInstrumentationHook(new InstrumentationHook(AndroidHack.getInstrumentation(), application.getBaseContext()));

            //init storageLocation start
            String baseDir = null;
            //String baseDir = RuntimeArgs.androidApplication.getFilesDir().getAbsolutePath();
            //为了方便调试，暂时优先放到SDCard
            File externalFile = RuntimeArgs.androidApplication.getExternalFilesDir(null);
            if (externalFile != null)
                baseDir = externalFile.getAbsolutePath();
            if (baseDir == null)
                baseDir = RuntimeArgs.androidApplication.getFilesDir().getAbsolutePath();
            storageLocation = baseDir + File.separatorChar + "storage" + File.separatorChar;
            LogUtil.w(TAG, "bundle location:" + storageLocation);
            //init storageLocation end

            checkStatus(application);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            LogUtil.w(TAG, "======================================================================");
            LogUtil.w(TAG, "******** init end ****************************************************");
            LogUtil.w(TAG, "======================================================================");
        }
    }

    private void checkStatus(final Application application) {
        if (!TextUtils.equals(getCurrentBundleKey(application), getLastBundleKey(application))) {
            LogUtil.d(TAG, "checkStatus: currentBundleKey != lastBundleKey , clean local and reCopyInstall bundles");
            new Thread(new Runnable() {
                @Override
                public void run() {
                    HotPatchManager.getInstance().purge();
                    cleanLocal();
                    copyBundles(application);
                    installBundleDexs();
                }
            }).start();
        } else {
            LogUtil.d(TAG, "checkStatus: currentBundleKey == lastBundleKey , restore from local hotfix and bundles");
            restoreFromProfile();
            HotPatchManager.getInstance().installHotFixDexs();
            installBundleDexs();
        }
    }

    public void cleanLocal() {
        LogUtil.w(TAG, "cleanLocal:" + storageLocation);
        File file = new File(storageLocation);
        if (file.exists())
            FileUtil.deleteDirectory(file);
        //noinspection ResultOfMethodCallIgnored
        file.mkdirs();
        saveToProfile();
    }

    public void installBundleDexs() {
        LogUtil.w(TAG, "installBundleDexs：start");
        long startTime = System.currentTimeMillis();
        for (Bundle bundle : getBundles()) {
            try {
                bundle.installBundleDexs();
            } catch (Exception e) {
                LogUtil.e(TAG, "installBundleDex exception", e);
            }
        }
        try {
            ResourcesHook.newResourcesHook(RuntimeArgs.androidApplication, RuntimeArgs.delegateResources);
        } catch (Exception e) {
            LogUtil.e(TAG, "DelegateResources.newResourcesHook exception", e);
        }
        LogUtil.w(TAG, "installBundleDexs：end 总耗时: " + String.valueOf(System.currentTimeMillis() - startTime) + "ms");
    }

    public List<String> getBundleList(ZipFile zipFile, String str, String str2) {
        List<String> arrayList = new ArrayList<>();
        Enumeration entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            String name = ((ZipEntry) entries.nextElement()).getName();
            if (name.startsWith(str) && name.endsWith(str2))
                arrayList.add(name);
        }
        return arrayList;
    }

    private static final String KEY_LAST_BUNDLE = "KEY_LAST_BUNDLE";

    private static void saveBundleKey(Application application, String bundleKey) {
        LogUtil.w(TAG, "saveBundleKey:" + bundleKey);
        PreferencesUtil.getInstance(application).putString(KEY_LAST_BUNDLE, bundleKey);
    }

    public String getLastBundleKey(Application application) {
        return PreferencesUtil.getInstance(application).getString(KEY_LAST_BUNDLE);
    }

    public void copyBundles(Application application) {
        LogUtil.w(TAG, "copyBundles:start");
        try {
            ZipFile zipFile = new ZipFile(application.getApplicationInfo().sourceDir);
            List<String> bundleFiles = getInstance().getBundleList(zipFile, LIB_PATH, ".so");
            if (bundleFiles.size() > 0) {
                getInstance().copyToLocal(zipFile, bundleFiles);
                saveBundleKey(application, getInstance().getCurrentBundleKey(application));
            } else {
                LogUtil.e(TAG, LIB_PATH + " 下没有发现任何 bundle");
            }
            try {
                zipFile.close();
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        LogUtil.w(TAG, "copyBundles:end");
    }

    public void copyToLocal(ZipFile zipFile, List<String> bundleList) {
        LogUtil.d(TAG, "copyToLocal:allBundles:" + getInstance().getBundles().toString());
        for (String bundleItem : bundleList) {
            String packageNameFromEntryName = bundleItem.substring(bundleItem.indexOf(LIB_PATH) + LIB_PATH.length(), bundleItem.indexOf(".so")).replace("_", ".");
            LogUtil.d(TAG, "bundleItem:" + bundleItem + " ,packageNameFromEntryName:" + packageNameFromEntryName);
            if (getInstance().getBundle(packageNameFromEntryName) == null) {
                LogUtil.w(TAG, "bundleItem 尚未被安装过，开始安装:" + packageNameFromEntryName);
                try {
                    getInstance().copyToLocal(packageNameFromEntryName, zipFile.getInputStream(zipFile.getEntry(bundleItem)));
                    LogUtil.d(TAG, "成功安装 bundleItem:" + packageNameFromEntryName);
                } catch (Exception exception) {
                    LogUtil.e(TAG, "无法安装 bundleItem:", exception);
                }
            } else {
                LogUtil.e(TAG, "bundleItem 已经被安装过了:" + packageNameFromEntryName);
            }
        }
        LogUtil.d(TAG, "copyToLocal:allBundles:" + getInstance().getBundles().toString());
    }

    public Bundle copyToLocal(String location, InputStream inputStream) throws Exception {
        LogUtil.d(TAG, "copyToLocal: " + location);
        Bundle bundle = getBundle(location);
        if (bundle != null)
            return bundle;
        long bundleID = nextBundleID;
        nextBundleID = 1 + bundleID;
        bundle = new Bundle(new File(storageLocation, String.valueOf(bundleID)), location, bundleID, inputStream);
        bundles.put(bundle.getLocation(), bundle);
        saveToMetadata();
        return bundle;
    }

    public void updateBundle(String location, InputStream inputStream) throws Exception {
        Bundle bundle = getBundle(location);
        if (bundle != null) {
            bundle.update(inputStream);
            return;
        }
        throw new IllegalStateException("Could not update bundle " + location + ", because could not find it");
    }

    public void uninstallBundle(String location) throws Exception {
        Bundle bundle = getBundle(location);
        if (bundle != null)
            bundle.getArchive().purge();
    }

    public List<Bundle> getBundles() {
        List<Bundle> arrayList = new ArrayList<>(bundles.size());
        synchronized (bundles) {
            arrayList.addAll(bundles.values());
        }
        return arrayList;
    }

    public Bundle getBundle(String str) {
        return bundles.get(str);
    }

    public Bundle getBundle(long id) {
        synchronized (bundles) {
            for (Bundle bundle : bundles.values())
                if (bundle.getBundleId() == id)
                    return bundle;
            return null;
        }
    }

    private void saveToProfile() {
        LogUtil.i(TAG, "saveToProfile");
        //noinspection SuspiciousToArrayCall
        Bundle[] bundleArray = getBundles().toArray(new Bundle[bundles.size()]);
        for (Bundle bundle : bundleArray)
            bundle.updateMetadata();
        saveToMetadata();
    }

    private void saveToMetadata() {
        LogUtil.i(TAG, "saveToMetadata:" + storageLocation + "/meta" + "  writeLong(nextBundleID):" + nextBundleID);
        try {
            DataOutputStream dataOutputStream = new DataOutputStream(new FileOutputStream(new File(storageLocation, "meta")));
            dataOutputStream.writeLong(nextBundleID);
            dataOutputStream.flush();
            dataOutputStream.close();
        } catch (Throwable e) {
            LogUtil.e(TAG, "Could not save meta data.", e);
        }
    }

    private int restoreFromProfile() {
        LogUtil.i(TAG, "restoreFromProfile");
        try {
            File file = new File(storageLocation, "meta");
            if (file.exists()) {
                DataInputStream dataInputStream = new DataInputStream(new FileInputStream(file));
                nextBundleID = dataInputStream.readLong();
                dataInputStream.close();
                File file2 = new File(storageLocation);
                File[] listFiles = file2.listFiles();
                int i = 0;
                while (i < listFiles.length) {
                    if (listFiles[i].isDirectory() && new File(listFiles[i], "meta").exists()) {
                        try {
                            Bundle bundle = new Bundle(listFiles[i]);
                            bundles.put(bundle.getLocation(), bundle);
                            LogUtil.w(TAG, "restored bundle " + bundle.getLocation());
                        } catch (Exception e) {
                            LogUtil.e(TAG, e.getMessage(), e.getCause());
                        }
                    }
                    i++;
                }
                return 1;
            }
            LogUtil.e(TAG, "Profile not found, performing clean start ...");
            return -1;
        } catch (Exception e2) {
            e2.printStackTrace();
            return 0;
        }
    }

    public String getCurrentBundleKey(Application application) {
        String bundleKey = null;
        try {
            PackageInfo packageInfo = application.getPackageManager().getPackageInfo(application.getPackageName(), PackageManager.GET_CONFIGURATIONS);
            bundleKey = String.valueOf(packageInfo.versionCode) + "_" + packageInfo.versionName;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bundleKey;
    }
}
