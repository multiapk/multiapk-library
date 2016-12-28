package com.mlibrary.patch.framework;

import android.app.Application;
import android.os.Build;
import android.os.Environment;

import com.mlibrary.patch.hack.AndroidHack;
import com.mlibrary.patch.hack.SysHacks;
import com.mlibrary.patch.runtime.ResourcesHook;
import com.mlibrary.patch.runtime.InstrumentationHook;
import com.mlibrary.patch.runtime.RuntimeArgs;
import com.mlibrary.patch.util.FileUtil;
import com.mlibrary.patch.util.LogUtil;
import com.mlibrary.patch.MLibraryPatch;
import com.mlibrary.patch.util.SdCardUtil;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BundleManager {
    public static final String TAG = MLibraryPatch.TAG + ":BundleManager";

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

    public void init(Application application, boolean isOpenLog) throws Exception {
        LogUtil.setDebugAble(isOpenLog);
        SysHacks.defineAndVerify();
        RuntimeArgs.androidApplication = application;
        RuntimeArgs.delegateResources = application.getResources();
        AndroidHack.injectInstrumentationHook(new InstrumentationHook(AndroidHack.getInstrumentation(), application.getBaseContext()));
    }

    public void run() {
        for (Bundle bundle : getBundles()) {
            try {
                bundle.optDexFile();
            } catch (Exception e) {
                LogUtil.e(TAG, "optDexFile exception", e);
            }
        }
        try {
            ResourcesHook.newResourcesHook(RuntimeArgs.androidApplication, RuntimeArgs.delegateResources);
        } catch (Exception e) {
            LogUtil.e(TAG, "DelegateResources.newResourcesHook exception", e);
        }
    }

    public Bundle installBundle(String location, InputStream inputStream) throws Exception {
        return installNewBundle(location, inputStream);
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


    public void startup(boolean needReInitBundle) {
        LogUtil.e(TAG, "*------------------------------------*");
        //noinspection deprecation
        LogUtil.e(TAG, " Ctrip Bundle on " + Build.MODEL + "|starting...");
        LogUtil.e(TAG, "*------------------------------------*");

        long currentTimeMillis = System.currentTimeMillis();
        String baseDir = null;
        //String baseDir = RuntimeArgs.androidApplication.getFilesDir().getAbsolutePath();
        LogUtil.w(TAG, "storageLocation:SdCard exists?" + SdCardUtil.isSdCardExist());
        File externalFile = RuntimeArgs.androidApplication.getExternalFilesDir(null);
        if (externalFile != null) {
            baseDir = externalFile.getAbsolutePath();
            LogUtil.w(TAG, "storageLocation:externalFile!=null:baseDir" + baseDir);
        }
        LogUtil.w(TAG, "storageLocation:sfcard cache dir" + RuntimeArgs.androidApplication.getExternalCacheDir());
        if (baseDir == null) {
            baseDir = RuntimeArgs.androidApplication.getFilesDir().getAbsolutePath();
            LogUtil.w(TAG, "storageLocation:baseDir==null:baseDir" + baseDir);
            baseDir = new File(Environment.getExternalStorageDirectory(), "files").getAbsolutePath();
            LogUtil.w(TAG, "storageLocation:baseDir==null:baseDir" + baseDir);
        }
        storageLocation = baseDir + File.separatorChar + "storage" + File.separatorChar;
        LogUtil.w(TAG, "storageLocation:" + storageLocation);
        if (needReInitBundle) {
            LogUtil.w(TAG, "重新初始化,即将删除:" + storageLocation);
            File file = new File(storageLocation);
            if (file.exists())
                FileUtil.deleteDirectory(file);
            //noinspection ResultOfMethodCallIgnored
            file.mkdirs();
            saveToProfile();
        } else {
            restoreFromProfile();
        }
        LogUtil.e(TAG, "*------------------------------------*");
        LogUtil.e(TAG, " Framework " + (needReInitBundle ? "restarted" : "start") + " in " + (System.currentTimeMillis() - currentTimeMillis) + " ms");
        LogUtil.e(TAG, "*------------------------------------*");
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
                            LogUtil.e(TAG, "RESTORED BUNDLE " + bundle.getLocation());
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

    public Bundle installNewBundle(String location, InputStream inputStream) throws Exception {
        LogUtil.d(TAG, "installNewBundle: " + location);
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
}
