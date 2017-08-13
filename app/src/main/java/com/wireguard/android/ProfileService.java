package com.wireguard.android;

import android.app.Service;
import android.content.Intent;
import android.databinding.ObservableArrayMap;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.wireguard.config.Profile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Service that handles profile state coordination and all background processing for the app.
 */

public class ProfileService extends Service {
    private static final String TAG = "ProfileService";

    private final IBinder binder = new ProfileServiceBinder();
    private final ObservableArrayMap<String, Profile> profiles = new ObservableArrayMap<>();
    private RootShell rootShell;

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        rootShell = new RootShell(this);
        // Ensure the service sticks around after being unbound. This only needs to happen once.
        final Intent intent = new Intent(this, ProfileService.class);
        startService(intent);
        new ProfileLoader().execute(getFilesDir().listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".conf");
            }
        }));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private class ProfileConnecter extends AsyncTask<Void, Void, Boolean> {
        private final Profile profile;

        private ProfileConnecter(Profile profile) {
            super();
            this.profile = profile;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            Log.i(TAG, "Running wg-quick up for profile " + profile.getName());
            final File configFile = new File(getFilesDir(), profile.getName() + ".conf");
            return rootShell.run(null, "wg-quick up '" + configFile.getPath() + "'") == 0;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (!result)
                return;
            profile.setIsConnected(true);
        }
    }

    private class ProfileDisconnecter extends AsyncTask<Void, Void, Boolean> {
        private final Profile profile;

        private ProfileDisconnecter(Profile profile) {
            super();
            this.profile = profile;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            Log.i(TAG, "Running wg-quick down for profile " + profile.getName());
            final File configFile = new File(getFilesDir(), profile.getName() + ".conf");
            return rootShell.run(null, "wg-quick down '" + configFile.getPath() + "'") == 0;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (!result)
                return;
            profile.setIsConnected(false);
        }
    }

    private class ProfileLoader extends AsyncTask<File, Void, List<Profile>> {
        @Override
        protected List<Profile> doInBackground(File... files) {
            final List<String> interfaceNames = new LinkedList<>();
            final List<Profile> loadedProfiles = new LinkedList<>();
            final String command = "wg show interfaces";
            if (rootShell.run(interfaceNames, command) == 0 && interfaceNames.size() == 1) {
                // wg puts all interface names on the same line. Split them into separate elements.
                final String nameList = interfaceNames.get(0);
                Collections.addAll(interfaceNames, nameList.split(" "));
                interfaceNames.remove(0);
            } else {
                interfaceNames.clear();
                Log.w(TAG, "Can't enumerate network interfaces. All profiles will appear down.");
            }
            for (File file : files) {
                if (isCancelled())
                    return null;
                final String fileName = file.getName();
                final String profileName = fileName.substring(0, fileName.length() - 5);
                final Profile profile = new Profile(profileName);
                Log.v(TAG, "Attempting to load profile " + profileName);
                try {
                    profile.parseFrom(openFileInput(fileName));
                    profile.setIsConnected(interfaceNames.contains(profileName));
                    loadedProfiles.add(profile);
                } catch (IOException | IndexOutOfBoundsException e) {
                    Log.w(TAG, "Failed to load profile from " + fileName, e);
                }
            }
            return loadedProfiles;
        }

        @Override
        protected void onPostExecute(List<Profile> loadedProfiles) {
            if (loadedProfiles == null)
                return;
            for (Profile profile : loadedProfiles)
                profiles.put(profile.getName(), profile);
        }
    }

    private class ProfileRemover extends AsyncTask<Void, Void, Boolean> {
        private final Profile profile;

        private ProfileRemover(Profile profile) {
            super();
            this.profile = profile;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            Log.i(TAG, "Removing profile " + profile.getName());
            final File configFile = new File(getFilesDir(), profile.getName() + ".conf");
            if (configFile.delete()) {
                return true;
            } else {
                Log.e(TAG, "Could not delete configuration for profile " + profile.getName());
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (!result)
                return;
            profiles.remove(profile.getName());
        }
    }

    private class ProfileUpdater extends AsyncTask<Void, Void, Boolean> {
        private final String newName;
        private Profile newProfile;
        private final String oldName;
        private final Boolean shouldConnect;

        private ProfileUpdater(String oldName, Profile newProfile, Boolean shouldConnect) {
            super();
            this.newName = newProfile.getName();
            this.newProfile = newProfile;
            this.oldName = oldName;
            this.shouldConnect = shouldConnect;
            if (profiles.values().contains(newProfile))
                throw new IllegalArgumentException("Profile " + newName + " modified directly");
            if (!newName.equals(oldName) && profiles.get(newName) != null)
                throw new IllegalStateException("Profile " + newName + " already exists");
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            Log.i(TAG, (oldName == null ? "Adding" : "Updating") + " profile " + newName);
            final File newFile = new File(getFilesDir(), newName + ".conf");
            final File oldFile = new File(getFilesDir(), oldName + ".conf");
            if (!newName.equals(oldName) && newFile.exists()) {
                Log.w(TAG, "Refusing to overwrite existing profile configuration");
                return false;
            }
            try {
                final FileOutputStream stream = openFileOutput(oldFile.getName(), MODE_PRIVATE);
                stream.write(newProfile.toString().getBytes(StandardCharsets.UTF_8));
                stream.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not save configuration for profile " + oldName, e);
                return false;
            }
            if (!newName.equals(oldName) && !oldFile.renameTo(newFile)) {
                Log.e(TAG, "Could not rename " + oldFile.getName() + " to " + newFile.getName());
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (!result)
                return;
            final Profile oldProfile = profiles.remove(oldName);
            if (oldProfile != null) {
                try {
                    oldProfile.parseFrom(newProfile);
                    oldProfile.setName(newName);
                    newProfile = oldProfile;
                } catch (IOException e) {
                    Log.e(TAG, "Could not replace profile " + oldName + " with " + newName, e);
                    return;
                }
            }
            newProfile.setIsConnected(false);
            profiles.put(newName, newProfile);
            if (shouldConnect)
                new ProfileConnecter(newProfile).execute();
        }
    }

    private class ProfileServiceBinder extends Binder implements ProfileServiceInterface {
        @Override
        public void connectProfile(String name) {
            final Profile profile = profiles.get(name);
            if (profile == null || profile.getIsConnected())
                return;
            new ProfileConnecter(profile).execute();
        }

        @Override
        public Profile copyProfileForEditing(String name) {
            final Profile profile = profiles.get(name);
            return profile != null ? profile.copy() : null;
        }

        @Override
        public void disconnectProfile(String name) {
            final Profile profile = profiles.get(name);
            if (profile == null || !profile.getIsConnected())
                return;
            new ProfileDisconnecter(profile).execute();
        }

        @Override
        public ObservableArrayMap<String, Profile> getProfiles() {
            return profiles;
        }

        @Override
        public void removeProfile(String name) {
            final Profile profile = profiles.get(name);
            if (profile == null)
                return;
            if (profile.getIsConnected())
                new ProfileDisconnecter(profile).execute();
            new ProfileRemover(profile).execute();
        }

        @Override
        public void saveProfile(String oldName, Profile newProfile) {
            if (oldName != null) {
                final Profile oldProfile = profiles.get(oldName);
                if (oldProfile == null)
                    return;
                final boolean wasConnected = oldProfile.getIsConnected();
                if (wasConnected)
                    new ProfileDisconnecter(oldProfile).execute();
                new ProfileUpdater(oldName, newProfile, wasConnected).execute();
            } else {
                new ProfileUpdater(null, newProfile, false).execute();
            }
        }
    }
}
