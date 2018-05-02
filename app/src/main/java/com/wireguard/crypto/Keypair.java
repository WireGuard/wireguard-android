/*
 * Copyright © 2018 Samuel Holland <samuel@sholland.org>
 * Copyright © 2018 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package com.wireguard.crypto;

import java.security.SecureRandom;

/**
 * Represents a Curve25519 keypair as used by WireGuard.
 */

public class Keypair {
    private final byte[] privateKey;
    private final byte[] publicKey;

    public Keypair() {
        this(generatePrivateKey());
    }

    private Keypair(final byte[] privateKey) {
        this.privateKey = privateKey;
        publicKey = generatePublicKey(privateKey);
    }

    public Keypair(final String privateKey) {
        this(KeyEncoding.keyFromBase64(privateKey));
    }

    @SuppressWarnings("MagicNumber")
    private static byte[] generatePrivateKey() {
        final SecureRandom secureRandom = new SecureRandom();
        final byte[] privateKey = new byte[KeyEncoding.KEY_LENGTH];
        secureRandom.nextBytes(privateKey);
        privateKey[0] &= 248;
        privateKey[31] &= 127;
        privateKey[31] |= 64;
        return privateKey;
    }

    private static byte[] generatePublicKey(final byte[] privateKey) {
        final byte[] publicKey = new byte[KeyEncoding.KEY_LENGTH];
        Curve25519.eval(publicKey, 0, privateKey, null);
        return publicKey;
    }

    public String getPrivateKey() {
        return KeyEncoding.keyToBase64(privateKey);
    }

    public String getPublicKey() {
        return KeyEncoding.keyToBase64(publicKey);
    }
}
