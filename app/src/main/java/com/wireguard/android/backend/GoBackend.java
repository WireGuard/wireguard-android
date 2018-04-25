package com.wireguard.android.backend;

import android.content.Context;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.support.v4.util.ArraySet;
import android.util.Log;
import android.util.Pair;

import com.wireguard.android.Application;
import com.wireguard.android.model.Tunnel;
import com.wireguard.android.model.Tunnel.State;
import com.wireguard.android.model.Tunnel.Statistics;
import com.wireguard.android.model.TunnelManager;
import com.wireguard.config.Config;
import com.wireguard.config.IPCidr;
import com.wireguard.config.Interface;
import com.wireguard.config.Peer;
import com.wireguard.crypto.KeyEncoding;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Formatter;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import java9.util.concurrent.CompletableFuture;

public final class GoBackend implements Backend {
    private static final String TAG = "WireGuard/" + GoBackend.class.getSimpleName();

    static {
        System.loadLibrary("wg-go");
    }

    private Tunnel currentTunnel;
    private int currentTunnelHandle = -1;

    private Context context;

    public GoBackend(Context context) {
        this.context = context;
    }

    public void startVpnService() {
        context.startService(new Intent(context, VpnService.class));
    }

    public static class VpnService extends android.net.VpnService {
        @Override
        public void onCreate() {
            vpnService.complete(this);
            super.onCreate();
        }

        @Override
        public void onDestroy() {
            for (final Tunnel tunnel : Application.getComponent().getTunnelManager().getTunnels()) {
                if (tunnel != null && tunnel.getState() != State.DOWN)
                    tunnel.setState(State.DOWN);
            }
            vpnService = vpnService.newIncompleteFuture();
            super.onDestroy();
        }

        public Builder getBuilder() {
            return new Builder();
        }
    }

    private static CompletableFuture<VpnService> vpnService = new CompletableFuture<>();

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
            Log.i(TAG, "Bringing tunnel up");

            if (VpnService.prepare(context) != null)
                throw new Exception("VPN service not authorized by user");

            VpnService service;
            if (!vpnService.isDone())
                startVpnService();

            try {
                service = vpnService.get(2, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new Exception("Unable to start Android VPN service");
            }

            if (currentTunnelHandle != -1) {
                Log.w(TAG, "Tunnel already up");
                return;
            }

            // Build config
            Formatter fmt = new Formatter(new StringBuilder());
            final Interface iface = config.getInterface();
            fmt.format("replace_peers=true\n");
            if (iface.getPrivateKey() != null)
                fmt.format("private_key=%s\n", KeyEncoding.keyToHex(KeyEncoding.keyFromBase64(iface.getPrivateKey())));
            if (iface.getListenPort() != 0)
                fmt.format("listen_port=%d\n", config.getInterface().getListenPort());
            for (final Peer peer : config.getPeers()) {
                if (peer.getPublicKey() != null)
                    fmt.format("public_key=%s\n", KeyEncoding.keyToHex(KeyEncoding.keyFromBase64(peer.getPublicKey())));
                if (peer.getPreSharedKey() != null)
                    fmt.format("preshared_key=%s\n", KeyEncoding.keyToHex(KeyEncoding.keyFromBase64(peer.getPreSharedKey())));
                if (peer.getEndpoint() != null)
                    fmt.format("endpoint=%s\n", peer.getResolvedEndpointString());
                if (peer.getPersistentKeepalive() != 0)
                    fmt.format("persistent_keepalive_interval=%d\n", peer.getPersistentKeepalive());
                for (final IPCidr addr : peer.getAllowedIPs()) {
                    fmt.format("allowed_ip=%s\n", addr.toString());
                }
            }

            // Create the vpn tunnel with android API
            VpnService.Builder builder = service.getBuilder();
            builder.setSession(tunnel.getName());

            for (final IPCidr addr : config.getInterface().getAddresses())
                builder.addAddress(addr.getAddress(), addr.getCidr());

            for (final InetAddress addr : config.getInterface().getDnses())
                builder.addDnsServer(addr.getHostAddress());

            for (final Peer peer : config.getPeers()) {
                for (final IPCidr addr : peer.getAllowedIPs())
                    builder.addRoute(addr.getAddress(), addr.getCidr());
            }

            int mtu = config.getInterface().getMtu();
            if (mtu == 0)
                mtu = 1280;
            builder.setMtu(mtu);

            builder.setBlocking(true);
            ParcelFileDescriptor tun = builder.establish();
            if (tun == null)
                throw new Exception("Unable to create tun device");

            currentTunnelHandle = wgTurnOn(tunnel.getName(), tun.detachFd(), fmt.toString());
            if (currentTunnelHandle < 0)
                throw new Exception("Unable to turn tunnel on (wgTurnOn return " + currentTunnelHandle + ")");

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
}
