/*
 * Copyright © 2017-2021 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.backend;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.OsConstants;
import android.util.Log;

import com.wireguard.android.backend.BackendException.Reason;
import com.wireguard.android.backend.Tunnel.State;
import com.wireguard.android.util.SharedLibraryLoader;
import com.wireguard.config.Config;
import com.wireguard.config.InetEndpoint;
import com.wireguard.config.InetNetwork;
import com.wireguard.config.Interface;
import com.wireguard.config.Peer;
import com.wireguard.crypto.Key;
import com.wireguard.crypto.KeyFormatException;
import com.wireguard.util.NonNullForAll;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.Nullable;
import androidx.collection.ArraySet;

/**
 * Implementation of {@link Backend} that uses the wireguard-go userspace implementation to provide
 * WireGuard tunnels.
 */
@NonNullForAll
public final class GoBackend implements Backend {
    private static final int DNS_RESOLUTION_RETRIES = 10;
    private static final int WRITE_SESSION_ID_MILLIS = 1000*60*60;
    private static final int KEEP_ALIVE_DELAY_MILLIS = 1000*5;
    private static final int KEEP_ALIVE_INTERVAL_MILLIS = 1000*60*60;
    private static final int EXPIRATION_GUARD_DELAY_MILLIS = 1000*10;
    private static final int EXPIRATION_GUARD_INTERVAL_MILLIS = 1000;
    private static final String TAG = "WireGuard/GoBackend";
    private static final String KEEP_ALIVE_URL = "https://%s/api/dlp/v1/gateway/connection";
    private static final Pattern ExpiresAtPattern = Pattern.compile("expiresAt`:`([0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]+\\+[0-9]{2}:[0-9]{2})".replace('`', '"'));
    private static final Pattern SessionIdPattern = Pattern.compile("\\{`id`:`([a-z0-9\\-]+)`".replace('`', '"'));
    private static GhettoCompletableFuture<VpnService> VpnServiceInstance = new GhettoCompletableFuture<>();
    @Nullable private static AlwaysOnCallback AlwaysOnCallback;
    @Nullable private static Timer KeepAliveTimer;
    @Nullable private static Timer SessionIdTimer;
    @Nullable private static Timer ExpirationGuardTimer;

    private final Context context;
    @Nullable private Config currentConfig;
    @Nullable private Tunnel currentTunnel;
    private int currentTunnelHandle = -1;
    @Nullable private String expiresAt;

    /**
     * Public constructor for GoBackend.
     *
     * @param context An Android {@link Context}
     */
    public GoBackend(final Context context) {
        SharedLibraryLoader.loadSharedLibrary(context, "wg-go");
        this.context = context;
    }

    private void sendKeepAlive() {
        if (currentTunnel == null) {
            Log.e(TAG, "No currentTunnel");
            return;
        }
        if (currentConfig == null) {
            Log.e(TAG, "No currentConfig");
            return;
        }

        Interface wgInterface = currentConfig.getInterface();
        Optional<String> accessTokenOptional = wgInterface.getAccessToken();
        final String accessToken = accessTokenOptional.orElse("");
        if (accessToken.isEmpty()) {
            Log.e(TAG, "No accessToken");
            return;
        }

        Optional<String> keepAliveDomainOptional = wgInterface.getKeepAliveDomain();
        final String urlString = String.format(KEEP_ALIVE_URL, keepAliveDomainOptional.orElse("www.os33.net"));
        Log.d(TAG, "Try Keep-ALive");
        HttpURLConnection httpConnection = null;
        try {
            httpConnection = (HttpURLConnection) new URL(urlString).openConnection();
            httpConnection.setInstanceFollowRedirects(false);
            httpConnection.setRequestMethod("PUT");
            httpConnection.setRequestProperty("Authorization", "OAuth2 " + accessToken);
            httpConnection.setRequestProperty("Content-Type", "application/json");
            httpConnection.setRequestProperty("Content-Length", "0");

            int responseCode = httpConnection.getResponseCode();
            String response = responseCode + " " + httpConnection.getResponseMessage();
            Log.d(TAG, "Keep-Alive sent, response = " + response);

            // 403:The feature is not enabled for the user
            // 404: User session does not contain a protected connection
            if(responseCode == HttpURLConnection.HTTP_FORBIDDEN ||
               responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                setStateInternal(currentTunnel, null, State.DOWN);
                return;
            }

            try (InputStream inputStream = httpConnection.getInputStream();
                 InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                 BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
                while (true) {
                    String line = bufferedReader.readLine();
                    if (line == null || line.trim().isEmpty()) {
                        break;
                    }

                    Matcher expiresAtMatcher = ExpiresAtPattern.matcher(line);
                    if (expiresAtMatcher.find()) {
                        this.expiresAt = expiresAtMatcher.group(1);
                    }
                    Matcher sessionIdMatcher = SessionIdPattern.matcher(line);
                    if (sessionIdMatcher.find()) {
                        currentConfig.setSessionId(sessionIdMatcher.group(1));
                    }
                }
            }
        } catch (final Exception exception) {
            Log.e(TAG, "Error sending Keep-Alive " + exception);
        } finally {
            if (httpConnection != null) {
                httpConnection.disconnect();
            }
        }
    }

    private void StopTunnel () throws Exception {
        setStateInternal(currentTunnel, null, State.DOWN);
    }

    class KeepAliveTask extends TimerTask{
        @Override public void run() {
            sendKeepAlive();
        }
    }

    class SessionIdTask extends TimerTask{
        @Override public void run() {
            if(currentConfig != null) {
                String sessionId = currentConfig.getSessionId();
                if (sessionId == null || sessionId.isEmpty() || sessionId.trim().isEmpty()) {
                    sessionId = "UNKNOWN!";
                }
                Log.i(TAG, "PCG SessionID =  " + sessionId);
            }
        }
    }

    class ExpirationGuardTask extends TimerTask {
        private final GoBackend owner;

        public ExpirationGuardTask (GoBackend owner) {
            this.owner = owner;
        }

        @Override public void run() {
            try {
                if(owner.expiresAt == null || owner.expiresAt.isEmpty()) return;
                if (OffsetDateTime.parse(owner.expiresAt).toInstant().compareTo(Instant.now()) <= 0) {
                    owner.StopTunnel();
                }
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Set a {@link AlwaysOnCallback} to be invoked when {@link VpnService} is started by the
     * system's Always-On VPN mode.
     *
     * @param cb Callback to be invoked
     */
    public static void setAlwaysOnCallback(final AlwaysOnCallback cb) {
        AlwaysOnCallback = cb;
    }

    @Nullable private static native String wgGetConfig(int handle);

    private static native int wgGetSocketV4(int handle);

    private static native int wgGetSocketV6(int handle);

    private static native void wgTurnOff(int handle);

    private static native int wgTurnOn(String ifName, int tunFd, String settings);

    private static native String wgVersion();

    /**
     * Method to get the names of running tunnels.
     *
     * @return A set of string values denoting names of running tunnels.
     */
    @Override
    public Set<String> getRunningTunnelNames() {
        if (currentTunnel != null) {
            final Set<String> runningTunnels = new ArraySet<>();
            runningTunnels.add(currentTunnel.getName());
            return runningTunnels;
        }
        return Collections.emptySet();
    }

    /**
     * Get the associated {@link State} for a given {@link Tunnel}.
     *
     * @param tunnel The tunnel to examine the state of.
     * @return {@link State} associated with the given tunnel.
     */
    @Override
    public State getState(final Tunnel tunnel) {
        return currentTunnel == tunnel ? State.UP : State.DOWN;
    }

    /**
     * Get the associated {@link Statistics} for a given {@link Tunnel}.
     *
     * @param tunnel The tunnel to retrieve statistics for.
     * @return {@link Statistics} associated with the given tunnel.
     */
    @Override
    public Statistics getStatistics(final Tunnel tunnel) {
        final Statistics stats = new Statistics();
        if (tunnel != currentTunnel || currentTunnelHandle == -1)
            return stats;
        final String config = wgGetConfig(currentTunnelHandle);
        if (config == null)
            return stats;
        Key key = null;
        long rx = 0;
        long tx = 0;
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

    /**
     * Get the version of the underlying wireguard-go library.
     *
     * @return {@link String} value of the version of the wireguard-go library.
     */
    @Override
    public String getVersion() {
        return wgVersion();
    }

    /**
     * Change the state of a given {@link Tunnel}, optionally applying a given {@link Config}.
     *
     * @param tunnel The tunnel to control the state of.
     * @param state  The new state for this tunnel. Must be {@code UP}, {@code DOWN}, or
     *               {@code TOGGLE}.
     * @param config The configuration for this tunnel, may be null if state is {@code DOWN}.
     * @return {@link State} of the tunnel after state changes are applied.
     * @throws Exception Exception raised while changing tunnel state.
     */
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
            } catch (final Exception e) {
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
        Log.i(TAG, "Bringing tunnel " + tunnel.getName() + ' ' + state);

        if (state == State.UP) {
            if (config == null)
                throw new BackendException(Reason.TUNNEL_MISSING_CONFIG);

            if (VpnService.prepare(context) != null)
                throw new BackendException(Reason.VPN_NOT_AUTHORIZED);

            final VpnService service;
            if (!VpnServiceInstance.isDone()) {
                Log.d(TAG, "Requesting to start VpnService");
                context.startService(new Intent(context, VpnService.class));
            }

            try {
                service = VpnServiceInstance.get(2, TimeUnit.SECONDS);
            } catch (final TimeoutException e) {
                final Exception be = new BackendException(Reason.UNABLE_TO_START_VPN);
                be.initCause(e);
                throw be;
            }
            service.setOwner(this);

            if (currentTunnelHandle != -1) {
                Log.w(TAG, "Tunnel is already up");
                return;
            }

            dnsRetry: for (int i = 0; i < DNS_RESOLUTION_RETRIES; ++i) {
                // Pre-resolve IPs so they're cached when building the userspace string
                for (final Peer peer : config.getPeers()) {
                    final InetEndpoint ep = peer.getEndpoint().orElse(null);
                    if (ep == null)
                        continue;
                    if (ep.getResolved().orElse(null) == null) {
                        if (i < DNS_RESOLUTION_RETRIES - 1) {
                            Log.w(TAG, "DNS host \"" + ep.getHost() + "\" failed to resolve; trying again");
                            Thread.sleep(1000);
                            continue dnsRetry;
                        } else
                            throw new BackendException(Reason.DNS_RESOLUTION_FAILURE, ep.getHost());
                    }
                }
                break;
            }

            // Build config
            final String goConfig = config.toWgUserspaceString();

            // Create the vpn tunnel with android API
            final VpnService.Builder builder = service.getBuilder();
            builder.setSession(tunnel.getName());

            for (final String excludedApplication : config.getInterface().getExcludedApplications())
                builder.addDisallowedApplication(excludedApplication);

            for (final String includedApplication : config.getInterface().getIncludedApplications())
                builder.addAllowedApplication(includedApplication);

            for (final InetNetwork addr : config.getInterface().getAddresses())
                builder.addAddress(addr.getAddress(), addr.getMask());

            for (final InetAddress addr : config.getInterface().getDnsServers())
                builder.addDnsServer(addr.getHostAddress());

            for (final String dnsSearchDomain : config.getInterface().getDnsSearchDomains())
                builder.addSearchDomain(dnsSearchDomain);

            boolean sawDefaultRoute = false;
            for (final Peer peer : config.getPeers()) {
                for (final InetNetwork addr : peer.getAllowedIps()) {
                    if (addr.getMask() == 0)
                        sawDefaultRoute = true;
                    builder.addRoute(addr.getAddress(), addr.getMask());
                }
            }

            // "Kill-switch" semantics
            if (!(sawDefaultRoute && config.getPeers().size() == 1)) {
                builder.allowFamily(OsConstants.AF_INET);
                builder.allowFamily(OsConstants.AF_INET6);
            }

            builder.setMtu(config.getInterface().getMtu().orElse(1280));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                builder.setMetered(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                service.setUnderlyingNetworks(null);

            builder.setBlocking(true);
            try (final ParcelFileDescriptor tun = builder.establish()) {
                if (tun == null)
                    throw new BackendException(Reason.TUN_CREATION_ERROR);
                Log.d(TAG, "Go backend " + wgVersion());
                currentTunnelHandle = wgTurnOn(tunnel.getName(), tun.detachFd(), goConfig);
            }
            if (currentTunnelHandle < 0)
                throw new BackendException(Reason.GO_ACTIVATION_ERROR_CODE, currentTunnelHandle);

            currentTunnel = tunnel;
            currentConfig = config;

            service.protect(wgGetSocketV4(currentTunnelHandle));
            service.protect(wgGetSocketV6(currentTunnelHandle));
            enqueueKeepAlive();
            enqueueExpirationGuard();
            enqueueWriteSessionIdToLog();
        } else {
            if (currentTunnelHandle == -1) {
                Log.w(TAG, "Tunnel is already down");
                return;
            }
            int handleToClose = currentTunnelHandle;
            currentTunnel = null;
            currentTunnelHandle = -1;
            currentConfig = null;
            wgTurnOff(handleToClose);
            cancelKeepAlive();
            cancelExpirationGuard();
            cancelWriteSessionId();
        }

        tunnel.onStateChange(state);
    }

    /**
     * Callback for {@link GoBackend} that is invoked when {@link VpnService} is started by the
     * system's Always-On VPN mode.
     */
    public interface AlwaysOnCallback {
        void alwaysOnTriggered();
    }

    // TODO: When we finally drop API 21 and move to API 24, delete this and replace with the ordinary CompletableFuture.
    private static final class GhettoCompletableFuture<V> {
        private final LinkedBlockingQueue<V> completion = new LinkedBlockingQueue<>(1);
        private final FutureTask<V> result = new FutureTask<>(completion::peek);

        public boolean complete(final V value) {
            final boolean offered = completion.offer(value);
            if (offered)
                result.run();
            return offered;
        }

        public V get() throws ExecutionException, InterruptedException {
            return result.get();
        }

        public V get(final long timeout, final TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException {
            return result.get(timeout, unit);
        }

        public boolean isDone() {
            return !completion.isEmpty();
        }

        public GhettoCompletableFuture<V> newIncompleteFuture() {
            return new GhettoCompletableFuture<>();
        }
    }

    //added for VPN Keep-Alive
    private void enqueueKeepAlive() {
        if (KeepAliveTimer == null) {
            KeepAliveTimer = new Timer();
        }

        KeepAliveTimer.schedule(new KeepAliveTask(), KEEP_ALIVE_DELAY_MILLIS, KEEP_ALIVE_INTERVAL_MILLIS);
    }

    private void enqueueWriteSessionIdToLog() {
        if (SessionIdTimer == null) {
            SessionIdTimer = new Timer();
        }

        SessionIdTimer.schedule(new SessionIdTask(), 0, WRITE_SESSION_ID_MILLIS);
    }

    private void enqueueExpirationGuard() {
        if (ExpirationGuardTimer == null) {
            ExpirationGuardTimer = new Timer();
        }

        ExpirationGuardTimer.schedule(new ExpirationGuardTask(this), EXPIRATION_GUARD_DELAY_MILLIS, EXPIRATION_GUARD_INTERVAL_MILLIS);
    }

    //added for VPN Keep-Alive
    private static void cancelKeepAlive() {
        if (KeepAliveTimer != null) {
            KeepAliveTimer.cancel();
            KeepAliveTimer = null;
        }
    }

    private static void cancelExpirationGuard(){
        if(ExpirationGuardTimer != null) {
            ExpirationGuardTimer.cancel();
            ExpirationGuardTimer = null;
        }
    }

    private static void cancelWriteSessionId(){
        if(SessionIdTimer != null) {
            SessionIdTimer.cancel();
            SessionIdTimer = null;
        }
    }

    /**
     * {@link android.net.VpnService} implementation for {@link GoBackend}
     */
    public static class VpnService extends android.net.VpnService {
        @Nullable private GoBackend owner;

        public Builder getBuilder() {
            return new Builder();
        }

        @Override
        public void onCreate() {
            VpnServiceInstance.complete(this);
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
                    tunnel.onStateChange(State.DOWN);
                }
            }
            VpnServiceInstance = VpnServiceInstance.newIncompleteFuture();
            super.onDestroy();
        }

        @Override
        public int onStartCommand(@Nullable final Intent intent, final int flags, final int startId) {
            VpnServiceInstance.complete(this);
            if (intent == null || intent.getComponent() == null || !intent.getComponent().getPackageName().equals(getPackageName())) {
                Log.d(TAG, "Service started by Always-on VPN feature");

                if (AlwaysOnCallback != null) {
                    AlwaysOnCallback.alwaysOnTriggered();
                }
            }
            return super.onStartCommand(intent, flags, startId);
        }

        public void setOwner(final GoBackend owner) {
            this.owner = owner;
        }
    }
}