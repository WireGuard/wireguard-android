/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.backend;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import androidx.annotation.Nullable;
import androidx.collection.ArraySet;
import android.util.Log;

import com.wireguard.android.Application;
import com.wireguard.android.R;
import com.wireguard.android.activity.MainActivity;
import com.wireguard.android.model.Tunnel;
import com.wireguard.android.model.Tunnel.State;
import com.wireguard.android.model.Tunnel.Statistics;
import com.wireguard.android.util.ExceptionLoggers;
import com.wireguard.android.util.SharedLibraryLoader;
import com.wireguard.config.Config;
import com.wireguard.config.InetNetwork;
import com.wireguard.config.Peer;

import java.net.InetAddress;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import java9.util.concurrent.CompletableFuture;

public final class GoBackend implements Backend {
    private static final String TAG = "WireGuard/" + GoBackend.class.getSimpleName();
    private static CompletableFuture<VpnService> vpnService = new CompletableFuture<>();

    private final Context context;
    @Nullable private Tunnel currentTunnel;
    private int currentTunnelHandle = -1;

    public GoBackend(final Context context) {
        SharedLibraryLoader.loadSharedLibrary(context, "wg-go");
        this.context = context;
    }

    private static native int wgGetSocketV4(int handle);

    private static native int wgGetSocketV6(int handle);

    private static native void wgTurnOff(int handle);

    private static native int wgTurnOn(String ifName, int tunFd, String settings);

    private static native String wgVersion();

    @Override
    public Config applyConfig(final Tunnel tunnel, final Config config) throws Exception {
        if (tunnel.getState() == State.UP) {
            // Restart the tunnel to apply the new config.
            setStateInternal(tunnel, tunnel.getConfig(), State.DOWN);
            try {
                setStateInternal(tunnel, config, State.UP);
            } catch (final Exception e) {
                // The new configuration didn't work, so try to go back to the old one.
                setStateInternal(tunnel, tunnel.getConfig(), State.UP);
                throw e;
            }
        }
        return config;
    }

    @Override
    public Set<String> enumerate() {
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
        return new Statistics();
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
    public State setState(final Tunnel tunnel, State state) throws Exception {
        final State originalState = getState(tunnel);
        if (state == State.TOGGLE)
            state = originalState == State.UP ? State.DOWN : State.UP;
        if (state == originalState)
            return originalState;
        if (state == State.UP && currentTunnel != null)
            throw new IllegalStateException(context.getString(R.string.multiple_tunnels_error));
        Log.d(TAG, "Changing tunnel " + tunnel.getName() + " to state " + state);
        setStateInternal(tunnel, tunnel.getConfig(), state);
        return getState(tunnel);
    }

    private void setStateInternal(final Tunnel tunnel, @Nullable final Config config, final State state)
            throws Exception {

        if (state == State.UP) {
            Log.i(TAG, "Bringing tunnel up");

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

            if (currentTunnelHandle != -1) {
                Log.w(TAG, "Tunnel already up");
                return;
            }

            // Build config
            final String goConfig = config.toWgUserspaceString();

            // Create the vpn tunnel with android API
            final VpnService.Builder builder = service.getBuilder();
            builder.setSession(tunnel.getName());

            final Intent configureIntent = new Intent(context, MainActivity.class);
            configureIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            builder.setConfigureIntent(PendingIntent.getActivity(context, 0, configureIntent, 0));

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

            service.protect(wgGetSocketV4(currentTunnelHandle));
            service.protect(wgGetSocketV6(currentTunnelHandle));
        } else {
            Log.i(TAG, "Bringing tunnel down");

            if (currentTunnelHandle == -1) {
                Log.w(TAG, "Tunnel already down");
                return;
            }

            wgTurnOff(currentTunnelHandle);
            currentTunnel = null;
            currentTunnelHandle = -1;
        }
    }

    private void startVpnService() {
        Log.d(TAG, "Requesting to start VpnService");
        context.startService(new Intent(context, VpnService.class));
    }

    public static class VpnService extends android.net.VpnService {
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
            Application.getTunnelManager().getTunnels().thenAccept(tunnels -> {
                for (final Tunnel tunnel : tunnels) {
                    if (tunnel != null && tunnel.getState() != State.DOWN)
                        tunnel.setState(State.DOWN);
                }
            });

            vpnService = vpnService.newIncompleteFuture();
            super.onDestroy();
        }

        @Override
        public int onStartCommand(@Nullable final Intent intent, final int flags, final int startId) {
            vpnService.complete(this);
            if (intent == null || intent.getComponent() == null || !intent.getComponent().getPackageName().equals(getPackageName())) {
                Log.d(TAG, "Service started by Always-on VPN feature");
                Application.getTunnelManager().restoreState(true).whenComplete(ExceptionLoggers.D);
            }
            return super.onStartCommand(intent, flags, startId);
        }

    }
}
