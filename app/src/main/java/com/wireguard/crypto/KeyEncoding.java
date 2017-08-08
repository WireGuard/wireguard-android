/* Copyright (C) 2015-2017 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 *
 * This is a specialized constant-time base64 implementation that resists side-channel attacks.
 */

package com.wireguard.crypto;

public class KeyEncoding {
	public static final int WG_KEY_LEN = 32;
	public static final int WG_KEY_LEN_BASE64 = 44;

	private static void encodeBase64(char dest[], final int dest_offset, final byte src[], final int src_offset) {
		final byte input[] = { (byte)((src[0 + src_offset] >>> 2) & 63),
				       (byte)(((src[0 + src_offset] << 4) | ((src[1 + src_offset] & 0xff) >>> 4)) & 63),
				       (byte)(((src[1 + src_offset] << 2) | ((src[2 + src_offset] & 0xff) >>> 6)) & 63),
				       (byte)(src[2 + src_offset] & 63) };
		for (int i = 0; i < 4; ++i)
			dest[i + dest_offset] = (char)(input[i] + 'A'
						+ (((25 - input[i]) >>> 8) & 6)
						- (((51 - input[i]) >>> 8) & 75)
						- (((61 - input[i]) >>> 8) & 15)
						+ (((62 - input[i]) >>> 8) & 3));
	}

	public static String keyToBase64(final byte key[]) {
		if (key.length != WG_KEY_LEN)
			throw new IllegalArgumentException("WireGuard keys must be 32 bytes");
		final char base64[] = new char[WG_KEY_LEN_BASE64];
		int i;
		for (i = 0; i < WG_KEY_LEN / 3; ++i)
			encodeBase64(base64, i * 4, key, i * 3);
		final byte endSegment[] = { key[i * 3 + 0], key[i * 3 + 1], 0 };
		encodeBase64(base64, i * 4, endSegment, 0);
		base64[WG_KEY_LEN_BASE64 - 1] = '=';
		return new String(base64);
	}

	private static int decodeBase64(final char src[], int src_offset) {
		int val = 0;
		for (int i = 0; i < 4; ++i)
			val |= (-1
				    + ((((('A' - 1) - src[i + src_offset]) & (src[i + src_offset] - ('Z' + 1))) >>> 8) & (src[i + src_offset] - 64))
				    + ((((('a' - 1) - src[i + src_offset]) & (src[i + src_offset] - ('z' + 1))) >>> 8) & (src[i + src_offset] - 70))
				    + ((((('0' - 1) - src[i + src_offset]) & (src[i + src_offset] - ('9' + 1))) >>> 8) & (src[i + src_offset] + 5))
				    + ((((('+' - 1) - src[i + src_offset]) & (src[i + src_offset] - ('+' + 1))) >>> 8) & 63)
				    + ((((('/' - 1) - src[i + src_offset]) & (src[i + src_offset] - ('/' + 1))) >>> 8) & 64)
				) << (18 - 6 * i);
		return val;
	}

	public static byte[] keyFromBase64(final String input) {
		final char base64[] = input.toCharArray();
		if (base64.length != WG_KEY_LEN_BASE64 || base64[WG_KEY_LEN_BASE64 - 1] != '=')
			throw new IllegalArgumentException("WireGuard base64 keys must be 44 characters encoding 32 bytes");
		final byte key[] = new byte[WG_KEY_LEN];
		int i;
		int val;

		for (i = 0; i < WG_KEY_LEN / 3; ++i) {
			val = decodeBase64(base64, i * 4);
			if (val < 0)
				throw new IllegalArgumentException("WireGuard base64 keys must be 44 characters encoding 32 bytes");
			key[i * 3 + 0] = (byte)((val >>> 16) & 0xff);
			key[i * 3 + 1] = (byte)((val >>> 8) & 0xff);
			key[i * 3 + 2] = (byte)(val & 0xff);
		}
		final char endSegment[] = { base64[i * 4 + 0], base64[i * 4 + 1], base64[i * 4 + 2], 'A' };
		val = decodeBase64(endSegment, 0);
		if (val < 0 || (val & 0xff) != 0)
			throw new IllegalArgumentException("WireGuard base64 keys must be 44 characters encoding 32 bytes");
		key[i * 3 + 0] = (byte)((val >>> 16) & 0xff);
		key[i * 3 + 1] = (byte)((val >>> 8) & 0xff);
		return key;
	}
}
