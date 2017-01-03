package com.mlibrary.patch.hotpatch;

import android.text.TextUtils;

import com.mlibrary.patch.runtime.RuntimeArgs;
import com.mlibrary.patch.util.FileUtil;
import com.mlibrary.patch.util.LogUtil;
import com.mlibrary.patch.MDynamicLib;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class HotPatchManager {
    public static final String TAG = MDynamicLib.TAG + ":HotPatchItem";
    private static volatile HotPatchManager instance;
    private static SortedMap<Integer, HotPatchItem> sortedMap;

    private File patchDir;

    @SuppressWarnings("unchecked")
    private HotPatchManager() {
        File baseFile = RuntimeArgs.androidApplication.getFilesDir();
        patchDir = new File(baseFile, "hotpatch");
        sortedMap = new TreeMap(new Comparator<Integer>() {
            @Override
            public int compare(Integer lhs, Integer rhs) {
                return rhs.compareTo(lhs);
            }
        });
    }

    public static HotPatchManager getInstance() {
        if (instance == null) {
            synchronized (HotPatchManager.class) {
                if (instance == null)
                    instance = new HotPatchManager();
            }
        }
        return instance;
    }

    public void installHotFixDexs() {
        LogUtil.w(TAG, "installHotFixDexs start");
        long startTime = System.currentTimeMillis();
        try {
            initHotPatches();
            if (!sortedMap.isEmpty()) {
                for (Map.Entry<Integer, HotPatchItem> entry : sortedMap.entrySet()) {
                    HotPatchItem item = entry.getValue();
                    if (item != null && item.isPatchInstalled()) {
                        try {
                            item.installBundleDex();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        } catch (Throwable e) {
            LogUtil.e(TAG, "installHotFixDexs failure !", e);
        }
        LogUtil.w(TAG, "installHotFixDexs：end 总耗时: " + String.valueOf(System.currentTimeMillis() - startTime) + "ms");
    }

    /**
     * 安装补丁
     */
    @SuppressWarnings("unused")
    public boolean installHotPatch(String hotFixFileName, InputStream inputStream) {
        if (TextUtils.isEmpty(hotFixFileName) || inputStream == null)
            return false;
        boolean ret = true;
        if (!patchDir.exists())
            patchDir.mkdirs();
        try {
            int rst_index = hotFixFileName.lastIndexOf("_rst");
            boolean isUnstalled = false;
            if (rst_index >= 0) {
                String fileName = hotFixFileName.substring(0, rst_index);
                isUnstalled = uninstallHotPatch(fileName);
            }
            if (!isUnstalled) {
                int version = 1;
                if (!sortedMap.isEmpty())
                    version = sortedMap.firstKey() + 1;
                String storageFile = hotFixFileName + "_" + version;
                HotPatchItem hotPatchItem = new HotPatchItem(new File(patchDir, storageFile), inputStream);
                sortedMap.put(version, hotPatchItem);

                if (hotPatchItem.isPatchInstalled()) {
                    try {
                        hotPatchItem.installHotFixDex();
                    } catch (Exception e) {
                        e.printStackTrace();
                        ret = false;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            LogUtil.e(TAG, "installHotPatch error", e);
            ret = false;
        }
        return ret;
    }

    public void delete() {
        LogUtil.d(TAG, "delete:" + patchDir.getPath());
        if (patchDir.exists())
            FileUtil.deleteDirectory(patchDir);
    }

    private boolean uninstallHotPatch(String hotFixFileName) {
        if (!sortedMap.isEmpty()) {
            int key = 0;
            for (Map.Entry<Integer, HotPatchItem> entry : sortedMap.entrySet()) {
                HotPatchItem item = entry.getValue();
                if (item != null && item.getHotPatchId().toLowerCase().contains(hotFixFileName.toLowerCase())) {
                    item.purge();
                    key = entry.getKey();
                    break;
                }
            }
            if (key > 0) {
                sortedMap.remove(key);
                return true;
            }
        }
        return false;

    }

    private void initHotPatches() {
        if (!patchDir.exists()) {
            patchDir.mkdirs();
        }
        File[] listFiles = patchDir.listFiles();
        if (listFiles != null) {
            for (File file : listFiles) {
                if (file.isDirectory()) {
                    HotPatchItem hotPatchItem = new HotPatchItem(file);
                    try {
                        int separatorIndex = file.getName().lastIndexOf("_");
                        String filter = file.getName().substring(separatorIndex + 1, file.getName().length());
                        int version = Integer.parseInt(filter);
                        sortedMap.put(version, hotPatchItem);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    }
}
