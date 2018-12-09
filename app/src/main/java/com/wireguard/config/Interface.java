/*
 * Copyright © 2017-2018 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.config;

import android.support.annotation.Nullable;

import com.wireguard.crypto.Key;
import com.wireguard.crypto.KeyPair;

import java.net.InetAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import java9.util.Lists;
import java9.util.Optional;
import java9.util.stream.Collectors;
import java9.util.stream.Stream;
import java9.util.stream.StreamSupport;

/**
 * Represents the configuration for a WireGuard interface (an [Interface] block). Interfaces must
 * have a private key (used to initialize a {@code KeyPair}), and may optionally have several other
 * attributes.
 * <p>
 * Instances of this class are immutable.
 */
public final class Interface {
    private static final int MAX_UDP_PORT = 65535;
    private static final int MIN_UDP_PORT = 0;

    private final Set<InetNetwork> addresses;
    private final Set<InetAddress> dnsServers;
    private final Set<String> excludedApplications;
    private final KeyPair keyPair;
    private final Optional<Integer> listenPort;
    private final Optional<Integer> mtu;

    private Interface(final Builder builder) {
        // Defensively copy to ensure immutability even if the Builder is reused.
        addresses = Collections.unmodifiableSet(new LinkedHashSet<>(builder.addresses));
        dnsServers = Collections.unmodifiableSet(new LinkedHashSet<>(builder.dnsServers));
        excludedApplications = Collections.unmodifiableSet(new LinkedHashSet<>(builder.excludedApplications));
        keyPair = Objects.requireNonNull(builder.keyPair, "Interfaces must have a private key");
        listenPort = builder.listenPort;
        mtu = builder.mtu;
    }

    /**
     * Parses an series of "KEY = VALUE" lines into an {@code Interface}. Throws
     * {@link ParseException} if the input is not well-formed or contains unknown attributes.
     *
     * @param lines An iterable sequence of lines, containing at least a private key attribute
     * @return An {@code Interface} with all of the attributes from {@code lines} set
     */
    public static Interface parse(final Iterable<? extends CharSequence> lines) throws ParseException {
        final Builder builder = new Builder();
        for (final CharSequence line : lines) {
            final Attribute attribute = Attribute.parse(line)
                    .orElseThrow(() -> new ParseException("[Interface]", line, "Syntax error"));
            switch (attribute.getKey().toLowerCase(Locale.ENGLISH)) {
                case "address":
                    builder.parseAddresses(attribute.getValue());
                    break;
                case "dns":
                    builder.parseDnsServers(attribute.getValue());
                    break;
                case "excludedapplications":
                    builder.parseExcludedApplications(attribute.getValue());
                    break;
                case "listenport":
                    builder.parseListenPort(attribute.getValue());
                    break;
                case "mtu":
                    builder.parseMtu(attribute.getValue());
                    break;
                case "privatekey":
                    builder.parsePrivateKey(attribute.getValue());
                    break;
                default:
                    throw new ParseException("[Interface]", attribute.getKey(), "Unknown attribute");
            }
        }
        return builder.build();
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof Interface))
            return false;
        final Interface other = (Interface) obj;
        return addresses.equals(other.addresses)
                && dnsServers.equals(other.dnsServers)
                && excludedApplications.equals(other.excludedApplications)
                && keyPair.equals(other.keyPair)
                && listenPort.equals(other.listenPort)
                && mtu.equals(other.mtu);
    }

    /**
     * Returns the set of IP addresses assigned to the interface.
     *
     * @return a set of {@link InetNetwork}s
     */
    public Set<InetNetwork> getAddresses() {
        // The collection is already immutable.
        return addresses;
    }

    /**
     * Returns the set of DNS servers associated with the interface.
     *
     * @return a set of {@link InetAddress}es
     */
    public Set<InetAddress> getDnsServers() {
        // The collection is already immutable.
        return dnsServers;
    }

    /**
     * Returns the set of applications excluded from using the interface.
     *
     * @return a set of package names
     */
    public Set<String> getExcludedApplications() {
        // The collection is already immutable.
        return excludedApplications;
    }

    /**
     * Returns the public/private key pair used by the interface.
     *
     * @return a key pair
     */
    public KeyPair getKeyPair() {
        return keyPair;
    }

    /**
     * Returns the UDP port number that the WireGuard interface will listen on.
     *
     * @return a UDP port number, or {@code Optional.empty()} if none is configured
     */
    public Optional<Integer> getListenPort() {
        return listenPort;
    }

    /**
     * Returns the MTU used for the WireGuard interface.
     *
     * @return the MTU, or {@code Optional.empty()} if none is configured
     */
    public Optional<Integer> getMtu() {
        return mtu;
    }

    @Override
    public int hashCode() {
        int hash = 1;
        hash = 31 * hash + addresses.hashCode();
        hash = 31 * hash + dnsServers.hashCode();
        hash = 31 * hash + excludedApplications.hashCode();
        hash = 31 * hash + keyPair.hashCode();
        hash = 31 * hash + listenPort.hashCode();
        hash = 31 * hash + mtu.hashCode();
        return hash;
    }

    /**
     * Converts the {@code Interface} into a string suitable for debugging purposes. The {@code
     * Interface} is identified by its public key and (if set) the port used for its UDP socket.
     *
     * @return A concise single-line identifier for the {@code Interface}
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("(Interface ");
        sb.append(keyPair.getPublicKey().toBase64());
        listenPort.ifPresent(lp -> sb.append(" @").append(lp));
        sb.append(')');
        return sb.toString();
    }

    /**
     * Converts the {@code Interface} into a string suitable for inclusion in a {@code wg-quick}
     * configuration file.
     *
     * @return The {@code Interface} represented as a series of "Key = Value" lines
     */
    public String toWgQuickString() {
        final StringBuilder sb = new StringBuilder();
        if (!addresses.isEmpty())
            sb.append("Address = ").append(Attribute.join(addresses)).append('\n');
        if (!dnsServers.isEmpty()) {
            final List<String> dnsServerStrings = StreamSupport.stream(dnsServers)
                    .map(InetAddress::getHostAddress)
                    .collect(Collectors.toUnmodifiableList());
            sb.append("DNS = ").append(Attribute.join(dnsServerStrings)).append('\n');
        }
        if (!excludedApplications.isEmpty())
            sb.append("ExcludedApplications = ").append(Attribute.join(excludedApplications)).append('\n');
        listenPort.ifPresent(lp -> sb.append("ListenPort = ").append(lp).append('\n'));
        mtu.ifPresent(m -> sb.append("MTU = ").append(m).append('\n'));
        sb.append("PrivateKey = ").append(keyPair.getPrivateKey().toBase64()).append('\n');
        return sb.toString();
    }

    /**
     * Serializes the {@code Interface} for use with the WireGuard cross-platform userspace API.
     * Note that not all attributes are included in this representation.
     *
     * @return the {@code Interface} represented as a series of "KEY=VALUE" lines
     */
    public String toWgUserspaceString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("private_key=").append(keyPair.getPrivateKey().toHex()).append('\n');
        listenPort.ifPresent(lp -> sb.append("listen_port=").append(lp).append('\n'));
        return sb.toString();
    }

    @SuppressWarnings("UnusedReturnValue")
    public static final class Builder {
        // Defaults to an empty set.
        private final Set<InetNetwork> addresses = new LinkedHashSet<>();
        // Defaults to an empty set.
        private final Set<InetAddress> dnsServers = new LinkedHashSet<>();
        // Defaults to an empty set.
        private final Set<String> excludedApplications = new LinkedHashSet<>();
        // No default; must be provided before building.
        @Nullable private KeyPair keyPair;
        // Defaults to not present.
        private Optional<Integer> listenPort = Optional.empty();
        // Defaults to not present.
        private Optional<Integer> mtu = Optional.empty();

        public Builder addAddress(final InetNetwork address) {
            addresses.add(address);
            return this;
        }

        public Builder addAddresses(final Collection<InetNetwork> addresses) {
            this.addresses.addAll(addresses);
            return this;
        }

        public Builder addDnsServer(final InetAddress dnsServer) {
            dnsServers.add(dnsServer);
            return this;
        }

        public Builder addDnsServers(final Collection<? extends InetAddress> dnsServers) {
            this.dnsServers.addAll(dnsServers);
            return this;
        }

        public Interface build() {
            return new Interface(this);
        }

        public Builder excludeApplication(final String application) {
            excludedApplications.add(application);
            return this;
        }

        public Builder excludeApplications(final Collection<String> applications) {
            excludedApplications.addAll(applications);
            return this;
        }

        public Builder parseAddresses(final CharSequence addresses) throws ParseException {
            try {
                final List<InetNetwork> parsed = Stream.of(Attribute.split(addresses))
                        .map(InetNetwork::parse)
                        .collect(Collectors.toUnmodifiableList());
                return addAddresses(parsed);
            } catch (final IllegalArgumentException e) {
                throw new ParseException("Address", addresses, e);
            }
        }

        public Builder parseDnsServers(final CharSequence dnsServers) throws ParseException {
            try {
                final List<InetAddress> parsed = Stream.of(Attribute.split(dnsServers))
                        .map(InetAddresses::parse)
                        .collect(Collectors.toUnmodifiableList());
                return addDnsServers(parsed);
            } catch (final IllegalArgumentException e) {
                throw new ParseException("DNS", dnsServers, e);
            }
        }

        public Builder parseExcludedApplications(final CharSequence apps) throws ParseException {
            try {
                return excludeApplications(Lists.of(Attribute.split(apps)));
            } catch (final IllegalArgumentException e) {
                throw new ParseException("ExcludedApplications", apps, e);
            }
        }

        public Builder parseListenPort(final String listenPort) throws ParseException {
            try {
                return setListenPort(Integer.parseInt(listenPort));
            } catch (final IllegalArgumentException e) {
                throw new ParseException("ListenPort", listenPort, e);
            }
        }

        public Builder parseMtu(final String mtu) throws ParseException {
            try {
                return setMtu(Integer.parseInt(mtu));
            } catch (final IllegalArgumentException e) {
                throw new ParseException("MTU", mtu, e);
            }
        }

        public Builder parsePrivateKey(final String privateKey) throws ParseException {
            try {
                return setKeyPair(new KeyPair(Key.fromBase64(privateKey)));
            } catch (final Key.KeyFormatException e) {
                throw new ParseException("PrivateKey", "(omitted)", e);
            }
        }

        public Builder setKeyPair(final KeyPair keyPair) {
            this.keyPair = keyPair;
            return this;
        }

        public Builder setListenPort(final int listenPort) {
            if (listenPort < MIN_UDP_PORT || listenPort > MAX_UDP_PORT)
                throw new IllegalArgumentException("ListenPort must be a valid UDP port number");
            this.listenPort = listenPort == 0 ? Optional.empty() : Optional.of(listenPort);
            return this;
        }

        public Builder setMtu(final int mtu) {
            if (mtu < 0)
                throw new IllegalArgumentException("MTU must not be negative");
            this.mtu = mtu == 0 ? Optional.empty() : Optional.of(mtu);
            return this;
        }
    }
}
