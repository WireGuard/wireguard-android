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
        new ProfileLoader().execute(getFilesDir().listFiles());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private class ProfileLoader extends AsyncTask<File, Void, List<Profile>> {
        @Override
        protected List<Profile> doInBackground(File... files) {
            final List<Profile> loadedProfiles = new LinkedList<>();
            for (File file : files) {
                final String fileName = file.getName();
                final String profileName = fileName.substring(0, fileName.length() - 5);
                final Profile profile = new Profile(profileName);
                try {
                    profile.parseFrom(openFileInput(fileName));
                    loadedProfiles.add(profile);
                } catch (IOException e) {
                    Log.w(TAG, "Failed to load profile from " + fileName, e);
                }
                if (isCancelled())
                    break;
            }
            return loadedProfiles;
        }

        @Override
        protected void onPostExecute(List<Profile> loadedProfiles) {
            profiles.addAll(loadedProfiles);
        }
    }

    private class ProfileServiceBinder extends Binder implements ProfileServiceInterface {
        public ObservableList<Profile> getProfiles() {
            return profiles;
        }
    }
}
