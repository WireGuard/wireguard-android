package com.wireguard.crypto;

import android.util.Base64;

import java.security.SecureRandom;

/**
 * Represents a Curve25519 keypair as used by WireGuard.
 */

public class Keypair {
    private static final int KEY_LENGTH = 32;
    public static final int KEY_STRING_LENGTH = 44;

    private static byte[] generatePrivateKey() {
        final SecureRandom secureRandom = new SecureRandom();
        final byte privateKey[] = new byte[KEY_LENGTH];
        secureRandom.nextBytes(privateKey);
        privateKey[0] &= 248;
        privateKey[31] &= 127;
        privateKey[31] |= 64;
        return privateKey;
    }

    private static byte[] generatePublicKey(byte privateKey[]) {
        final byte publicKey[] = new byte[KEY_LENGTH];
        Curve25519.eval(publicKey, 0, privateKey, null);
        return publicKey;
    }

    private static byte[] parseKey(String key) {
        final byte keyBytes[] = Base64.decode(key, Base64.NO_WRAP);
        if (keyBytes.length != KEY_LENGTH)
            throw new IndexOutOfBoundsException("Key is not the correct length");
        return keyBytes;
    }

    private static String unParseKey(byte keyBytes[]) {
        return Base64.encodeToString(keyBytes, Base64.NO_WRAP);
    }

    private final byte privateKey[];
    private final byte publicKey[];

    public Keypair() {
        this(generatePrivateKey());
    }

    private Keypair(byte privateKey[]) {
        this.privateKey = privateKey;
        publicKey = generatePublicKey(privateKey);
    }

    public Keypair(String privateKey) {
        this(parseKey(privateKey));
    }

    public String getPrivateKey() {
        return unParseKey(privateKey);
    }

    public String getPublicKey() {
        return unParseKey(publicKey);
    }
}
