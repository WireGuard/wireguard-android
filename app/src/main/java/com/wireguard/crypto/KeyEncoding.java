/*
 * Copyright © 2015-2018 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package com.wireguard.crypto;

/**
 * This is a specialized constant-time base64 and hex implementation that resists side-channel attacks.
 */

@SuppressWarnings("MagicNumber")
public final class KeyEncoding {
    public static final int KEY_LENGTH = 32;
    public static final int KEY_LENGTH_BASE64 = 44;
    public static final int KEY_LENGTH_HEX = 64;
    private static final String KEY_LENGTH_BASE64_EXCEPTION_MESSAGE =
            "WireGuard base64 keys must be 44 characters encoding 32 bytes";
    private static final String KEY_LENGTH_EXCEPTION_MESSAGE =
            "WireGuard keys must be 32 bytes";
    private static final String KEY_LENGTH_HEX_EXCEPTION_MESSAGE =
            "WireGuard hex keys must be 64 characters encoding 32 bytes";

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

    public static byte[] keyFromHex(final String str) {
        final char[] input = str.toCharArray();
        final byte[] key = new byte[KEY_LENGTH];
        if (input.length != KEY_LENGTH_HEX)
            throw new IllegalArgumentException(KEY_LENGTH_HEX_EXCEPTION_MESSAGE);

        int c_acc = 0;
        int state = 0;

        for (int i = 0; i < KEY_LENGTH_HEX; ++i) {
            final int c = input[i];
            final int c_num = c ^ 48;
            final int c_num0 = (c_num - 10) >> 8;
            final int c_alpha = (c & ~32) - 55;
            final int c_alpha0 = ((c_alpha - 10) ^ (c_alpha - 16)) >> 8;
            if ((c_num0 | c_alpha0) == 0)
                throw new IllegalArgumentException(KEY_LENGTH_HEX_EXCEPTION_MESSAGE);
            final int c_val = (c_num0 & c_num) | (c_alpha0 & c_alpha);
            if (state == 0)
                c_acc = c_val * 16;
            else
                key[i / 2] = (byte) (c_acc | c_val);
            state = ~state;
        }
        return key;
    }

    public static String keyToBase64(final byte[] key) {
        final char[] output = new char[KEY_LENGTH_BASE64];
        if (key.length != KEY_LENGTH)
            throw new IllegalArgumentException(KEY_LENGTH_EXCEPTION_MESSAGE);
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

    public static String keyToHex(final byte[] key) {
        final char[] output = new char[KEY_LENGTH_HEX];
        if (key.length != KEY_LENGTH)
            throw new IllegalArgumentException(KEY_LENGTH_EXCEPTION_MESSAGE);
        for (int i = 0; i < KEY_LENGTH; ++i) {
            output[i * 2] = (char) (87 + (key[i] >> 4 & 0xf)
                    + ((((key[i] >> 4 & 0xf) - 10) >> 8) & ~38));
            output[i * 2 + 1] = (char) (87 + (key[i] & 0xf)
                    + ((((key[i] & 0xf) - 10) >> 8) & ~38));
        }
        return new String(output);
    }
}
