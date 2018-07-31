/*
 * Copyright © 2015-2018 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.crypto;

import com.wireguard.android.Application;
import com.wireguard.android.R;

/**
 * This is a specialized constant-time base64 and hex implementation that resists side-channel attacks.
 */

@SuppressWarnings("MagicNumber")
public final class KeyEncoding {
    public static final int KEY_LENGTH = 32;
    public static final int KEY_LENGTH_BASE64 = 44;
    public static final int KEY_LENGTH_HEX = 64;
    private static final String KEY_LENGTH_BASE64_EXCEPTION_MESSAGE =
            Application.get().getString(R.string.key_length_base64_exception_message);
    private static final String KEY_LENGTH_EXCEPTION_MESSAGE =
            Application.get().getString(R.string.key_length_exception_message);
    private static final String KEY_LENGTH_HEX_EXCEPTION_MESSAGE =
            Application.get().getString(R.string.key_length_hex_exception_message);

    private KeyEncoding() {
        // Prevent instantiation.
    }

    private static int decodeBase64(final char[] src, final int srcOffset) {
        int val = 0;
        for (int i = 0; i < 4; ++i) {
            final char c = src[i + srcOffset];
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

    private static void encodeBase64(final byte[] src, final int srcOffset,
                                     final char[] dest, final int destOffset) {
        final byte[] input = {
                (byte) ((src[srcOffset] >>> 2) & 63),
                (byte) ((src[srcOffset] << 4 | ((src[1 + srcOffset] & 0xff) >>> 4)) & 63),
                (byte) ((src[1 + srcOffset] << 2 | ((src[2 + srcOffset] & 0xff) >>> 6)) & 63),
                (byte) ((src[2 + srcOffset]) & 63),
        };
        for (int i = 0; i < 4; ++i) {
            dest[i + destOffset] = (char) (input[i] + 'A'
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
        int ret = 0;
        for (i = 0; i < KEY_LENGTH / 3; ++i) {
            final int val = decodeBase64(input, i * 4);
            ret |= val >>> 31;
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
        ret |= (val >>> 31) | (val & 0xff);
        key[i * 3] = (byte) ((val >>> 16) & 0xff);
        key[i * 3 + 1] = (byte) ((val >>> 8) & 0xff);

        if (ret != 0)
            throw new IllegalArgumentException(KEY_LENGTH_BASE64_EXCEPTION_MESSAGE);
        return key;
    }

    public static byte[] keyFromHex(final String str) {
        final char[] input = str.toCharArray();
        final byte[] key = new byte[KEY_LENGTH];
        if (input.length != KEY_LENGTH_HEX)
            throw new IllegalArgumentException(KEY_LENGTH_HEX_EXCEPTION_MESSAGE);
        int ret = 0;

        for (int i = 0; i < KEY_LENGTH_HEX; i += 2) {
            int c;
            int cNum;
            int cNum0;
            int cAlpha;
            int cAlpha0;
            int cVal;
            final int cAcc;

            c = input[i];
            cNum = c ^ 48;
            cNum0 = ((cNum - 10) >>> 8) & 0xff;
            cAlpha = (c & ~32) - 55;
            cAlpha0 = (((cAlpha - 10) ^ (cAlpha - 16)) >>> 8) & 0xff;
            ret |= ((cNum0 | cAlpha0) - 1) >>> 8;
            cVal = (cNum0 & cNum) | (cAlpha0 & cAlpha);
            cAcc = cVal * 16;

            c = input[i + 1];
            cNum = c ^ 48;
            cNum0 = ((cNum - 10) >>> 8) & 0xff;
            cAlpha = (c & ~32) - 55;
            cAlpha0 = (((cAlpha - 10) ^ (cAlpha - 16)) >>> 8) & 0xff;
            ret |= ((cNum0 | cAlpha0) - 1) >>> 8;
            cVal = (cNum0 & cNum) | (cAlpha0 & cAlpha);
            key[i / 2] = (byte) (cAcc | cVal);
        }
        if (ret != 0)
            throw new IllegalArgumentException(KEY_LENGTH_HEX_EXCEPTION_MESSAGE);
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
