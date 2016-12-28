package com.mlibrary.patch.framework;

import com.mlibrary.patch.framework.storage.BundleArchive;
import com.mlibrary.patch.util.FileUtil;
import com.mlibrary.patch.util.LogUtil;
import com.mlibrary.patch.MLibraryPatch;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class Bundle {
    public static final String TAG = MLibraryPatch.TAG + ":Bundle";

    private final File bundleDir;
    private final String location;
    private final long bundleID;
    private BundleArchive archive;
    //是否dex优化
    private volatile boolean isOptimized;

    Bundle(File bundleDir) throws Exception {
        this.bundleDir = bundleDir;
        this.isOptimized = false;
        DataInputStream dataInputStream = new DataInputStream(new FileInputStream(new File(bundleDir, "meta")));
        this.bundleID = dataInputStream.readLong();
        this.location = dataInputStream.readUTF();
        dataInputStream.close();
        this.archive = new BundleArchive(bundleDir);
    }

    Bundle(File bundleDir, String location, long bundleID, InputStream inputStream) throws Exception {
        this.bundleDir = bundleDir;
        this.isOptimized = false;
        this.bundleID = bundleID;
        this.location = location;
        if (inputStream == null) {
            throw new NullPointerException("inputStream is null : " + location);
        } else {
            try {
                this.archive = new BundleArchive(bundleDir, inputStream);
            } catch (Exception e) {
                FileUtil.deleteDirectory(bundleDir);
                throw new IOException("Can not install bundle " + location, e);
            }
        }
        this.updateMetadata();
    }

    @SuppressWarnings("unused")
    public boolean isOptimized() {
        return this.isOptimized;
    }

    public BundleArchive getArchive() {
        return this.archive;
    }

    public long getBundleId() {
        return this.bundleID;
    }

    public String getLocation() {
        return this.location;
    }

    public synchronized void update(InputStream inputStream) throws IOException {
        this.archive.newRevision(this.bundleDir, inputStream);
    }

    synchronized void optDexFile() throws Exception {
        if (!isOptimized) {
            long startTime = System.currentTimeMillis();
            getArchive().optimizeDexFile();
            isOptimized = true;
            LogUtil.d(TAG, "执行：" + getLocation() + ",时间-----" + String.valueOf(System.currentTimeMillis() - startTime));
        }
    }

    public synchronized void purge() throws Exception {
        getArchive().purge();
    }

    void updateMetadata() {
        File file = new File(this.bundleDir, "meta");
        DataOutputStream dataOutputStream;
        try {
            if (!file.getParentFile().exists())
                //noinspection ResultOfMethodCallIgnored
                file.getParentFile().mkdirs();
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            dataOutputStream = new DataOutputStream(fileOutputStream);
            dataOutputStream.writeLong(this.bundleID);
            dataOutputStream.writeUTF(this.location);
            dataOutputStream.flush();
            fileOutputStream.getFD().sync();
            try {
                dataOutputStream.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        } catch (Throwable e) {
            LogUtil.e(TAG, "Could not save meta data " + file.getAbsolutePath(), e);
        }
    }

    public String toString() {
        return "Bundle [" + this.bundleID + "]: " + this.location;
    }
}
