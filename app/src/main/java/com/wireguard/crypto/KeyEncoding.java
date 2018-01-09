/* Copyright (C) 2015-2017 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved. */

package com.wireguard.crypto;

/**
 * This is a specialized constant-time base64 implementation that resists side-channel attacks.
 */

@SuppressWarnings("MagicNumber")
public final class KeyEncoding {
    public static final int KEY_LENGTH = 32;
    public static final int KEY_LENGTH_BASE64 = 44;
    private static final String KEY_LENGTH_BASE64_EXCEPTION_MESSAGE =
            "WireGuard base64 keys must be 44 characters encoding 32 bytes";

    private KeyEncoding() {
        // Prevent instantiation.
    }

    private static int decodeBase64(final char[] src, final int src_offset) {
        int val = 0;
        for (int i = 0; i < 4; ++i) {
            final char c = src[i + src_offset];
            val |= (-1
                    + ((((('A' - 1) - c) & (c - ('Z' + 1))) >>> 8) & (c - 64))
                    + ((((('a' - 1) - c) & (c - ('z' + 1))) >>> 8) & (c - 70))
                    + ((((('0' - 1) - c) & (c - ('9' + 1))) >>> 8) & (c + 5))
                    + ((((('+' - 1) - c) & (c - ('+' + 1))) >>> 8) & 63)
                    + ((((('/' - 1) - c) & (c - ('/' + 1))) >>> 8) & 64)
            ) << (18 - 6 * i);
        }
        return val;
    }

    private static void encodeBase64(final byte[] src, final int src_offset,
                                     final char[] dest, final int dest_offset) {
        final byte[] input = {
                (byte) ((src[src_offset] >>> 2) & 63),
                (byte) ((src[src_offset] << 4 | ((src[1 + src_offset] & 0xff) >>> 4)) & 63),
                (byte) ((src[1 + src_offset] << 2 | ((src[2 + src_offset] & 0xff) >>> 6)) & 63),
                (byte) ((src[2 + src_offset]) & 63),
        };
        for (int i = 0; i < 4; ++i) {
            dest[i + dest_offset] = (char) (input[i] + 'A'
                    + (((25 - input[i]) >>> 8) & 6)
                    - (((51 - input[i]) >>> 8) & 75)
                    - (((61 - input[i]) >>> 8) & 15)
                    + (((62 - input[i]) >>> 8) & 3));
        }
    }

    public static byte[] keyFromBase64(final String str) {
        final char[] input = str.toCharArray();
        final byte[] key = new byte[KEY_LENGTH];
        if (input.length != KEY_LENGTH_BASE64 || input[KEY_LENGTH_BASE64 - 1] != '=')
            throw new IllegalArgumentException(KEY_LENGTH_BASE64_EXCEPTION_MESSAGE);
        int i;
        for (i = 0; i < KEY_LENGTH / 3; ++i) {
            final int val = decodeBase64(input, i * 4);
            if (val < 0)
                throw new IllegalArgumentException(KEY_LENGTH_BASE64_EXCEPTION_MESSAGE);
            key[i * 3] = (byte) ((val >>> 16) & 0xff);
            key[i * 3 + 1] = (byte) ((val >>> 8) & 0xff);
            key[i * 3 + 2] = (byte) (val & 0xff);
        }
        final char[] endSegment = {
                input[i * 4],
                input[i * 4 + 1],
                input[i * 4 + 2],
                'A',
        };
        final int val = decodeBase64(endSegment, 0);
        if (val < 0 || (val & 0xff) != 0)
            throw new IllegalArgumentException(KEY_LENGTH_BASE64_EXCEPTION_MESSAGE);
        key[i * 3] = (byte) ((val >>> 16) & 0xff);
        key[i * 3 + 1] = (byte) ((val >>> 8) & 0xff);
        return key;
    }

    public static String keyToBase64(final byte[] key) {
        final char[] output = new char[KEY_LENGTH_BASE64];
        if (key.length != KEY_LENGTH)
            throw new IllegalArgumentException("WireGuard keys must be 32 bytes");
        int i;
        for (i = 0; i < KEY_LENGTH / 3; ++i)
            encodeBase64(key, i * 3, output, i * 4);
        final byte[] endSegment = {
                key[i * 3],
                key[i * 3 + 1],
                0,
        };
        encodeBase64(endSegment, 0, output, i * 4);
        output[KEY_LENGTH_BASE64 - 1] = '=';
        return new String(output);
    }
}
