package com.wireguard.android.fragment;

import android.content.Context;
import android.databinding.DataBindingUtil;
import android.databinding.ViewDataBinding;
import android.util.Log;
import android.view.View;

import com.commonsware.cwac.crossport.design.widget.Snackbar;
import com.wireguard.android.R;
import com.wireguard.android.databinding.TunnelDetailFragmentBinding;
import com.wireguard.android.databinding.TunnelListItemBinding;
import com.wireguard.android.model.Tunnel;
import com.wireguard.android.model.Tunnel.State;
import com.wireguard.android.util.ExceptionLoggers;

/**
 * Helper method shared by TunnelListFragment and TunnelDetailFragment.
 */

public final class TunnelController {
    private static final String TAG = "WireGuard/" + TunnelController.class.getSimpleName();

    private TunnelController() {
        // Prevent instantiation.
    }

    public static void setTunnelState(final View view, final boolean checked) {
        final ViewDataBinding binding = DataBindingUtil.findBinding(view);
        final Tunnel tunnel;
        if (binding instanceof TunnelDetailFragmentBinding)
            tunnel = ((TunnelDetailFragmentBinding) binding).getTunnel();
        else if (binding instanceof TunnelListItemBinding)
            tunnel = ((TunnelListItemBinding) binding).getItem();
        else
            tunnel = null;
        if (tunnel == null) {
            Log.e(TAG, "setChecked() from a null tunnel", new IllegalStateException("No tunnel"));
            return;
        }
        tunnel.setState(State.of(checked)).whenComplete((state, throwable) -> {
            if (throwable == null)
                return;
            final Context context = view.getContext();
            final String error = ExceptionLoggers.unwrap(throwable).getMessage();
            final int messageResId = checked ? R.string.error_up : R.string.error_down;
            final String message = context.getString(messageResId, error);
            Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
            Log.e(TAG, message, throwable);
        });
    }
}
