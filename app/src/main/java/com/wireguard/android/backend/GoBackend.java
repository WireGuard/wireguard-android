package com.wireguard.android.backend;

import android.content.Context;
import android.support.v4.util.ArraySet;
import android.util.Log;

import com.wireguard.android.model.Tunnel;
import com.wireguard.android.model.Tunnel.State;
import com.wireguard.android.model.Tunnel.Statistics;
import com.wireguard.config.Config;
import com.wireguard.config.Interface;
import com.wireguard.config.Peer;
import com.wireguard.crypto.KeyEncoding;

import java.util.Collections;
import java.util.Formatter;
import java.util.Set;

public final class GoBackend implements Backend {
    private static final String TAG = "WireGuard/" + GoBackend.class.getSimpleName();

    static {
        System.loadLibrary("wg-go");
    }

    private final Context context;
    private Tunnel currentTunnel;

    public GoBackend(final Context context) {
        this.context = context;
    }

    private static native int wgGetSocketV4(int handle);

    private static native int wgGetSocketV6(int handle);

    private static native void wgTurnOff(int handle);

    private static native int wgTurnOn(String ifName, int tunFd, String settings);

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
    public State setState(final Tunnel tunnel, State state) throws Exception {
        final State originalState = getState(tunnel);
        if (state == State.TOGGLE)
            state = originalState == State.UP ? State.DOWN : State.UP;
        if (state == originalState)
            return originalState;
        if (state == State.UP && currentTunnel != null)
            throw new IllegalStateException("Only one userspace tunnel can run at a time");
        Log.d(TAG, "Changing tunnel " + tunnel.getName() + " to state " + state);
        setStateInternal(tunnel, tunnel.getConfig(), state);
        return getState(tunnel);
    }

    private void setStateInternal(final Tunnel tunnel, final Config config, final State state)
            throws Exception {
        if (state == State.UP) {
            // Do something (context.startService()...).
            currentTunnel = tunnel;

            Formatter fmt = new Formatter(new StringBuilder());
            final Interface iface = config.getInterface();
            fmt.format("replace_peers=true\n");
            if (iface.getPrivateKey() != null)
                fmt.format("private_key=%s\n", KeyEncoding.keyToHex(KeyEncoding.keyFromBase64(iface.getPrivateKey())));
            if (iface.getListenPort() != null)
                fmt.format("listen_port=%d\n", Integer.parseInt(config.getInterface().getListenPort()));
            for (final Peer peer : config.getPeers()) {
                if (peer.getPublicKey() != null)
                    fmt.format("public_key=%s\n", KeyEncoding.keyToHex(KeyEncoding.keyFromBase64(peer.getPublicKey())));
                if (peer.getPreSharedKey() != null)
                    fmt.format("preshared_key=%s\n", KeyEncoding.keyToHex(KeyEncoding.keyFromBase64(peer.getPreSharedKey())));
                if (peer.getEndpoint() != null)
                    fmt.format("endpoint=%s\n", peer.getEndpoint());
                if (peer.getPersistentKeepalive() != null)
                    fmt.format("persistent_keepalive_interval=%d\n", Integer.parseInt(peer.getPersistentKeepalive()));
                if (peer.getAllowedIPs() != null) {
                    for (final String allowedIp : peer.getAllowedIPs().split(" *, *")) {
                        fmt.format("allowed_ip=%s\n", allowedIp);
                    }
                }
            }
            wgTurnOn(tunnel.getName(), -1, fmt.toString());
        } else {
            // Do something else.
            currentTunnel = null;
        }
    }
}
