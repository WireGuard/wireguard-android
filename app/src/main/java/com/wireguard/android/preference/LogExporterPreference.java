/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.preference;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Environment;
import androidx.annotation.Nullable;
import com.google.android.material.snackbar.Snackbar;
import androidx.preference.Preference;
import android.util.AttributeSet;
import android.util.Log;

import com.wireguard.android.Application;
import com.wireguard.android.R;
import com.wireguard.android.util.ErrorMessages;
import com.wireguard.android.util.FragmentUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Preference implementing a button that asynchronously exports logs.
 */

public class LogExporterPreference extends Preference {
    private static final String TAG = "WireGuard/" + LogExporterPreference.class.getSimpleName();

    @Nullable private String exportedFilePath;

    public LogExporterPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    private void exportLog() {
        Application.getAsyncWorker().supplyAsync(() -> {
            final File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            final File file = new File(path, "wireguard-log.txt");
            if (!path.isDirectory() && !path.mkdirs())
                throw new IOException(
                        getContext().getString(R.string.create_output_dir_error));

            /* We would like to simply run `builder.redirectOutput(file);`, but this is API 26.
             * Instead we have to do this dance, since logcat appends.
             */
            new FileOutputStream(file).close();

            try {
                final Process process = Runtime.getRuntime().exec(new String[]{
                        "logcat", "-b", "all", "-d", "-v", "threadtime", "-f", file.getAbsolutePath(), "*:V"});
                if (process.waitFor() != 0) {
                    try (final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                        final StringBuilder errors = new StringBuilder();
                        errors.append("Unable to run logcat: ");
                        String line;
                        while ((line = reader.readLine()) != null)
                            errors.append(line);
                        throw new Exception(errors.toString());
                    }
                }
            } catch (final Exception e) {
                // noinspection ResultOfMethodCallIgnored
                file.delete();
                throw e;
            }
            return file.getAbsolutePath();
        }).whenComplete(this::exportLogComplete);
    }

    private void exportLogComplete(final String filePath, @Nullable final Throwable throwable) {
        if (throwable != null) {
            final String error = ErrorMessages.get(throwable);
            final String message = getContext().getString(R.string.log_export_error, error);
            Log.e(TAG, message, throwable);
            Snackbar.make(
                    FragmentUtils.getPrefActivity(this).findViewById(android.R.id.content),
                    message, Snackbar.LENGTH_LONG).show();
            setEnabled(true);
        } else {
            exportedFilePath = filePath;
            notifyChanged();
        }
    }

    @Override
    public CharSequence getSummary() {
        return exportedFilePath == null ?
                getContext().getString(R.string.log_export_summary) :
                getContext().getString(R.string.log_export_success, exportedFilePath);
    }

    @Override
    public CharSequence getTitle() {
        return getContext().getString(R.string.log_export_title);
    }

    @Override
    protected void onClick() {
        FragmentUtils.getPrefActivity(this).ensurePermissions(
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                (permissions, granted) -> {
                    if (granted.length > 0 && granted[0] == PackageManager.PERMISSION_GRANTED) {
                        setEnabled(false);
                        exportLog();
                    }
                });
    }

}
