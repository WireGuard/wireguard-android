/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.config;

import com.wireguard.util.NonNullForAll;

import org.xbill.DNS.DClass;
import org.xbill.DNS.ExtendedResolver;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.SRVRecord;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import androidx.annotation.Nullable;


/**
 * An external endpoint (host and port) used to connect to a WireGuard {@link Peer}.
 * <p>
 * Instances of this class are externally immutable.
 */
@NonNullForAll
public final class InetEndpoint {
    private static final Pattern BARE_IPV6 = Pattern.compile("^[^\\[\\]]*:[^\\[\\]]*");
    private static final Pattern FORBIDDEN_CHARACTERS = Pattern.compile("[/?#]");

    private final String host;
    private final boolean isResolved;
    private final Object lock = new Object();
    private final int port;
    private Instant lastResolution = Instant.EPOCH;
    @Nullable private InetEndpoint resolved;

    private InetEndpoint(final String host, final boolean isResolved, final int port) {
        this.host = host;
        this.isResolved = isResolved;
        this.port = port;
    }

    public static InetEndpoint parse(final String endpoint) throws ParseException {
        if (FORBIDDEN_CHARACTERS.matcher(endpoint).find())
            throw new ParseException(InetEndpoint.class, endpoint, "Forbidden characters");
        if (endpoint.contains("_")) {
            // SRV records
            final String host = endpoint.split(":")[0];
            return new InetEndpoint(host, false, 0);
        }
        final URI uri;
        try {
            uri = new URI("wg://" + endpoint);
        } catch (final URISyntaxException e) {
            throw new ParseException(InetEndpoint.class, endpoint, e);
        }
        if (uri.getPort() < 0 || uri.getPort() > 65535)
            throw new ParseException(InetEndpoint.class, endpoint, "Missing/invalid port number");
        try {
            InetAddresses.parse(uri.getHost());
            // Parsing ths host as a numeric address worked, so we don't need to do DNS lookups.
            return new InetEndpoint(uri.getHost(), true, uri.getPort());
        } catch (final ParseException ignored) {
            // Failed to parse the host as a numeric address, so it must be a DNS hostname/FQDN.
            return new InetEndpoint(uri.getHost(), false, uri.getPort());
        }
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof InetEndpoint))
            return false;
        final InetEndpoint other = (InetEndpoint) obj;
        return host.equals(other.host) && port == other.port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    /**
     * Generate an {@code InetEndpoint} instance with the same port and the host resolved using DNS
     * to a numeric address. If the host is already numeric, the existing instance may be returned.
     * Because this function may perform network I/O, it must not be called from the main thread.
     *
     * @return the resolved endpoint, or {@link Optional#empty()}
     */
    public Optional<InetEndpoint> getResolved() {
        if (isResolved)
            return Optional.of(this);
        synchronized (lock) {
            //TODO(zx2c4): Implement a real timeout mechanism using DNS TTL
            final long ttlTimeout = Duration.between(lastResolution, Instant.now()).toSeconds();
            if (ttlTimeout > 60) {
                resolved = null;
                final String[] target = {host};
                final int[] targetPort = {port};
                if (host.contains("_")) {
                    // SRV records
                    try {
                        final Lookup lookup = new Lookup(host, Type.SRV, DClass.IN);
                        final Resolver resolver1 = new SimpleResolver("223.5.5.5");
                        final Resolver resolver2 = new SimpleResolver("223.6.6.6");
                        final Resolver[] resolvers = {resolver1, resolver2};
                        final Resolver extendedResolver = new ExtendedResolver(resolvers);
                        lookup.setResolver(extendedResolver);
                        lookup.setCache(null);
                        final Record[] records = lookup.run();
                        if (lookup.getResult() == Lookup.SUCCESSFUL) {
                            for (final Record record : records) {
                                final SRVRecord srv = (SRVRecord) record;
                                try {
                                    target[0] = srv.getTarget().toString(true);
                                    targetPort[0] = srv.getPort();
                                    InetAddresses.parse(target[0]);
                                    // Parsing ths host as a numeric address worked, so we don't need to do DNS lookups.
                                    resolved = new InetEndpoint(target[0], true, targetPort[0]);
                                } catch (final ParseException ignored) {
                                    // Failed to parse the host as a numeric address, so it must be a DNS hostname/FQDN.
                                }
                                // use the first SRV record and break out of loop
                                break;
                            }
                        } else {
                            System.out.println("SRV lookup failed: " + lookup.getErrorString());
                        }
                    } catch (final TextParseException | UnknownHostException e) {
                        System.out.println("SRV lookup failed: " + e.getMessage());
                    }
                }
                if (resolved == null) {
                    try {
                        // Prefer v4 endpoints over v6 to work around DNS64 and IPv6 NAT issues.
                        final InetAddress[] candidates = InetAddress.getAllByName(target[0]);
                        InetAddress address = candidates[0];
                        for (final InetAddress candidate : candidates) {
                            if (candidate instanceof Inet4Address) {
                                address = candidate;
                                break;
                            }
                        }
                        resolved = new InetEndpoint(address.getHostAddress(), true, targetPort[0]);
                        lastResolution = Instant.now();
                    } catch (final UnknownHostException e) {
                        System.out.println("DNS lookup failed: " + e.getMessage());
                    }
                }
            }
            return Optional.ofNullable(resolved);
        }
    }

    @Override
    public int hashCode() {
        return host.hashCode() ^ port;
    }

    @Override
    public String toString() {
        final boolean isBareIpv6 = isResolved && BARE_IPV6.matcher(host).matches();
        // Only show the port if it's non-zero
        if (port > 0)
            return (isBareIpv6 ? '[' + host + ']' : host) + ':' + port;
        return (isBareIpv6 ? '[' + host + ']' : host);
    }
}
