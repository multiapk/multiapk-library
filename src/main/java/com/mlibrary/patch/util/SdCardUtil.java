package com.mlibrary.patch.util;

import android.os.Environment;
import android.os.StatFs;

import java.io.File;

public class SdCardUtil {
    public static boolean isSdCardExist() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    public static String getSdCardPath() {
        if (isSdCardExist())
            return Environment.getExternalStorageDirectory().getAbsolutePath();
        return null;
    }

    @SuppressWarnings("deprecation")
    public static long[] getSDCardMemory() {
        long[] sdCardInfo = new long[2];
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            File sdcardDir = Environment.getExternalStorageDirectory();
            StatFs sf = new StatFs(sdcardDir.getPath());
            long bSize = sf.getBlockSize();
            long bCount = sf.getBlockCount();
            long availBlocks = sf.getAvailableBlocks();

            sdCardInfo[0] = bSize * bCount;//总大小
            sdCardInfo[1] = bSize * availBlocks;//可用大小
        }
        return sdCardInfo;
    }
}
