package com.mlibrary.patch.framework;

import com.mlibrary.patch.framework.storage.BundleArchive;
import com.mlibrary.patch.util.FileUtil;
import com.mlibrary.patch.util.LogUtil;
import com.mlibrary.patch.MDynamicLib;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class Bundle {
    public static final String TAG = MDynamicLib.TAG + ":Bundle";

    private final File bundleDir;
    private final String location;
    private BundleArchive archive;
    private volatile boolean isBundleDexInstalled;

    Bundle(File bundleDir) throws Exception {
        this.bundleDir = bundleDir;
        this.isBundleDexInstalled = false;
        DataInputStream dataInputStream = new DataInputStream(new FileInputStream(new File(bundleDir, "meta")));
        this.location = dataInputStream.readUTF();
        dataInputStream.close();
        this.archive = new BundleArchive(bundleDir);
    }

    Bundle(File bundleDir, String location, InputStream inputStream) throws Exception {
        this.bundleDir = bundleDir;
        this.isBundleDexInstalled = false;
        this.location = location;
        if (inputStream == null) {
            throw new NullPointerException("inputStream is null : " + location);
        } else {
            try {
                this.archive = new BundleArchive(bundleDir, inputStream);
            } catch (Exception e) {
                FileUtil.deleteDirectory(bundleDir);
                throw new IOException("can not install bundle " + location, e);
            }
        }
        this.updateMetadata();
    }

    public boolean isBundleDexInstalled() {
        return this.isBundleDexInstalled;
    }

    public BundleArchive getArchive() {
        return this.archive;
    }

    public String getLocation() {
        return this.location;
    }

    public synchronized void update(InputStream inputStream) throws IOException {
        this.archive.newRevision(this.bundleDir, inputStream);
    }

    synchronized void installBundleDexs() throws Exception {
        if (!isBundleDexInstalled) {
            long startTime = System.currentTimeMillis();
            getArchive().installBundleDex();
            isBundleDexInstalled = true;
            LogUtil.v(TAG, "installBundleDex：" + getLocation() + ", 耗时: " + String.valueOf(System.currentTimeMillis() - startTime) + "ms");
        }
    }

    public synchronized void purge() throws Exception {
        getArchive().clean();
    }

    void updateMetadata() {
        LogUtil.w(TAG, "updateMetadata start");
        File file = new File(this.bundleDir, "meta");
        DataOutputStream dataOutputStream;
        try {
            if (!file.getParentFile().exists())
                file.getParentFile().mkdirs();
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            dataOutputStream = new DataOutputStream(fileOutputStream);
            dataOutputStream.writeUTF(this.location);
            dataOutputStream.flush();
            fileOutputStream.getFD().sync();
            try {
                dataOutputStream.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        } catch (Throwable e) {
            LogUtil.e(TAG, "could not save meta data " + file.getAbsolutePath(), e);
        }
        LogUtil.w(TAG, "updateMetadata end");
    }

    public String toString() {
        return "\nbundle: " + this.location;
    }
}
