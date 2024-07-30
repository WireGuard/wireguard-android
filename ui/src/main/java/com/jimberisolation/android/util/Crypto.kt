package com.jimberisolation.android.util

/*
 * Copyright © 2017-2024 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.util.encoders.Base64
import java.security.SecureRandom

fun generateEd25519KeyPair(): Pair<String, String> {
    val keyPairGenerator = Ed25519KeyPairGenerator()

    keyPairGenerator.init(Ed25519KeyGenerationParameters(SecureRandom()))

    val keyPair = keyPairGenerator.generateKeyPair()

    val privateKey = (keyPair.private as Ed25519PrivateKeyParameters).encoded
    val publicKey = (keyPair.public as Ed25519PublicKeyParameters).encoded

    val privateKeyBase64 = Base64.toBase64String(privateKey)
    val publicKeyBase64 = Base64.toBase64String(publicKey)

    return Pair(publicKeyBase64, privateKeyBase64);
}