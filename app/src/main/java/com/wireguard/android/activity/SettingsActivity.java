package com.wireguard.android.activity;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import com.wireguard.android.R;
import com.wireguard.android.util.RootShell;

/**
 * Interface for changing application-global persistent settings.
 */

public class SettingsActivity extends Activity {
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getFragmentManager().findFragmentById(android.R.id.content) == null) {
            getFragmentManager().beginTransaction()
                    .add(android.R.id.content, new SettingsFragment())
                    .commit();
        }
    }

    public static class SettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
            final Preference installTools = findPreference("install_cmd_line_tools");
            installTools.setOnPreferenceClickListener(preference -> {
                new ToolsInstaller(preference).execute();
                return true;
            });
        }
    }

    private static final class ToolsInstaller extends AsyncTask<Void, Void, Integer> {
        private static final String[][] LIBRARY_NAMED_EXECUTABLES = {
                {"libwg.so", "wg"},
                {"libwg-quick.so", "wg-quick"}
        };

        private final Context context;
        private final Preference preference;

        private ToolsInstaller(final Preference preference) {
            context = preference.getContext();
            this.preference = preference;
            preference.setEnabled(false);
            preference.setSummary(context.getString(R.string.install_cmd_line_tools_progress));
        }

        @Override
        protected Integer doInBackground(final Void... voids) {
            final String libDir = context.getApplicationInfo().nativeLibraryDir;
            final StringBuilder cmd = new StringBuilder();

            cmd.append("set -ex;");

            for (final String[] libraryNamedExecutable : LIBRARY_NAMED_EXECUTABLES) {
                final String arg1 = '\'' + libDir + '/' + libraryNamedExecutable[0] + '\'';
                final String arg2 = "'/system/xbin/" + libraryNamedExecutable[1] + '\'';

                cmd.append(String.format("cmp -s %s %s && ", arg1, arg2));
            }
            cmd.append("exit 114;");

            cmd.append("trap 'mount -o ro,remount /system' EXIT;");
            cmd.append("mount -o rw,remount /system;");

            for (final String[] libraryNamedExecutable : LIBRARY_NAMED_EXECUTABLES) {
                final String arg1 = '\'' + libDir + '/' + libraryNamedExecutable[0] + '\'';
                final String arg2 = "'/system/xbin/" + libraryNamedExecutable[1] + '\'';
                cmd.append(String.format("cp %s %s; chmod 755 %s;", arg1, arg2, arg2));
            }

            return new RootShell(context).run(null, cmd.toString());
        }

        @Override
        protected void onPostExecute(final Integer ret) {
            final String status;

            switch (ret) {
                case 0:
                    status = context.getString(R.string.install_cmd_line_tools_success);
                    break;
                case 114 /* OsConstants.EALREADY */:
                    status = context.getString(R.string.install_cmd_line_tools_already);
                    break;
                default:
                    status = context.getString(R.string.install_cmd_line_tools_failure);
                    break;
            }
            preference.setSummary(status);
            preference.setEnabled(true);
        }
    }
}
