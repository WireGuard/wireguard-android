package com.wireguard.android.backend;

import android.content.Context;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.support.v4.util.ArraySet;
import android.util.Log;
import android.util.Pair;

import com.wireguard.android.model.Tunnel;
import com.wireguard.android.model.Tunnel.State;
import com.wireguard.android.model.Tunnel.Statistics;
import com.wireguard.config.Config;
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

    private String parseEndpoint(String string) throws Exception {
        String[] part;
        if (string.charAt(0) == '[') {
            int end = string.indexOf(']');
            if (end == -1 || end >= string.length() - 2 || string.charAt(end + 1) != ':')
                throw new Exception("Invalid endpoint " + string);

            part = new String[2];
            part[0] = string.substring(1, end);
            part[1] = string.substring(end + 2);
        } else {
            part = string.split(":", 2);
        }

        if (part.length != 2 || part[0].isEmpty() || part[1].isEmpty())
            throw new Exception("Invalid endpoint " + string);

        InetAddress address = InetAddress.getByName(part[0]);
        int port = -1;
        try {
            port = Integer.parseInt(part[1], 10);
        } catch (Exception e) {
        }
        if (port < 1)
            throw new Exception("Invalid endpoint port " + part[1]);
        InetSocketAddress socketAddress = new InetSocketAddress(address, port);
        if (socketAddress.getAddress() instanceof Inet4Address)
            return socketAddress.getAddress().getHostAddress() + ":" + socketAddress.getPort();
        else
            return "[" + socketAddress.getAddress().getHostAddress() + "]:" + socketAddress.getPort();
    }

    private Pair<String, Integer> parseAddressWithCidr(String in) {
        int cidr = -1;
        String addr = in;
        int slash = in.lastIndexOf('/');
        if (slash != -1 && slash < in.length() - 1) {
            try {
                cidr = Integer.parseInt(in.substring(slash + 1), 10);
                addr = in.substring(0, slash);
            } catch (Exception e) {
            }
        }
        boolean isV6 = addr.indexOf(':') != -1;
        if (isV6 && (cidr > 128 || cidr < 0))
            cidr = 128;
        else if (!isV6 && (cidr > 32 || cidr < 0))
            cidr = 32;
        return new Pair<>(addr, cidr);
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
            if (iface.getListenPort() != null)
                fmt.format("listen_port=%d\n", Integer.parseInt(config.getInterface().getListenPort()));
            for (final Peer peer : config.getPeers()) {
                if (peer.getPublicKey() != null)
                    fmt.format("public_key=%s\n", KeyEncoding.keyToHex(KeyEncoding.keyFromBase64(peer.getPublicKey())));
                if (peer.getPreSharedKey() != null)
                    fmt.format("preshared_key=%s\n", KeyEncoding.keyToHex(KeyEncoding.keyFromBase64(peer.getPreSharedKey())));
                if (peer.getEndpoint() != null)
                    fmt.format("endpoint=%s\n", parseEndpoint(peer.getEndpoint()));
                if (peer.getPersistentKeepalive() != null)
                    fmt.format("persistent_keepalive_interval=%d\n", Integer.parseInt(peer.getPersistentKeepalive(), 10));
                for (final String addr : peer.getAllowedIPs()) {
                    final Pair<String, Integer> addressCidr = parseAddressWithCidr(addr);
                    fmt.format("allowed_ip=%s\n", addressCidr.first + "/" + addressCidr.second);
                }
            }

            // Create the vpn tunnel with android API
            VpnService.Builder builder = service.getBuilder();
            builder.setSession(tunnel.getName());

            for (final String addr : config.getInterface().getAddresses()) {
                final Pair<String, Integer> addressCidr = parseAddressWithCidr(addr);
                final InetAddress address = InetAddress.getByName(addressCidr.first);
                builder.addAddress(address.getHostAddress(), addressCidr.second);
            }

            for (final String addr : config.getInterface().getDnses()) {
                final InetAddress address = InetAddress.getByName(addr);
                builder.addDnsServer(address.getHostAddress());
            }

            for (final Peer peer : config.getPeers()) {
                for (final String addr : peer.getAllowedIPs()) {
                    final Pair<String, Integer> addressCidr = parseAddressWithCidr(addr);
                    builder.addRoute(addressCidr.first, addressCidr.second);
                }
            }

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
