package com.jimberisolation.android.util

import com.ionspin.kotlin.crypto.signature.Signature
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.util.encoders.Base64

import java.security.SecureRandom

data class EdKeyPair(
    val pk: String,
    val sk: String,
)
data class WireguardKeys(
    val base64EncodedPkCurveX25519: String,
    val base64EncodedSkCurveX25519: String,
)


// Function to generate Ed25519 key pair
fun generateEd25519KeyPair(): EdKeyPair {
    // Initialize the key pair generator
    val keyPairGenerator = Ed25519KeyPairGenerator()
    keyPairGenerator.init(Ed25519KeyGenerationParameters(SecureRandom())) // Use SecureRandom for key generation

    // Generate the key pair
    val keyPair: AsymmetricCipherKeyPair = keyPairGenerator.generateKeyPair()
    val privateKey = keyPair.private as Ed25519PrivateKeyParameters
    val publicKey = keyPair.public as Ed25519PublicKeyParameters

    return EdKeyPair(Base64.toBase64String(publicKey.encoded), Base64.toBase64String(privateKey.encoded))
}

fun generateWireguardConfigurationKeys(pk: String, sk: String): WireguardKeys {
    val base64EncodedCurveSk = parseEdPrivateKeyToCurveX25519(sk)
    val base64EncodedCurvePk = parseEdPublicKeyToCurveX25519(pk)

    return WireguardKeys(
        base64EncodedPkCurveX25519 = base64EncodedCurvePk,
        base64EncodedSkCurveX25519 = base64EncodedCurveSk,
    )
}

@OptIn(ExperimentalUnsignedTypes::class)
fun parseEdPublicKeyToCurveX25519(pk: String): String {
    val curvePk = Signature.ed25519PkToCurve25519((Base64.decode(pk).toUByteArray()));
    val baseEncodedCurvePk = Base64.toBase64String(curvePk.toByteArray());

    return baseEncodedCurvePk;
}

@OptIn(ExperimentalUnsignedTypes::class)
fun parseEdPrivateKeyToCurveX25519(sk: String): String {
    val curveSk= Signature.ed25519SkToCurve25519((Base64.decode(sk).toUByteArray()));
    val baseEncodedCurveSk = Base64.toBase64String(curveSk.toByteArray());

    return baseEncodedCurveSk;
}

@OptIn(ExperimentalUnsignedTypes::class)
fun generateSign(message: ByteArray, sk: String): ByteArray {
    val privateKeyBytes = Base64.decode(sk)
    val privateKeyEd25519 =  Ed25519PrivateKeyParameters(privateKeyBytes, 0)

    val signer = Ed25519Signer()
    signer.init(true, privateKeyEd25519)

    signer.update(message, 0, message.size)

    return signer.generateSignature()
}

fun generateSignedMessage(message: ByteArray, privateKey: String): String {
    val signature = generateSign(message, privateKey)

    val payload = signature + message
    return Base64.toBase64String(payload)
}





