/*
 * Copyright © 2017-2018 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.config;

import android.annotation.SuppressLint;

import com.wireguard.android.Application;
import com.wireguard.android.R;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

import javax.annotation.Nullable;

public class InetEndpoint {
    private final String host;
    private final int port;
    @Nullable private InetAddress resolvedHost;

    public InetEndpoint(@Nullable final String endpoint) {
        if (endpoint.indexOf('/') != -1 || endpoint.indexOf('?') != -1 || endpoint.indexOf('#') != -1)
            throw new IllegalArgumentException(Application.get().getString(R.string.tunnel_error_forbidden_endpoint_chars));
        final URI uri;
        try {
            uri = new URI("wg://" + endpoint);
        } catch (final URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
        host = uri.getHost();
        port = uri.getPort();
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @SuppressLint("DefaultLocale")
    public String getResolvedEndpoint() throws UnknownHostException {
        if (resolvedHost == null) {
            final InetAddress[] candidates = InetAddress.getAllByName(host);
            if (candidates.length == 0)
                throw new UnknownHostException(host);
            for (final InetAddress addr : candidates) {
                if (addr instanceof Inet4Address) {
                    resolvedHost = addr;
                    break;
                }
            }
            if (resolvedHost == null)
                resolvedHost = candidates[0];
        }
        return String.format(resolvedHost instanceof Inet6Address ?
                "[%s]:%d" : "%s:%d", resolvedHost.getHostAddress(), port);
    }

    @SuppressLint("DefaultLocale")
    public String getEndpoint() {
        return String.format(host.contains(":") && !host.contains("[") ?
                "[%s]:%d" : "%s:%d", host, port);
    }
}
