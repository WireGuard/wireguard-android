package com.wireguard.android.configStore;

import android.content.Context;
import android.util.Log;

import com.wireguard.android.Application.ApplicationContext;
import com.wireguard.android.util.AsyncWorker;
import com.wireguard.config.Config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import java9.util.concurrent.CompletionStage;
import java9.util.stream.Collectors;
import java9.util.stream.Stream;

/**
 * Created by samuel on 12/28/17.
 */

public final class FileConfigStore implements ConfigStore {
    private static final String TAG = FileConfigStore.class.getSimpleName();

    private final AsyncWorker asyncWorker;
    private final Context context;

    public FileConfigStore(final AsyncWorker asyncWorker,
                           @ApplicationContext final Context context) {
        this.asyncWorker = asyncWorker;
        this.context = context;
    }

    @Override
    public CompletionStage<Config> create(final String name, final Config config) {
        return asyncWorker.supplyAsync(() -> {
            final File file = fileFor(name);
            if (!file.createNewFile()) {
                final String message = "Configuration file " + file.getName() + " already exists";
                throw new IllegalStateException(message);
            }
            try (FileOutputStream stream = new FileOutputStream(file, false)) {
                stream.write(config.toString().getBytes(StandardCharsets.UTF_8));
                return config;
            }
        });
    }

    @Override
    public CompletionStage<Void> delete(final String name) {
        return asyncWorker.runAsync(() -> {
            final File file = fileFor(name);
            if (!file.delete())
                throw new IOException("Cannot delete configuration file " + file.getName());
        });
    }

    @Override
    public CompletionStage<Set<String>> enumerate() {
        return asyncWorker.supplyAsync(() -> Stream.of(context.fileList())
                .filter(name -> name.endsWith(".conf"))
                .map(name -> name.substring(0, name.length() - ".conf".length()))
                .collect(Collectors.toUnmodifiableSet()));
    }

    private File fileFor(final String name) {
        return new File(context.getFilesDir(), name + ".conf");
    }

    @Override
    public CompletionStage<Config> load(final String name) {
        return asyncWorker.supplyAsync(() -> {
            try (FileInputStream stream = new FileInputStream(fileFor(name))) {
                return Config.from(stream);
            }
        });
    }

    @Override
    public CompletionStage<Config> save(final String name, final Config config) {
        Log.d(TAG, "Requested save config for tunnel " + name);
        return asyncWorker.supplyAsync(() -> {
            final File file = fileFor(name);
            if (!file.isFile()) {
                final String message = "Configuration file " + file.getName() + " not found";
                throw new IllegalStateException(message);
            }
            try (FileOutputStream stream = new FileOutputStream(file, false)) {
                Log.d(TAG, "Writing out config for tunnel " + name);
                stream.write(config.toString().getBytes(StandardCharsets.UTF_8));
                return config;
            }
        });
    }
}
