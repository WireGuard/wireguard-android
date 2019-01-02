/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.configStore;

import android.content.Context;
import android.util.Log;

import com.wireguard.android.R;
import com.wireguard.config.BadConfigException;
import com.wireguard.config.Config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import java9.util.stream.Collectors;
import java9.util.stream.Stream;

/**
 * Configuration store that uses a {@code wg-quick}-style file for each configured tunnel.
 */

public final class FileConfigStore implements ConfigStore {
    private static final String TAG = "WireGuard/" + FileConfigStore.class.getSimpleName();

    private final Context context;

    public FileConfigStore(final Context context) {
        this.context = context;
    }

    @Override
    public Config create(final String name, final Config config) throws IOException {
        Log.d(TAG, "Creating configuration for tunnel " + name);
        final File file = fileFor(name);
        if (!file.createNewFile())
            throw new IOException(context.getString(R.string.config_file_exists_error, file.getName()));
        try (final FileOutputStream stream = new FileOutputStream(file, false)) {
            stream.write(config.toWgQuickString().getBytes(StandardCharsets.UTF_8));
        }
        return config;
    }

    @Override
    public void delete(final String name) throws IOException {
        Log.d(TAG, "Deleting configuration for tunnel " + name);
        final File file = fileFor(name);
        if (!file.delete())
            throw new IOException(context.getString(R.string.config_delete_error, file.getName()));
    }

    @Override
    public Set<String> enumerate() {
        return Stream.of(context.fileList())
                .filter(name -> name.endsWith(".conf"))
                .map(name -> name.substring(0, name.length() - ".conf".length()))
                .collect(Collectors.toUnmodifiableSet());
    }

    private File fileFor(final String name) {
        return new File(context.getFilesDir(), name + ".conf");
    }

    @Override
    public Config load(final String name) throws BadConfigException, IOException {
        try (final FileInputStream stream = new FileInputStream(fileFor(name))) {
            return Config.parse(stream);
        }
    }

    @Override
    public void rename(final String name, final String replacement) throws IOException {
        Log.d(TAG, "Renaming configuration for tunnel " + name + " to " + replacement);
        final File file = fileFor(name);
        final File replacementFile = fileFor(replacement);
        if (!replacementFile.createNewFile())
            throw new IOException(context.getString(R.string.config_exists_error, replacement));
        if (!file.renameTo(replacementFile)) {
            if (!replacementFile.delete())
                Log.w(TAG, "Couldn't delete marker file for new name " + replacement);
            throw new IOException(context.getString(R.string.config_rename_error, file.getName()));
        }
    }

    @Override
    public Config save(final String name, final Config config) throws IOException {
        Log.d(TAG, "Saving configuration for tunnel " + name);
        final File file = fileFor(name);
        if (!file.isFile())
            throw new FileNotFoundException(context.getString(R.string.config_not_found_error, file.getName()));
        try (final FileOutputStream stream = new FileOutputStream(file, false)) {
            stream.write(config.toWgQuickString().getBytes(StandardCharsets.UTF_8));
        }
        return config;
    }
}
