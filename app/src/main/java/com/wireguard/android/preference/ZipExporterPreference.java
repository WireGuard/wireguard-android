/*
 * Copyright © 2018 Samuel Holland <samuel@sholland.org>
 * Copyright © 2018 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package com.wireguard.android.preference;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.support.design.widget.Snackbar;
import android.support.v7.preference.Preference;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextThemeWrapper;

import com.wireguard.android.Application;
import com.wireguard.android.Application.ApplicationComponent;
import com.wireguard.android.R;
import com.wireguard.android.activity.SettingsActivity;
import com.wireguard.android.model.Tunnel;
import com.wireguard.android.model.TunnelManager;
import com.wireguard.android.util.AsyncWorker;
import com.wireguard.android.util.ExceptionLoggers;
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

    private final AsyncWorker asyncWorker;
    private final TunnelManager tunnelManager;
    private String exportedFilePath;

    @SuppressWarnings({"SameParameterValue", "WeakerAccess"})
    public ZipExporterPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        final ApplicationComponent applicationComponent = Application.getComponent();
        asyncWorker = applicationComponent.getAsyncWorker();
        tunnelManager = applicationComponent.getTunnelManager();
    }

    private static SettingsActivity getPrefActivity(final Preference preference) {
        final Context context = preference.getContext();
        if (context instanceof ContextThemeWrapper) {
            if (((ContextThemeWrapper) context).getBaseContext() instanceof SettingsActivity) {
                return ((SettingsActivity) ((ContextThemeWrapper) context).getBaseContext());
            }
        }
        return null;
    }

    private void exportZip() {
        final List<Tunnel> tunnels = new ArrayList<>(tunnelManager.getTunnels());
        final List<CompletableFuture<Config>> futureConfigs = new ArrayList<>(tunnels.size());
        for (final Tunnel tunnel : tunnels)
            futureConfigs.add(tunnel.getConfigAsync().toCompletableFuture());
        if (futureConfigs.isEmpty()) {
            exportZipComplete(null, new IllegalArgumentException("No tunnels exist"));
            return;
        }
        CompletableFuture.allOf(futureConfigs.toArray(new CompletableFuture[futureConfigs.size()]))
                .whenComplete((ignored1, exception) -> asyncWorker.supplyAsync(() -> {
                    if (exception != null)
                        throw exception;
                    final File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    final File file = new File(path, "wireguard-export.zip");
                    if (!path.isDirectory() && !path.mkdirs())
                        throw new IOException("Cannot create output directory");
                    try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
                        for (int i = 0; i < futureConfigs.size(); ++i) {
                            zip.putNextEntry(new ZipEntry(tunnels.get(i).getName() + ".conf"));
                            zip.write(futureConfigs.get(i).getNow(null).
                                    toString().getBytes(StandardCharsets.UTF_8));
                        }
                        zip.closeEntry();
                        zip.close();
                    } catch (Exception e) {
                        // noinspection ResultOfMethodCallIgnored
                        file.delete();
                        throw e;
                    }
                    return file.getAbsolutePath();
                }).whenComplete(this::exportZipComplete));
    }

    private void exportZipComplete(final String filePath, final Throwable throwable) {
        if (throwable != null) {
            final String error = ExceptionLoggers.unwrapMessage(throwable);
            final String message = getContext().getString(R.string.export_error, error);
            Log.e(TAG, message, throwable);
            Snackbar.make(
                    getPrefActivity(this).findViewById(android.R.id.content),
                    message, Snackbar.LENGTH_LONG).show();
        } else {
            exportedFilePath = filePath;
            setEnabled(false);
            notifyChanged();
        }
    }

    @Override
    public CharSequence getSummary() {
        return exportedFilePath == null ?
                getContext().getString(R.string.export_summary) :
                getContext().getString(R.string.export_success, exportedFilePath);
    }

    @Override
    public CharSequence getTitle() {
        return getContext().getString(R.string.zip_exporter_title);
    }

    @Override
    protected void onClick() {
        getPrefActivity(this).ensurePermissions(
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                (permissions, granted) -> {
                    if (granted.length > 0 && granted[0] == PackageManager.PERMISSION_GRANTED)
                        exportZip();
                });
    }

}
