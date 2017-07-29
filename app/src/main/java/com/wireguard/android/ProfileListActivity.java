package com.wireguard.android;

import android.app.Activity;
import android.databinding.DataBindingUtil;
import android.databinding.ObservableArrayList;
import android.databinding.ObservableList;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import com.wireguard.android.databinding.ProfileListActivityBinding;
import com.wireguard.config.Profile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;

public class ProfileListActivity extends Activity {
    private final ObservableList<Profile> profiles = new ObservableArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final ProfileListActivityBinding binding =
                DataBindingUtil.setContentView(this, R.layout.profile_list_activity);
        binding.setProfiles(profiles);
        new ProfileLoader().execute(getFilesDir().listFiles());
    }

    private class ProfileLoader extends AsyncTask<File, Profile, ArrayList<Profile>> {
        private static final String TAG = "WGProfileLoader";

        @Override
        protected ArrayList<Profile> doInBackground(File... files) {
            final ArrayList<Profile> loadedProfiles = new ArrayList<>();
            for (File file : files) {
                final String fileName = file.getName();
                final int suffixStart = fileName.lastIndexOf(".conf");
                if (suffixStart <= 0) {
                    Log.w(TAG, "Ignoring stray file " + fileName);
                    continue;
                }
                final Profile profile = new Profile(fileName.substring(0, suffixStart));
                try {
                    final FileInputStream inputStream = openFileInput(fileName);
                    profile.fromStream(inputStream);
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
        protected void onPostExecute(ArrayList<Profile> loadedProfiles) {
            // FIXME: This should replace an existing profile if the name matches.
            profiles.addAll(loadedProfiles);
        }
    }
}
