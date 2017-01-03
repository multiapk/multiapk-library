package com.mlibrary.patch.util;


import com.mlibrary.patch.MDynamicLib;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class FileUtil {
    private static final String TAG = MDynamicLib.TAG + ":FileUtil";

    public static void deleteDirectory(File file) {
        try {
            if (file != null) {
                File[] childFiles = file.listFiles();
                if (file.isDirectory() && childFiles != null && childFiles.length > 0) {
                    for (File childFile : childFiles)
                        deleteDirectory(childFile);
                }
                //noinspection ResultOfMethodCallIgnored
                file.delete();
                LogUtil.d(TAG, file.getPath() + " is deleted !");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void copyInputStreamToFile(InputStream inputStream, File file) throws IOException {
        FileChannel fileChannel = null;
        FileOutputStream fileOutputStream = null;
        try {
            LogUtil.d(TAG, "copyInputStreamToFile:" + file.getPath());
            fileOutputStream = new FileOutputStream(file);
            fileChannel = fileOutputStream.getChannel();
            byte[] buffer = new byte[1024];
            while (true) {
                int read = inputStream.read(buffer);
                if (read <= 0)
                    break;
                fileChannel.write(ByteBuffer.wrap(buffer, 0, read));
            }
        } finally {
            try {
                if (inputStream != null)
                    inputStream.close();
                if (fileChannel != null)
                    fileChannel.close();
                if (fileOutputStream != null)
                    fileOutputStream.close();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}