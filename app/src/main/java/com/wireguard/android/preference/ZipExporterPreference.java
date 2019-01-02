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
import com.wireguard.android.model.Tunnel;
import com.wireguard.android.util.ErrorMessages;
import com.wireguard.android.util.FragmentUtils;
import com.wireguard.config.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import java9.util.concurrent.CompletableFuture;

/**
 * Preference implementing a button that asynchronously exports config zips.
 */

public class ZipExporterPreference extends Preference {
    private static final String TAG = "WireGuard/" + ZipExporterPreference.class.getSimpleName();

    @Nullable private String exportedFilePath;

    public ZipExporterPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    private void exportZip() {
        Application.getTunnelManager().getTunnels().thenAccept(this::exportZip);
    }

    private void exportZip(final List<Tunnel> tunnels) {
        final List<CompletableFuture<Config>> futureConfigs = new ArrayList<>(tunnels.size());
        for (final Tunnel tunnel : tunnels)
            futureConfigs.add(tunnel.getConfigAsync().toCompletableFuture());
        if (futureConfigs.isEmpty()) {
            exportZipComplete(null, new IllegalArgumentException(
                    getContext().getString(R.string.no_tunnels_error)));
            return;
        }
        CompletableFuture.allOf(futureConfigs.toArray(new CompletableFuture[futureConfigs.size()]))
                .whenComplete((ignored1, exception) -> Application.getAsyncWorker().supplyAsync(() -> {
                    if (exception != null)
                        throw exception;
                    final File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    final File file = new File(path, "wireguard-export.zip");
                    if (!path.isDirectory() && !path.mkdirs())
                        throw new IOException(
                                getContext().getString(R.string.create_output_dir_error));
                    try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
                        for (int i = 0; i < futureConfigs.size(); ++i) {
                            zip.putNextEntry(new ZipEntry(tunnels.get(i).getName() + ".conf"));
                            zip.write(futureConfigs.get(i).getNow(null).
                                    toWgQuickString().getBytes(StandardCharsets.UTF_8));
                        }
                        zip.closeEntry();
                    } catch (final Exception e) {
                        // noinspection ResultOfMethodCallIgnored
                        file.delete();
                        throw e;
                    }
                    return file.getAbsolutePath();
                }).whenComplete(this::exportZipComplete));
    }

    private void exportZipComplete(@Nullable final String filePath, @Nullable final Throwable throwable) {
        if (throwable != null) {
            final String error = ErrorMessages.get(throwable);
            final String message = getContext().getString(R.string.zip_export_error, error);
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
                getContext().getString(R.string.zip_export_summary) :
                getContext().getString(R.string.zip_export_success, exportedFilePath);
    }

    @Override
    public CharSequence getTitle() {
        return getContext().getString(R.string.zip_export_title);
    }

    @Override
    protected void onClick() {
        FragmentUtils.getPrefActivity(this).ensurePermissions(
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                (permissions, granted) -> {
                    if (granted.length > 0 && granted[0] == PackageManager.PERMISSION_GRANTED) {
                        setEnabled(false);
                        exportZip();
                    }
                });
    }

}
