package com.wireguard.crypto;

import android.util.Base64;

import java.security.SecureRandom;

/**
 * Represents a Curve25519 keypair as used by WireGuard.
 */

public class Keypair {
    private static byte[] generatePrivateKey() {
        final SecureRandom secureRandom = new SecureRandom();
        final byte[] privateKey = new byte[KeyEncoding.WG_KEY_LEN];
        secureRandom.nextBytes(privateKey);
        privateKey[0] &= 248;
        privateKey[31] &= 127;
        privateKey[31] |= 64;
        return privateKey;
    }

    private static byte[] generatePublicKey(byte[] privateKey) {
        final byte[] publicKey = new byte[KeyEncoding.WG_KEY_LEN];
        Curve25519.eval(publicKey, 0, privateKey, null);
        return publicKey;
    }

    private final byte[] privateKey;
    private final byte[] publicKey;

    public Keypair() {
        this(generatePrivateKey());
    }

    private Keypair(byte[] privateKey) {
        this.privateKey = privateKey;
        publicKey = generatePublicKey(privateKey);
    }

    public Keypair(String privateKey) {
        this(KeyEncoding.keyFromBase64(privateKey));
    }

    public String getPrivateKey() {
        return KeyEncoding.keyToBase64(privateKey);
    }

    public String getPublicKey() {
        return KeyEncoding.keyToBase64(publicKey);
    }
}
