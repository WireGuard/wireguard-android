/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.preference;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import androidx.annotation.Nullable;
import com.google.android.material.snackbar.Snackbar;
import androidx.preference.Preference;

import android.util.AttributeSet;
import android.util.Log;

import com.wireguard.android.Application;
import com.wireguard.android.R;
import com.wireguard.android.util.DownloadsFileSaver;
import com.wireguard.android.util.DownloadsFileSaver.DownloadsFile;
import com.wireguard.android.util.ErrorMessages;
import com.wireguard.android.util.FragmentUtils;

import java.io.BufferedReader;
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
            DownloadsFile outputFile = DownloadsFileSaver.save(getContext(), "wireguard-log.txt", "text/plain", true);
            try {
                final Process process = Runtime.getRuntime().exec(new String[]{
                        "logcat", "-b", "all", "-d", "-v", "threadtime", "*:V"});
                try (final BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
                     final BufferedReader stderr = new BufferedReader(new InputStreamReader(process.getErrorStream())))
                {
                    String line;
                    while ((line = stdout.readLine()) != null) {
                        outputFile.getOutputStream().write(line.getBytes());
                        outputFile.getOutputStream().write('\n');
                    }
                    outputFile.getOutputStream().close();
                    stdout.close();
                    if (process.waitFor() != 0) {
                        final StringBuilder errors = new StringBuilder();
                        errors.append(R.string.logcat_error);
                        while ((line = stderr.readLine()) != null)
                            errors.append(line);
                        throw new Exception(errors.toString());
                    }
                }
            } catch (final Exception e) {
                outputFile.delete();
                throw e;
            }
            return outputFile.getFileName();
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
