/*
 * Copyright © 2017-2018 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.crypto;

import java.security.SecureRandom;

/**
 * Represents a Curve25519 key pair as used by WireGuard.
 * <p>
 * Instances of this class are immutable.
 */
public class KeyPair {
    private final Key privateKey;
    private final Key publicKey;

    /**
     * Creates a key pair using a newly-generated private key.
     */
    public KeyPair() {
        this(generatePrivateKey());
    }

    /**
     * Creates a key pair using an existing private key.
     *
     * @param privateKey a private key, used to derive the public key
     */
    public KeyPair(final Key privateKey) {
        this.privateKey = privateKey;
        publicKey = generatePublicKey(privateKey);
    }

    /**
     * Generates a private key using the system's {@link SecureRandom} number generator.
     *
     * @return a well-formed random private key
     */
    @SuppressWarnings("MagicNumber")
    private static Key generatePrivateKey() {
        final SecureRandom secureRandom = new SecureRandom();
        final byte[] privateKey = new byte[Key.Format.BINARY.getLength()];
        secureRandom.nextBytes(privateKey);
        privateKey[0] &= 248;
        privateKey[31] &= 127;
        privateKey[31] |= 64;
        return Key.fromBytes(privateKey);
    }

    /**
     * Generates a public key from an existing private key.
     *
     * @param privateKey a private key
     * @return a well-formed public key that corresponds to the supplied private key
     */
    private static Key generatePublicKey(final Key privateKey) {
        final byte[] publicKey = new byte[Key.Format.BINARY.getLength()];
        Curve25519.eval(publicKey, 0, privateKey.getBytes(), null);
        return Key.fromBytes(publicKey);
    }

    /**
     * Returns the private key from the key pair.
     *
     * @return the private key
     */
    public Key getPrivateKey() {
        return privateKey;
    }

    /**
     * Returns the public key from the key pair.
     *
     * @return the public key
     */
    public Key getPublicKey() {
        return publicKey;
    }
}
