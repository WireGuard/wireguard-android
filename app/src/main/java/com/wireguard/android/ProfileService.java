package com.wireguard.android;

import android.app.Service;
import android.content.Intent;
import android.databinding.ObservableArrayList;
import android.databinding.ObservableList;
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
    private final ObservableList<Profile> profiles = new ObservableArrayList<>();
    private RootShell rootShell;

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        rootShell = new RootShell(this);
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

    private class ProfileAdder extends AsyncTask<Void, Void, Boolean> {
        private final Profile profile;

        private ProfileAdder(Profile profile) {
            super();
            this.profile = profile;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            Log.i(TAG, "Adding profile " + profile.getName());
            try {
                final String configFile = profile.getName() + ".conf";
                final FileOutputStream stream = openFileOutput(configFile, MODE_PRIVATE);
                stream.write(profile.toString().getBytes(StandardCharsets.UTF_8));
                stream.close();
                return true;
            } catch (IOException e) {
                Log.e(TAG, "Could not create profile " + profile.getName(), e);
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (!result)
                return;
            profile.setIsConnected(false);
            profiles.add(profile);
        }
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
                } catch (IOException e) {
                    Log.w(TAG, "Failed to load profile from " + fileName, e);
                }
            }
            return loadedProfiles;
        }

        @Override
        protected void onPostExecute(List<Profile> loadedProfiles) {
            if (loadedProfiles == null)
                return;
            profiles.addAll(loadedProfiles);
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
            profiles.remove(profile);
        }
    }

    private class ProfileUpdater extends AsyncTask<Void, Void, Boolean> {
        private final Profile profile, newProfile;
        private final boolean wasConnected;

        private ProfileUpdater(Profile profile, Profile newProfile, boolean wasConnected) {
            super();
            this.profile = profile;
            this.newProfile = newProfile;
            this.wasConnected = wasConnected;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            Log.i(TAG, "Updating profile " + profile.getName());
            if (!newProfile.getName().equals(profile.getName()))
                throw new IllegalStateException("Profile name mismatch: " + profile.getName());
            try {
                final String configFile = profile.getName() + ".conf";
                final FileOutputStream stream = openFileOutput(configFile, MODE_PRIVATE);
                stream.write(newProfile.toString().getBytes(StandardCharsets.UTF_8));
                stream.close();
                return true;
            } catch (IOException e) {
                Log.e(TAG, "Could not update profile " + profile.getName(), e);
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (!result)
                return;
            // FIXME: This is also why the list of profiles should be a map.
            final int index = profiles.indexOf(profile);
            profiles.set(index, newProfile);
            if (wasConnected)
                new ProfileConnecter(newProfile).execute();
        }
    }

    private class ProfileServiceBinder extends Binder implements ProfileServiceInterface {
        @Override
        public void connectProfile(Profile profile) {
            if (!profiles.contains(profile))
                return;
            if (profile.getIsConnected())
                return;
            new ProfileConnecter(profile).execute();
        }

        @Override
        public Profile copyProfileForEditing(Profile profile) {
            if (!profiles.contains(profile))
                return null;
            return profile.copy();
        }

        @Override
        public void disconnectProfile(Profile profile) {
            if (!profiles.contains(profile))
                return;
            if (!profile.getIsConnected())
                return;
            new ProfileDisconnecter(profile).execute();
        }

        @Override
        public ObservableList<Profile> getProfiles() {
            return profiles;
        }

        @Override
        public void removeProfile(Profile profile) {
            if (!profiles.contains(profile))
                return;
            if (profile.getIsConnected())
                new ProfileDisconnecter(profile).execute();
            new ProfileRemover(profile).execute();
        }

        @Override
        public void saveProfile(Profile newProfile) {
            // FIXME: This is why the list of profiles should be a map.
            Profile profile = null;
            for (Profile p : profiles)
                if (p.getName().equals(newProfile.getName()))
                    profile = p;
            if (profile != null) {
                final boolean wasConnected = profile.getIsConnected();
                if (wasConnected)
                    new ProfileDisconnecter(profile).execute();
                new ProfileUpdater(profile, newProfile, wasConnected).execute();
            } else {
                new ProfileAdder(newProfile).execute();
            }
        }
    }
}
