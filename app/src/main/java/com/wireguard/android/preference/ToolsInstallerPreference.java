package com.wireguard.android.preference;

import android.content.Context;
import android.preference.Preference;
import android.support.annotation.NonNull;
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

    public ToolsInstallerPreference(final Context context) {
        this(context, null);
    }

    private static State mapResultToState(final int scriptResult) {
        if (scriptResult == OsConstants.EXIT_SUCCESS)
            return State.SUCCESS;
        else if (scriptResult == OsConstants.EALREADY)
            return State.ALREADY;
        else
            return State.FAILURE;
    }

    @Override
    public CharSequence getSummary() {
        return getContext().getString(state.messageResourceId);
    }

    @Override
    public CharSequence getTitle() {
        return getContext().getString(getTitleRes());
    }

    @Override
    public int getTitleRes() {
        return R.string.tools_installer_title;
    }

    @Override
    protected void onAttachedToActivity() {
        super.onAttachedToActivity();
        asyncWorker.supplyAsync(toolsInstaller::areInstalled)
                .thenAccept(installed -> setState(installed ? State.ALREADY : State.INITIAL));
    }

    @Override
    protected void onClick() {
        setState(State.WORKING);
        asyncWorker.supplyAsync(toolsInstaller::install)
                .thenApply(ToolsInstallerPreference::mapResultToState)
                .thenAccept(this::setState);
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
