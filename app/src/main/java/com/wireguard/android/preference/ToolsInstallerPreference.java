package com.wireguard.android.preference;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.preference.Preference;
import android.system.OsConstants;
import android.util.AttributeSet;

import com.wireguard.android.Application;
import com.wireguard.android.Application.ApplicationComponent;
import com.wireguard.android.R;
import com.wireguard.android.util.AsyncWorker;
import com.wireguard.android.util.ToolsInstaller;

/**
 * Preference implementing a button that asynchronously runs {@code ToolsInstaller} and displays the
 * result as the preference summary.
 */

public class ToolsInstallerPreference extends Preference {
    private final AsyncWorker asyncWorker;
    private final ToolsInstaller toolsInstaller;
    private State state = State.INITIAL;

    @SuppressWarnings({"SameParameterValue", "WeakerAccess"})
    public ToolsInstallerPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        final ApplicationComponent applicationComponent = Application.getComponent();
        asyncWorker = applicationComponent.getAsyncWorker();
        toolsInstaller = applicationComponent.getToolsInstaller();
    }

    @Override
    public CharSequence getSummary() {
        return getContext().getString(state.messageResourceId);
    }

    @Override
    public CharSequence getTitle() {
        return getContext().getString(R.string.tools_installer_title);
    }

    @Override
    public void onAttached() {
        super.onAttached();
        asyncWorker.supplyAsync(toolsInstaller::areInstalled).whenComplete(this::onCheckResult);
    }

    private void onCheckResult(final Integer result, final Throwable throwable) {
        setState(throwable == null && result == OsConstants.EALREADY ?
                State.ALREADY : State.INITIAL);
    }

    @Override
    protected void onClick() {
        setState(State.WORKING);
        asyncWorker.supplyAsync(toolsInstaller::install).whenComplete(this::onInstallResult);
    }

    private void onInstallResult(final Integer result, final Throwable throwable) {
        final State nextState;
        if (throwable != null)
            nextState = State.FAILURE;
        else if (result == OsConstants.EXIT_SUCCESS)
            nextState = State.SUCCESS;
        else if (result == OsConstants.EALREADY)
            nextState = State.ALREADY;
        else
            nextState = State.FAILURE;
        setState(nextState);
    }

    private void setState(@NonNull final State state) {
        if (this.state == state)
            return;
        this.state = state;
        if (isEnabled() != state.shouldEnableView)
            setEnabled(state.shouldEnableView);
        notifyChanged();
    }

    private enum State {
        ALREADY(R.string.tools_installer_already, false),
        FAILURE(R.string.tools_installer_failure, true),
        INITIAL(R.string.tools_installer_initial, true),
        SUCCESS(R.string.tools_installer_success, false),
        WORKING(R.string.tools_installer_working, false);

        private final int messageResourceId;
        private final boolean shouldEnableView;

        State(final int messageResourceId, final boolean shouldEnableView) {
            this.messageResourceId = messageResourceId;
            this.shouldEnableView = shouldEnableView;
        }
    }
}
