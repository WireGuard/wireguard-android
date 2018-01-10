package com.wireguard.android.fragment;

import android.app.AlertDialog;
import android.content.Context;
import android.databinding.DataBindingUtil;
import android.databinding.ViewDataBinding;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.commonsware.cwac.crossport.design.widget.Snackbar;
import com.wireguard.android.R;
import com.wireguard.android.backend.WgQuickBackend;
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
            if (ExceptionLoggers.unwrap(throwable)
                    instanceof WgQuickBackend.ModuleNotLoadedException) {
                final String message = context.getString(R.string.not_supported_message);
                final String title = context.getString(R.string.not_supported_title);
                final AlertDialog dialog = new AlertDialog.Builder(context)
                        .setMessage(Html.fromHtml(message))
                        .setPositiveButton(R.string.ok, null)
                        .setTitle(title)
                        .show();
                // Make links work.
                ((TextView) dialog.findViewById(android.R.id.message))
                        .setMovementMethod(LinkMovementMethod.getInstance());
                Log.e(TAG, title, throwable);
            } else {
                final String error = ExceptionLoggers.unwrap(throwable).getMessage();
                final int messageResId = checked ? R.string.error_up : R.string.error_down;
                final String message = context.getString(messageResId, error);
                Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
                Log.e(TAG, message, throwable);
            }
        });
    }
}
