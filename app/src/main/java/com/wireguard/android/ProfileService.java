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
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * Service that handles profile state coordination and all background processing for the app.
 */

public class ProfileService extends Service {
    private static final String TAG = "ProfileService";

    private final IBinder binder = new ProfileServiceBinder();
    private final ObservableList<Profile> profiles = new ObservableArrayList<>();

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
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

    private class ProfileLoader extends AsyncTask<File, Void, List<Profile>> {
        @Override
        protected List<Profile> doInBackground(File... files) {
            final List<String> interfaceNames = new LinkedList<>();
            final List<Profile> loadedProfiles = new LinkedList<>();
            final String command = "ip -br link show type wireguard | cut -d' ' -f1";
            if (RootShell.run(interfaceNames, command) != 0) {
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

    private class ProfileServiceBinder extends Binder implements ProfileServiceInterface {
        @Override
        public void connectProfile(Profile profile) {
        }

        @Override
        public Profile copyProfileForEditing(Profile profile) {
            if (!profiles.contains(profile))
                return null;
            return profile.copy();
        }

        @Override
        public void disconnectProfile(Profile profile) {
        }

        @Override
        public ObservableList<Profile> getProfiles() {
            return profiles;
        }

        @Override
        public void removeProfile(Profile profile) {
        }

        @Override
        public void saveProfile(Profile newProfile) {
        }
    }
}
