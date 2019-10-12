/*
 * Copyright © 2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;

import com.wireguard.android.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class DownloadsFileSaver {

    public static class DownloadsFile {
        private Context context;
        private OutputStream outputStream;
        private String fileName;
        private Uri uri;

        private DownloadsFile(final Context context, final OutputStream outputStream, final String fileName, final Uri uri) {
            this.context = context;
            this.outputStream = outputStream;
            this.fileName = fileName;
            this.uri = uri;
        }

        public OutputStream getOutputStream() { return outputStream; }
        public String getFileName() { return fileName; }

        public void delete() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                context.getContentResolver().delete(uri, null, null);
            else
                new File(fileName).delete();
        }
    }

    public static DownloadsFile save(final Context context, final String name, final String mimeType, final boolean overwriteExisting) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            final ContentResolver contentResolver = context.getContentResolver();
            if (overwriteExisting)
                contentResolver.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI, String.format("%s = ?", MediaColumns.DISPLAY_NAME), new String[]{name});
            final ContentValues contentValues = new ContentValues();
            contentValues.put(MediaColumns.DISPLAY_NAME, name);
            contentValues.put(MediaColumns.MIME_TYPE, mimeType);
            final Uri contentUri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);
            if (contentUri == null)
                throw new IOException(context.getString(R.string.create_downloads_file_error));
            final OutputStream contentStream = contentResolver.openOutputStream(contentUri);
            if (contentStream == null)
                throw new IOException(context.getString(R.string.create_downloads_file_error));
            Cursor cursor = contentResolver.query(contentUri, new String[]{MediaColumns.DATA}, null, null, null);
            String path = null;
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst())
                        path = cursor.getString(0);
                } finally {
                    cursor.close();
                }
            }
            if (path == null) {
                path = "Download/";
                cursor = contentResolver.query(contentUri, new String[]{MediaColumns.DISPLAY_NAME}, null, null, null);
                if (cursor != null) {
                    try {
                        if (cursor.moveToFirst())
                            path += cursor.getString(0);
                    } finally {
                        cursor.close();
                    }
                }
            }
            return new DownloadsFile(context, contentStream, path, contentUri);
        } else {
            final File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            final File file = new File(path, name);
            if (!path.isDirectory() && !path.mkdirs())
                throw new IOException(context.getString(R.string.create_output_dir_error));
            return new DownloadsFile(context, new FileOutputStream(file), file.getAbsolutePath(), null);
        }
    }
}
