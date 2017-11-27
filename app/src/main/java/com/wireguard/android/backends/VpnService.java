package com.wireguard.android.backends;

import android.app.Service;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.OpenableColumns;
import android.service.quicksettings.TileService;
import android.util.Log;
import android.widget.Toast;

import com.wireguard.android.QuickTileService;
import com.wireguard.android.bindings.ObservableSortedMap;
import com.wireguard.android.bindings.ObservableTreeMap;
import com.wireguard.config.Config;
import com.wireguard.config.Peer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Service that handles config state coordination and all background processing for the application.
 */

public class VpnService extends Service
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String KEY_ENABLED_CONFIGS = "enabled_configs";
    public static final String KEY_PRIMARY_CONFIG = "primary_config";
    public static final String KEY_RESTORE_ON_BOOT = "restore_on_boot";
    private static final String TAG = "VpnService";

    private static VpnService instance;
    private final IBinder binder = new Binder();
    private final ObservableTreeMap<String, Config> configurations = new ObservableTreeMap<>();
    private final Set<String> enabledConfigs = new HashSet<>();
    private SharedPreferences preferences;
    private String primaryName;
    private RootShell rootShell;

    public static VpnService getInstance() {
        return instance;
    }

    /**
     * Add a new configuration to the set of known configurations. The configuration will initially
     * be disabled. The configuration's name must be unique within the set of known configurations.
     *
     * @param config The configuration to add.
     */
    public void add(final Config config) {
        new ConfigUpdater(null, config, false).execute();
    }

    /**
     * Attempt to disable and tear down an interface for this configuration. The configuration's
     * enabled state will be updated the operation is successful. If this configuration is already
     * disconnected, or it is not a known configuration, no changes will be made.
     *
     * @param name The name of the configuration (in the set of known configurations) to disable.
     */
    public void disable(final String name) {
        final Config config = configurations.get(name);
        if (config == null || !config.isEnabled())
            return;
        new ConfigDisabler(config).execute();
    }

    /**
     * Attempt to set up and enable an interface for this configuration. The configuration's enabled
     * state will be updated if the operation is successful. If this configuration is already
     * enabled, or it is not a known configuration, no changes will be made.
     *
     * @param name The name of the configuration (in the set of known configurations) to enable.
     */
    public void enable(final String name) {
        final Config config = configurations.get(name);
        if (config == null || config.isEnabled())
            return;
        new ConfigEnabler(config).execute();
    }

    /**
     * Retrieve a configuration known and managed by this service. The returned object must not be
     * modified directly.
     *
     * @param name The name of the configuration (in the set of known configurations) to retrieve.
     * @return An object representing the configuration. This object must not be modified.
     */
    public Config get(final String name) {
        return configurations.get(name);
    }

    /**
     * Retrieve the set of configurations known and managed by the service. Configurations in this
     * set must not be modified directly. If a configuration is to be updated, first create a copy
     * of it by calling getCopy().
     *
     * @return The set of known configurations.
     */
    public ObservableSortedMap<String, Config> getConfigs() {
        return configurations;
    }

    public void importFrom(final Uri... uris) {
        new ConfigImporter().execute(uris);
    }

    @Override
    public IBinder onBind(final Intent intent) {
        instance = this;
        return binder;
    }

    @Override
    public void onCreate() {
        // Ensure the service sticks around after being unbound. This only needs to happen once.
        startService(new Intent(this, getClass()));
        rootShell = new RootShell(this);
        new ConfigLoader().execute(getFilesDir().listFiles(new FilenameFilter() {
            @Override
            public boolean accept(final File dir, final String name) {
                return name.endsWith(".conf");
            }
        }));
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onDestroy() {
        preferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences preferences,
                                          final String key) {
        if (!KEY_PRIMARY_CONFIG.equals(key))
            return;
        boolean changed = false;
        final String newName = preferences.getString(key, null);
        if (primaryName != null && !primaryName.equals(newName)) {
            final Config oldConfig = configurations.get(primaryName);
            if (oldConfig != null)
                oldConfig.setIsPrimary(false);
            changed = true;
        }
        if (newName != null && !newName.equals(primaryName)) {
            final Config newConfig = configurations.get(newName);
            if (newConfig != null)
                newConfig.setIsPrimary(true);
            else
                preferences.edit().remove(KEY_PRIMARY_CONFIG).apply();
            changed = true;
        }
        primaryName = newName;
        if (changed)
            updateTile();
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        instance = this;
        return START_STICKY;
    }

    /**
     * Remove a configuration from being managed by the service. If it is currently enabled, the
     * the configuration will be disabled before removal. If successful, the configuration will be
     * removed from persistent storage. If the configuration is not known to the service, no changes
     * will be made.
     *
     * @param name The name of the configuration (in the set of known configurations) to remove.
     */
    public void remove(final String name) {
        final Config config = configurations.get(name);
        if (config == null)
            return;
        if (config.isEnabled())
            new ConfigDisabler(config).execute();
        new ConfigRemover(config).execute();
    }

    /**
     * Update the attributes of the named configuration. If the configuration is currently enabled,
     * it will be disabled before the update, and the service will attempt to re-enable it
     * afterward. If successful, the updated configuration will be saved to persistent storage.
     *
     * @param name   The name of an existing configuration to update.
     * @param config A copy of the configuration, with updated attributes.
     */
    public void update(final String name, final Config config) {
        if (name == null)
            return;
        if (configurations.containsValue(config))
            throw new IllegalArgumentException("Config " + config.getName() + " modified directly");
        final Config oldConfig = configurations.get(name);
        if (oldConfig == null)
            return;
        final boolean wasEnabled = oldConfig.isEnabled();
        if (wasEnabled)
            new ConfigDisabler(oldConfig).execute();
        new ConfigUpdater(oldConfig, config, wasEnabled).execute();
    }

    private void updateTile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
            return;
        Log.v(TAG, "Requesting quick tile update");
        TileService.requestListeningState(this, new ComponentName(this, QuickTileService.class));
    }

    private class ConfigDisabler extends AsyncTask<Void, Void, Boolean> {
        private final Config config;

        private ConfigDisabler(final Config config) {
            this.config = config;
        }

        @Override
        protected Boolean doInBackground(final Void... voids) {
            Log.i(TAG, "Running wg-quick down for " + config.getName());
            final File configFile = new File(getFilesDir(), config.getName() + ".conf");
            return rootShell.run(null, "wg-quick down '" + configFile.getPath() + "'") == 0;
        }

        @Override
        protected void onPostExecute(final Boolean result) {
            config.setIsEnabled(!result);
            if (!result)
                return;
            enabledConfigs.remove(config.getName());
            preferences.edit().putStringSet(KEY_ENABLED_CONFIGS, enabledConfigs).apply();
            if (config.getName().equals(primaryName))
                updateTile();
        }
    }

    private class ConfigEnabler extends AsyncTask<Void, Void, Boolean> {
        private final Config config;

        private ConfigEnabler(final Config config) {
            this.config = config;
        }

        @Override
        protected Boolean doInBackground(final Void... voids) {
            Log.i(TAG, "Running wg-quick up for " + config.getName());
            final File configFile = new File(getFilesDir(), config.getName() + ".conf");
            return rootShell.run(null, "wg-quick up '" + configFile.getPath() + "'") == 0;
        }

        @Override
        protected void onPostExecute(final Boolean result) {
            config.setIsEnabled(result);
            if (!result)
                return;
            enabledConfigs.add(config.getName());
            preferences.edit().putStringSet(KEY_ENABLED_CONFIGS, enabledConfigs).apply();
            if (config.getName().equals(primaryName))
                updateTile();
        }
    }

    private class ConfigImporter extends AsyncTask<Uri, String, List<File>> {
        @Override
        protected List<File> doInBackground(final Uri... uris) {
            final ContentResolver contentResolver = getContentResolver();
            final List<File> files = new ArrayList<>(uris.length);
            for (final Uri uri : uris) {
                if (isCancelled())
                    return null;
                String name = null;
                if ("file".equals(uri.getScheme())) {
                    name = uri.getLastPathSegment();
                } else {
                    final String[] columns = {OpenableColumns.DISPLAY_NAME};
                    try (final Cursor cursor =
                                 getContentResolver().query(uri, columns, null, null, null)) {
                        if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                            name = cursor.getString(0);
                            Log.v(getClass().getSimpleName(), "Got name via cursor");
                        }
                    }
                    if (name == null) {
                        name = Uri.decode(uri.getLastPathSegment());
                        if (name.indexOf('/') >= 0)
                            name = name.substring(name.lastIndexOf('/') + 1);
                        Log.v(getClass().getSimpleName(), "Got name from urlencoded path");
                    }
                }
                if (!name.endsWith(".conf"))
                    name = name + ".conf";
                if (!Config.isNameValid(name.substring(0, name.length() - 5))) {
                    Log.v(getClass().getSimpleName(), "Detected name is not valid: " + name);
                    publishProgress(name + ": Invalid config filename");
                    continue;
                }
                Log.d(getClass().getSimpleName(), "Mapped URI " + uri + " to file name " + name);
                final File output = new File(getFilesDir(), name);
                if (output.exists()) {
                    Log.w(getClass().getSimpleName(), "Config file " + name + " already exists");
                    publishProgress(name + " already exists");
                    continue;
                }
                try (final InputStream in = contentResolver.openInputStream(uri);
                     final OutputStream out = new FileOutputStream(output, false)) {
                    if (in == null)
                        throw new IOException("Failed to open input");
                    // FIXME: This is a rather arbitrary size.
                    final byte[] buffer = new byte[4096];
                    int bytes;
                    while ((bytes = in.read(buffer)) != -1)
                        out.write(buffer, 0, bytes);
                    files.add(output);
                } catch (final IOException e) {
                    Log.w(getClass().getSimpleName(), "Failed to import config from " + uri, e);
                    publishProgress(name + ": " + e.getMessage());
                }
            }
            return files;
        }

        @Override
        protected void onProgressUpdate(final String... errors) {
            Toast.makeText(getApplicationContext(), errors[0], Toast.LENGTH_SHORT).show();
        }

        @Override
        protected void onPostExecute(final List<File> files) {
            new ConfigLoader().execute(files.toArray(new File[files.size()]));
        }
    }

    private class ConfigLoader extends AsyncTask<File, String, List<Config>> {
        @Override
        protected List<Config> doInBackground(final File... files) {
            final List<Config> configs = new LinkedList<>();
            final List<String> interfaces = new LinkedList<>();
            final String command = "wg show interfaces";
            if (rootShell.run(interfaces, command) == 0 && interfaces.size() == 1) {
                // wg puts all interface names on the same line. Split them into separate elements.
                final String nameList = interfaces.get(0);
                Collections.addAll(interfaces, nameList.split(" "));
                interfaces.remove(0);
            } else {
                interfaces.clear();
                Log.w(TAG, "No existing WireGuard interfaces found. Maybe they are all disabled?");
            }
            for (final File file : files) {
                if (isCancelled())
                    return null;
                final String fileName = file.getName();
                final String configName = fileName.substring(0, fileName.length() - 5);
                Log.v(TAG, "Attempting to load config " + configName);
                try {
                    final Config config = new Config();
                    config.parseFrom(openFileInput(fileName));
                    config.setIsEnabled(interfaces.contains(configName));
                    config.setName(configName);
                    configs.add(config);
                } catch (IllegalArgumentException | IOException e) {
                    try {
                        file.delete();
                    } catch (Exception e2) {
                        Log.w(TAG, "Could not remove " + fileName, e2);
                    }
                    Log.w(TAG, "Failed to load config from " + fileName, e);
                    publishProgress(fileName + ": " + e.getMessage());
                }
            }
            return configs;
        }

        @Override
        protected void onProgressUpdate(final String... errors) {
            Toast.makeText(getApplicationContext(), errors[0], Toast.LENGTH_SHORT).show();
        }

        @Override
        protected void onPostExecute(final List<Config> configs) {
            if (configs == null)
                return;
            for (final Config config : configs)
                configurations.put(config.getName(), config);
            // Run the handler to avoid duplicating the code here.
            onSharedPreferenceChanged(preferences, KEY_PRIMARY_CONFIG);
            if (preferences.getBoolean(KEY_RESTORE_ON_BOOT, false)) {
                final Set<String> configsToEnable =
                        preferences.getStringSet(KEY_ENABLED_CONFIGS, null);
                if (configsToEnable != null) {
                    for (final String name : configsToEnable) {
                        final Config config = configurations.get(name);
                        if (config != null && !config.isEnabled())
                            new ConfigEnabler(config).execute();
                    }
                }
            }
        }
    }

    private class ConfigRemover extends AsyncTask<Void, Void, Boolean> {
        private final Config config;

        private ConfigRemover(final Config config) {
            this.config = config;
        }

        @Override
        protected Boolean doInBackground(final Void... voids) {
            Log.i(TAG, "Removing config " + config.getName());
            final File configFile = new File(getFilesDir(), config.getName() + ".conf");
            if (configFile.delete()) {
                return true;
            } else {
                Log.e(TAG, "Could not delete configuration for config " + config.getName());
                return false;
            }
        }

        @Override
        protected void onPostExecute(final Boolean result) {
            if (!result)
                return;
            configurations.remove(config.getName());
            if (config.getName().equals(primaryName)) {
                // This will get picked up by the preference change listener.
                preferences.edit().remove(KEY_PRIMARY_CONFIG).apply();
            }
        }
    }

    private class ConfigUpdater extends AsyncTask<Void, Void, Boolean> {
        private final Config newConfig;
        private final String newName;
        private final String oldName;
        private final Boolean shouldConnect;
        private Config knownConfig;

        private ConfigUpdater(final Config knownConfig, final Config newConfig,
                              final Boolean shouldConnect) {
            this.knownConfig = knownConfig;
            this.newConfig = newConfig.copy();
            newName = newConfig.getName();
            // When adding a config, "old file" and "new file" are the same thing.
            oldName = knownConfig != null ? knownConfig.getName() : newName;
            this.shouldConnect = shouldConnect;
            if (newName == null || !Config.isNameValid(newName))
                throw new IllegalArgumentException("This configuration does not have a valid name");
            if (isAddOrRename() && configurations.containsKey(newName))
                throw new IllegalStateException("Configuration " + newName + " already exists");
            if (newConfig.getInterface().getPublicKey() == null)
                throw new IllegalArgumentException("This configuration needs a valid private key");
            for (final Peer peer : newConfig.getPeers())
                if (peer.getPublicKey() == null || peer.getPublicKey().isEmpty())
                    throw new IllegalArgumentException("Each peer must have a valid public key");
        }

        @Override
        protected Boolean doInBackground(final Void... voids) {
            Log.i(TAG, (knownConfig == null ? "Adding" : "Updating") + " config " + newName);
            final File newFile = new File(getFilesDir(), newName + ".conf");
            final File oldFile = new File(getFilesDir(), oldName + ".conf");
            if (isAddOrRename() && newFile.exists()) {
                Log.w(TAG, "Refusing to overwrite existing config configuration");
                return false;
            }
            try {
                final FileOutputStream stream = openFileOutput(oldFile.getName(), MODE_PRIVATE);
                stream.write(newConfig.toString().getBytes(StandardCharsets.UTF_8));
                stream.close();
            } catch (final IOException e) {
                Log.e(TAG, "Could not save configuration for config " + oldName, e);
                return false;
            }
            if (isRename() && !oldFile.renameTo(newFile)) {
                Log.e(TAG, "Could not rename " + oldFile.getName() + " to " + newFile.getName());
                return false;
            }
            return true;
        }

        private boolean isAddOrRename() {
            return knownConfig == null || !newName.equals(oldName);
        }

        private boolean isRename() {
            return knownConfig != null && !newName.equals(oldName);
        }

        @Override
        protected void onPostExecute(final Boolean result) {
            if (!result)
                return;
            if (knownConfig != null)
                configurations.remove(oldName);
            if (knownConfig == null)
                knownConfig = new Config();
            knownConfig.copyFrom(newConfig);
            knownConfig.setIsEnabled(false);
            knownConfig.setIsPrimary(oldName != null && oldName.equals(primaryName));
            configurations.put(newName, knownConfig);
            if (isRename() && oldName != null && oldName.equals(primaryName))
                preferences.edit().putString(KEY_PRIMARY_CONFIG, newName).apply();
            if (shouldConnect)
                new ConfigEnabler(knownConfig).execute();
        }
    }
}
