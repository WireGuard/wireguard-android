/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.fragment;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.widget.Toast;

import com.wireguard.android.Application;
import com.wireguard.android.R;
import com.wireguard.android.databinding.AppListDialogFragmentBinding;
import com.wireguard.android.model.ApplicationData;
import com.wireguard.android.util.ErrorMessages;
import com.wireguard.android.util.ObservableKeyedArrayList;
import com.wireguard.android.util.ObservableKeyedList;
import com.wireguard.util.NonNullForAll;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import java9.util.Comparators;
import java9.util.stream.Collectors;
import java9.util.stream.StreamSupport;

@NonNullForAll
public class AppListDialogFragment extends DialogFragment {

    private static final String KEY_EXCLUDED_APPS = "excludedApps";
    private final ObservableKeyedList<String, ApplicationData> appData = new ObservableKeyedArrayList<>();
    private List<String> currentlyExcludedApps = Collections.emptyList();

    public static <T extends Fragment & AppExclusionListener>
    AppListDialogFragment newInstance(final ArrayList<String> excludedApps, final T target) {
        final Bundle extras = new Bundle();
        extras.putStringArrayList(KEY_EXCLUDED_APPS, excludedApps);
        final AppListDialogFragment fragment = new AppListDialogFragment();
        fragment.setTargetFragment(target, 0);
        fragment.setArguments(extras);
        return fragment;
    }

    private void loadData() {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        final PackageManager pm = activity.getPackageManager();
        Application.getAsyncWorker().supplyAsync(() -> {
            final Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
            launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            final List<ResolveInfo> resolveInfos = pm.queryIntentActivities(launcherIntent, 0);

            final List<ApplicationData> applicationData = new ArrayList<>();
            for (ResolveInfo resolveInfo : resolveInfos) {
                String packageName = resolveInfo.activityInfo.packageName;
                applicationData.add(new ApplicationData(resolveInfo.loadIcon(pm), resolveInfo.loadLabel(pm).toString(), packageName, currentlyExcludedApps.contains(packageName)));
            }

            Collections.sort(applicationData, Comparators.comparing(ApplicationData::getName, String.CASE_INSENSITIVE_ORDER));
            return applicationData;
        }).whenComplete(((data, throwable) -> {
            if (data != null) {
                appData.clear();
                appData.addAll(data);
            } else {
                final String error = ErrorMessages.get(throwable);
                final String message = activity.getString(R.string.error_fetching_apps, error);
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
                dismissAllowingStateLoss();
            }
        }));
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final List<String> excludedApps = requireArguments().getStringArrayList(KEY_EXCLUDED_APPS);
        currentlyExcludedApps = (excludedApps != null) ? excludedApps : Collections.emptyList();
    }

    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(requireActivity());
        alertDialogBuilder.setTitle(R.string.excluded_applications);

        final AppListDialogFragmentBinding binding = AppListDialogFragmentBinding.inflate(requireActivity().getLayoutInflater(), null, false);
        binding.executePendingBindings();
        alertDialogBuilder.setView(binding.getRoot());

        alertDialogBuilder.setPositiveButton(R.string.set_exclusions, (dialog, which) -> setExclusionsAndDismiss());
        alertDialogBuilder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss());
        alertDialogBuilder.setNeutralButton(R.string.toggle_all, (dialog, which) -> {
        });

        binding.setFragment(this);
        binding.setAppData(appData);

        loadData();

        final AlertDialog dialog = alertDialogBuilder.create();
        dialog.setOnShowListener(d -> dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(view -> {
            final List<ApplicationData> selectedItems = StreamSupport.stream(appData)
                    .filter(ApplicationData::isExcludedFromTunnel)
                    .collect(Collectors.toList());
            final boolean excludeAll = selectedItems.isEmpty();
            for (final ApplicationData app : appData)
                app.setExcludedFromTunnel(excludeAll);
        }));
        return dialog;
    }

    private void setExclusionsAndDismiss() {
        final List<String> excludedApps = new ArrayList<>();
        for (final ApplicationData data : appData) {
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
