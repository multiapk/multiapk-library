package com.mlibrary.patch.framework.storage;


import android.text.TextUtils;

import com.mlibrary.patch.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Created by yb.wang on 14/12/31.
 * Bundle 目录结构：version_1,version_2
 */
public class BundleArchive {
    private static final String REVISION_DIRECTORY = "version";
    private static final Long BEGIN_VERSION = 1L;
    private final BundleArchiveRevision currentRevision;
    private final SortedMap<Long, BundleArchiveRevision> revisionSortedMap;

    public BundleArchive(File bundleDir) throws IOException {
        this.revisionSortedMap = new TreeMap<>();
        String[] lists = bundleDir.list();
        if (lists != null) {
            for (String str : lists) {
                if (str.startsWith(REVISION_DIRECTORY)) {
                    long parseLong = Long.parseLong(subStringAfter(str, "_"));
                    if (parseLong > 0)
                        this.revisionSortedMap.put(parseLong, null);
                }
            }
        }
        if (revisionSortedMap.isEmpty())
            throw new IOException("No Valid revisions in bundle archive directory");
        long longValue = this.revisionSortedMap.lastKey();
        BundleArchiveRevision bundleArchiveRevision = new BundleArchiveRevision(longValue, new File(bundleDir, REVISION_DIRECTORY + "_" + String.valueOf(longValue)));
        this.revisionSortedMap.put(longValue, bundleArchiveRevision);
        this.currentRevision = bundleArchiveRevision;
    }

    public BundleArchive(File file, InputStream inputStream) throws IOException {
        this.revisionSortedMap = new TreeMap<>();
        BundleArchiveRevision bundleArchiveRevision = new BundleArchiveRevision(BEGIN_VERSION, new File(file, REVISION_DIRECTORY + "_" + String.valueOf(BEGIN_VERSION)), inputStream);
        this.revisionSortedMap.put(BEGIN_VERSION, bundleArchiveRevision);
        this.currentRevision = bundleArchiveRevision;
    }

    public BundleArchiveRevision newRevision(File storageFile, InputStream inputStream) throws IOException {
        long version = this.revisionSortedMap.lastKey() + 1;
        BundleArchiveRevision bundleArchiveRevision = new BundleArchiveRevision(version, new File(storageFile, REVISION_DIRECTORY + "_" + String.valueOf(version)), inputStream);
        this.revisionSortedMap.put(version, bundleArchiveRevision);
        return bundleArchiveRevision;
    }

    public File getArchiveFile() {
        return this.currentRevision.getRevisionFile();
    }

    public void optimizeDexFile() throws Exception {
        this.currentRevision.optimizeDexFile();
    }

    public void purge() throws Exception {
        FileUtil.deleteDirectory(this.currentRevision.getRevisionDir());
        long lastKey = this.revisionSortedMap.lastKey();
        this.revisionSortedMap.clear();
        if (lastKey < 1)
            this.revisionSortedMap.put(0L, this.currentRevision);
        else
            this.revisionSortedMap.put(lastKey - 1, this.currentRevision);
    }

    private static String subStringAfter(String source, String prefix) {
        if (TextUtils.isEmpty(source) || prefix == null)
            return source;
        int indexOf = source.indexOf(prefix);
        return indexOf != -1 ? source.substring(indexOf + prefix.length()) : "";
    }
}
