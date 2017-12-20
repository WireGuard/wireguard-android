package com.wireguard.android;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import com.wireguard.android.backends.RootShell;

public class SettingsActivity extends Activity {
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final FragmentTransaction transaction = getFragmentManager().beginTransaction();
        final SettingsFragment fragment = new SettingsFragment();
        fragment.setArguments(getIntent().getExtras());
        transaction.replace(android.R.id.content, fragment).commit();
    }

    public static class SettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
            if (getArguments() != null && getArguments().getBoolean("showQuickTile"))
                ((ConfigListPreference) findPreference("primary_config")).show();

            final Preference installTools = findPreference("install_cmd_line_tools");
            installTools.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference preference) {
                    new ToolsInstaller(installTools).execute();
                    return true;
                }
            });
        }
    }

    private static class ToolsInstaller extends AsyncTask<Void, Void, Integer> {
        Preference installTools;

        public ToolsInstaller(Preference installTools) {
            this.installTools = installTools;
            installTools.setEnabled(false);
            installTools.setSummary(installTools.getContext().getString(R.string.install_cmd_line_tools_progress));
        }

        private static final String[][] libraryNamedExecutables = {
                { "libwg.so", "wg" },
                { "libwg-quick.so", "wg-quick" }
        };

        @Override
        protected Integer doInBackground(final Void... voids) {
            final Context context = installTools.getContext();
            final String libDir = context.getApplicationInfo().nativeLibraryDir;
            final StringBuilder cmd = new StringBuilder();

            cmd.append("set -ex;");

            for (final String[] libraryNamedExecutable : libraryNamedExecutables) {
                final String arg1 = "'" + libDir + "/" + libraryNamedExecutable[0] + "'";
                final String arg2 = "'/system/xbin/" + libraryNamedExecutable[1] + "'";

                cmd.append(String.format("cmp -s %s %s && ", arg1, arg2));
            }
            cmd.append("exit 114;");

            cmd.append("trap 'mount -o ro,remount /system' EXIT;");
            cmd.append("mount -o rw,remount /system;");

            for (final String[] libraryNamedExecutable : libraryNamedExecutables) {
                final String arg1 = "'" + libDir + "/" + libraryNamedExecutable[0] + "'";
                final String arg2 = "'/system/xbin/" + libraryNamedExecutable[1] + "'";
                cmd.append(String.format("cp %s %s; chmod 755 %s;", arg1, arg2, arg2));
            }

            return new RootShell(context).run(null, cmd.toString());
        }

        @Override
        protected void onPostExecute(final Integer ret) {
            final Context context = installTools.getContext();
            String status;

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
            installTools.setSummary(status);
            installTools.setEnabled(true);
        }
    }
}
