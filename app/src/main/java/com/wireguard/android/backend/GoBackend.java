/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.backend;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import androidx.annotation.Nullable;
import androidx.collection.ArraySet;
import android.util.Log;

import com.wireguard.android.Application;
import com.wireguard.android.R;
import com.wireguard.android.backend.Tunnel.State;
import com.wireguard.android.util.ExceptionLoggers;
import com.wireguard.android.util.SharedLibraryLoader;
import com.wireguard.config.Config;
import com.wireguard.config.InetNetwork;
import com.wireguard.config.Peer;
import com.wireguard.crypto.Key;
import com.wireguard.crypto.KeyFormatException;

import java.net.InetAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import java9.util.concurrent.CompletableFuture;

public final class GoBackend implements Backend {
    private static final String TAG = "WireGuard/" + GoBackend.class.getSimpleName();
    private static CompletableFuture<VpnService> vpnService = new CompletableFuture<>();
    public interface AlwaysOnCallback {
        void alwaysOnTriggered();
    }
    @Nullable private static AlwaysOnCallback alwaysOnCallback;
    public static void setAlwaysOnCallback(AlwaysOnCallback cb) {
        alwaysOnCallback = cb;
    }

    private final Context context;
    private final PendingIntent configurationIntent;
    @Nullable private Tunnel currentTunnel;
    @Nullable private Config currentConfig;
    private int currentTunnelHandle = -1;

    private final Set<TunnelStateChangeNotificationReceiver> notifiers = new HashSet<>();

    public GoBackend(final Context context, final PendingIntent configurationIntent) {
        SharedLibraryLoader.loadSharedLibrary(context, "wg-go");
        this.context = context;
        this.configurationIntent = configurationIntent;
    }

    private static native String wgGetConfig(int handle);

    private static native int wgGetSocketV4(int handle);

    private static native int wgGetSocketV6(int handle);

    private static native void wgTurnOff(int handle);

    private static native int wgTurnOn(String ifName, int tunFd, String settings);

    private static native String wgVersion();

    @Override
    public Set<String> getRunningTunnelNames() {
        if (currentTunnel != null) {
            final Set<String> runningTunnels = new ArraySet<>();
            runningTunnels.add(currentTunnel.getName());
            return runningTunnels;
        }
        return Collections.emptySet();
    }

    @Override
    public State getState(final Tunnel tunnel) {
        return currentTunnel == tunnel ? State.UP : State.DOWN;
    }

    @Override
    public Statistics getStatistics(final Tunnel tunnel) {
        final Statistics stats = new Statistics();
        if (tunnel != currentTunnel) {
            return stats;
        }
        final String config = wgGetConfig(currentTunnelHandle);
        Key key = null;
        long rx = 0, tx = 0;
        for (final String line : config.split("\\n")) {
            if (line.startsWith("public_key=")) {
                if (key != null)
                    stats.add(key, rx, tx);
                rx = 0;
                tx = 0;
                try {
                    key = Key.fromHex(line.substring(11));
                } catch (final KeyFormatException ignored) {
                    key = null;
                }
            } else if (line.startsWith("rx_bytes=")) {
                if (key == null)
                    continue;
                try {
                    rx = Long.parseLong(line.substring(9));
                } catch (final NumberFormatException ignored) {
                    rx = 0;
                }
            } else if (line.startsWith("tx_bytes=")) {
                if (key == null)
                    continue;
                try {
                    tx = Long.parseLong(line.substring(9));
                } catch (final NumberFormatException ignored) {
                    tx = 0;
                }
            }
        }
        if (key != null)
            stats.add(key, rx, tx);
        return stats;
    }

    @Override
    public String getTypePrettyName() {
        return context.getString(R.string.type_name_go_userspace);
    }

    @Override
    public String getVersion() {
        return wgVersion();
    }

    @Override
    public State setState(final Tunnel tunnel, State state, @Nullable final Config config) throws Exception {
        final State originalState = getState(tunnel);

        if (state == State.TOGGLE)
            state = originalState == State.UP ? State.DOWN : State.UP;
        if (state == originalState && tunnel == currentTunnel && config == currentConfig)
            return originalState;
        if (state == State.UP) {
            final Config originalConfig = currentConfig;
            final Tunnel originalTunnel = currentTunnel;
            if (currentTunnel != null)
                setStateInternal(currentTunnel, null, State.DOWN);
            try {
                setStateInternal(tunnel, config, state);
            } catch(final Exception e) {
                if (originalTunnel != null)
                    setStateInternal(originalTunnel, originalConfig, State.UP);
                throw e;
            }
        } else if (state == State.DOWN && tunnel == currentTunnel) {
            setStateInternal(tunnel, null, State.DOWN);
        }
        return getState(tunnel);
    }

    private void setStateInternal(final Tunnel tunnel, @Nullable final Config config, final State state)
            throws Exception {
        Log.i(TAG, "Bringing tunnel " + tunnel.getName() + " " + state);

        if (state == State.UP) {
            Objects.requireNonNull(config, context.getString(R.string.no_config_error));

            if (VpnService.prepare(context) != null)
                throw new Exception(context.getString(R.string.vpn_not_authorized_error));

            final VpnService service;
            if (!vpnService.isDone())
                startVpnService();

            try {
                service = vpnService.get(2, TimeUnit.SECONDS);
            } catch (final TimeoutException e) {
                throw new Exception(context.getString(R.string.vpn_start_error), e);
            }
            service.setOwner(this);

            if (currentTunnelHandle != -1) {
                Log.w(TAG, "Tunnel already up");
                return;
            }

            // Build config
            final String goConfig = config.toWgUserspaceString();

            // Create the vpn tunnel with android API
            final VpnService.Builder builder = service.getBuilder();
            builder.setSession(tunnel.getName());

            builder.setConfigureIntent(configurationIntent);

            for (final String excludedApplication : config.getInterface().getExcludedApplications())
                builder.addDisallowedApplication(excludedApplication);

            for (final InetNetwork addr : config.getInterface().getAddresses())
                builder.addAddress(addr.getAddress(), addr.getMask());

            for (final InetAddress addr : config.getInterface().getDnsServers())
                builder.addDnsServer(addr.getHostAddress());

            for (final Peer peer : config.getPeers()) {
                for (final InetNetwork addr : peer.getAllowedIps())
                    builder.addRoute(addr.getAddress(), addr.getMask());
            }

            builder.setMtu(config.getInterface().getMtu().orElse(1280));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                builder.setMetered(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
	        service.setUnderlyingNetworks(null);

            builder.setBlocking(true);
            try (final ParcelFileDescriptor tun = builder.establish()) {
                if (tun == null)
                    throw new Exception(context.getString(R.string.tun_create_error));
                Log.d(TAG, "Go backend v" + wgVersion());
                currentTunnelHandle = wgTurnOn(tunnel.getName(), tun.detachFd(), goConfig);
            }
            if (currentTunnelHandle < 0)
                throw new Exception(context.getString(R.string.tunnel_on_error, currentTunnelHandle));

            currentTunnel = tunnel;
            currentConfig = config;

            service.protect(wgGetSocketV4(currentTunnelHandle));
            service.protect(wgGetSocketV6(currentTunnelHandle));
        } else {
            if (currentTunnelHandle == -1) {
                Log.w(TAG, "Tunnel already down");
                return;
            }

            wgTurnOff(currentTunnelHandle);
            currentTunnel = null;
            currentTunnelHandle = -1;
            currentConfig = null;
        }

        for (final TunnelStateChangeNotificationReceiver notifier : notifiers)
            notifier.tunnelStateChange(tunnel, state);
    }

    private void startVpnService() {
        Log.d(TAG, "Requesting to start VpnService");
        context.startService(new Intent(context, VpnService.class));
    }

    @Override
    public void registerStateChangeNotification(final TunnelStateChangeNotificationReceiver receiver) {
        notifiers.add(receiver);
    }

    @Override
    public void unregisterStateChangeNotification(final TunnelStateChangeNotificationReceiver receiver) {
        notifiers.remove(receiver);
    }

    public static class VpnService extends android.net.VpnService {
        @Nullable private GoBackend owner;

        public void setOwner(final GoBackend owner) {
            this.owner = owner;
        }

        public Builder getBuilder() {
            return new Builder();
        }

        @Override
        public void onCreate() {
            vpnService.complete(this);
            super.onCreate();
        }

        @Override
        public void onDestroy() {
            if (owner != null) {
                final Tunnel tunnel = owner.currentTunnel;
                if (tunnel != null) {
                    if (owner.currentTunnelHandle != -1)
                        wgTurnOff(owner.currentTunnelHandle);
                    owner.currentTunnel = null;
                    owner.currentTunnelHandle = -1;
                    owner.currentConfig = null;
                    for (final TunnelStateChangeNotificationReceiver notifier : owner.notifiers)
                        notifier.tunnelStateChange(tunnel, State.DOWN);
                }
            }
            vpnService = vpnService.newIncompleteFuture();
            super.onDestroy();
        }

        @Override
        public int onStartCommand(@Nullable final Intent intent, final int flags, final int startId) {
            vpnService.complete(this);
            if (intent == null || intent.getComponent() == null || !intent.getComponent().getPackageName().equals(getPackageName())) {
                Log.d(TAG, "Service started by Always-on VPN feature");
                if (alwaysOnCallback != null)
                    alwaysOnCallback.alwaysOnTriggered();
            }
            return super.onStartCommand(intent, flags, startId);
        }
    }
}
