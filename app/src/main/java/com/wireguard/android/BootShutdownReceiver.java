package com.wireguard.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.wireguard.android.model.TunnelManager;
import com.wireguard.android.util.ExceptionLoggers;

public class BootShutdownReceiver extends BroadcastReceiver {
    private static final String TAG = "WireGuard/" + BootShutdownReceiver.class.getSimpleName();

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final String action = intent.getAction();
        if (action == null)
            return;
        final TunnelManager tunnelManager = Application.getComponent().getTunnelManager();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            Log.d(TAG, "Broadcast receiver restoring state (boot)");
            tunnelManager.restoreState().whenComplete(ExceptionLoggers.D);
        } else if (Intent.ACTION_SHUTDOWN.equals(action)) {
            Log.d(TAG, "Broadcast receiver saving state (shutdown)");
            tunnelManager.saveState();
        }
    }
}
