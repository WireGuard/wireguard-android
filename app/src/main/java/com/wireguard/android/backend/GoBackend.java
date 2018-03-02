package com.wireguard.android.backend;

import android.content.Context;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.support.v4.util.ArraySet;
import android.util.Log;

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
        context.startService(new Intent(context, VpnService.class));
    }

    public static class VpnService extends android.net.VpnService {
        @Override
        public void onCreate() {
            super.onCreate();
            vpnService = this;
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            vpnService = null;
        }

        public Builder getBuilder() {
            return new Builder();
        }
    }

    private static VpnService vpnService = null;

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
        if (string.charAt(0) == '[') { // ipv6
            int end = string.indexOf(']');
            if (end == -1 || string.charAt(end+1) != ':')
                throw new Exception("Invalid endpoint " + string);

            part = new String[2];
            part[0] = string.substring(1, end);
            part[1] = string.substring(end + 2);
            Log.d(TAG, "PP " + part[0] + " " + part[1]);
        } else { // ipv4
            part = string.split(":", 2);
        }

        if (part.length != 2 || part[0].isEmpty() || part[1].isEmpty())
            throw new Exception("Invalid endpoint " + string);

        InetAddress address = InetAddress.getByName(part[0]);
        int port = Integer.valueOf(part[1]);
        InetSocketAddress socketAddress = new InetSocketAddress(address, port);
        if (socketAddress.getAddress() instanceof Inet4Address)
            return socketAddress.getAddress().getHostAddress() + ":" + socketAddress.getPort();
        else
            return "[" + socketAddress.getAddress().getHostAddress() + "]:" + socketAddress.getPort();
    }

    private void setStateInternal(final Tunnel tunnel, final Config config, final State state)
            throws Exception {

        if (state == State.UP) {
            Log.i(TAG, "Bringing tunnel up");

            if (VpnService.prepare(context) != null)
                throw new Exception("VPN service not authorized by user");

            if (vpnService == null)
                throw new Exception("Android VPN service is not running");

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
                if (peer.getEndpoint() != null) {
                    fmt.format("endpoint=%s\n", parseEndpoint(peer.getEndpoint()));
                }
                if (peer.getPersistentKeepalive() != null)
                    fmt.format("persistent_keepalive_interval=%d\n", Integer.parseInt(peer.getPersistentKeepalive()));
                if (peer.getAllowedIPs() != null) {
                    for (final String allowedIp : peer.getAllowedIPs().split(" *, *")) {
                        fmt.format("allowed_ip=%s\n", allowedIp);
                    }
                }
            }

            // Create the vpn tunnel with android API
            VpnService.Builder builder = vpnService.getBuilder();
            builder.setSession(tunnel.getName());
            InetAddress address = InetAddress.getByName(config.getInterface().getAddress());
            builder.addAddress(address.getHostAddress(), (address instanceof Inet4Address) ? 32 : 128);
            if (config.getInterface().getDns() != null)
                builder.addDnsServer(config.getInterface().getDns());

            for (final Peer peer : config.getPeers()) {
                if (peer.getAllowedIPs() != null) {
                    for (final String allowedIp : peer.getAllowedIPs().split(" *, *")) {
                        String[] part = allowedIp.split("/", 2);
                        builder.addRoute(part[0], Integer.parseInt(part[1]));
                    }
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

            vpnService.protect(wgGetSocketV4(currentTunnelHandle));
            vpnService.protect(wgGetSocketV6(currentTunnelHandle));
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
