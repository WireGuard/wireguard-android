package com.wireguard.android.fragment;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.widget.Toast;

import com.wireguard.android.Application;
import com.wireguard.android.R;
import com.wireguard.android.activity.BaseActivity;
import com.wireguard.android.databinding.AppListDialogFragmentBinding;
import com.wireguard.android.model.ApplicationData;
import com.wireguard.android.model.Tunnel;
import com.wireguard.android.util.ExceptionLoggers;
import com.wireguard.android.util.ObservableKeyedArrayList;
import com.wireguard.android.util.ObservableKeyedList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AppListDialogFragment extends DialogFragment {

    private static final String KEY_EXCLUDED_APPS = "excludedApps";

    private List<String> currentlyExcludedApps;
    private Tunnel tunnel;
    private final ObservableKeyedList<String, ApplicationData> appData = new ObservableKeyedArrayList<>();

    public static <T extends Fragment & AppExclusionListener> AppListDialogFragment newInstance(String[] excludedApps, T target) {
        Bundle extras = new Bundle();
        extras.putStringArray(KEY_EXCLUDED_APPS, excludedApps);
        AppListDialogFragment fragment = new AppListDialogFragment();
        fragment.setTargetFragment(target, 0);
        fragment.setArguments(extras);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        currentlyExcludedApps = Arrays.asList(getArguments().getStringArray(KEY_EXCLUDED_APPS));
    }

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        if (context instanceof BaseActivity) {
            tunnel = ((BaseActivity) context).getSelectedTunnel();
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());
        alertDialogBuilder.setTitle(R.string.excluded_applications);

        AppListDialogFragmentBinding binding = AppListDialogFragmentBinding.inflate(getActivity().getLayoutInflater(), null, false);
        binding.executePendingBindings();
        alertDialogBuilder.setView(binding.getRoot());

        alertDialogBuilder.setPositiveButton(R.string.set_exclusions, (dialog, which) -> setExclusionsAndDismiss());
        alertDialogBuilder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss());

        binding.setFragment(this);
        binding.setAppData(appData);

        loadData();

        return alertDialogBuilder.create();
    }

    private void loadData() {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        final PackageManager pm = activity.getPackageManager();
        Application.getAsyncWorker().supplyAsync(() -> {
            Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
            launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> resolveInfos = pm.queryIntentActivities(launcherIntent, 0);

            List<ApplicationData> appData = new ArrayList<>();
            for (ResolveInfo resolveInfo : resolveInfos) {
                String packageName = resolveInfo.activityInfo.packageName;
                appData.add(new ApplicationData(resolveInfo.loadIcon(pm), resolveInfo.loadLabel(pm).toString(), packageName, currentlyExcludedApps.contains(packageName)));
            }

            Collections.sort(appData, (lhs, rhs) -> lhs.getName().toLowerCase().compareTo(rhs.getName().toLowerCase()));
            return appData;
        }).whenComplete(((data, throwable) -> {
            if (data != null) {
                appData.clear();
                appData.addAll(data);
            } else {
                final String error = throwable != null ? ExceptionLoggers.unwrapMessage(throwable) : "Unknown";
                final String message = activity.getString(R.string.error_fetching_apps, error);
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
                dismissAllowingStateLoss();
            }
        }));
    }

    void setExclusionsAndDismiss() {
        final List<String> excludedApps = new ArrayList<>();
        for (ApplicationData data : appData) {
            if (data.isExcludedFromTunnel()) {
                excludedApps.add(data.getPackageName());
            }
        }

        ((AppExclusionListener) getTargetFragment()).onExcludedAppsSelected(excludedApps);
        dismiss();
    }

    public interface AppExclusionListener {
        void onExcludedAppsSelected(List<String> excludedApps);
    }

}
