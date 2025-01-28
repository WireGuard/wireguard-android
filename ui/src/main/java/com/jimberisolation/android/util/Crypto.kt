package com.jimberisolation.android.util

import android.util.Log
import com.ionspin.kotlin.crypto.box.Box
import com.ionspin.kotlin.crypto.signature.Signature
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.util.encoders.Base64
import java.nio.charset.Charset

import java.security.SecureRandom

data class EdKeyPair(
    val pk: String,
    val sk: String,
)
data class WireguardKeys(
    val base64EncodedPkCurveX25519: String,
    val base64EncodedSkCurveX25519: String,
    val base64EncodedNetworkControllerPkCurveX25519: String,
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

fun generateWireguardConfigurationKeys(pk: String, sk: String, cloudControllerPk: String): WireguardKeys {
    val base64EncodedCurveNetworkControllerPk = parseEdPublicKeyToCurveX25519(cloudControllerPk)

    val base64EncodedCurveSk = parseEdPrivateKeyToCurveX25519(sk)
    val base64EncodedCurvePk = parseEdPublicKeyToCurveX25519(pk)

    return WireguardKeys(
        base64EncodedPkCurveX25519 = base64EncodedCurvePk,
        base64EncodedSkCurveX25519 = base64EncodedCurveSk,
        base64EncodedNetworkControllerPkCurveX25519 = base64EncodedCurveNetworkControllerPk
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
fun decryptData(encryptedData: String, secretKeyBase64: String): String? {
    try {
        val privateKeyBytes = Base64.decode(secretKeyBase64);
        val privateKeyX25519 =  X25519PrivateKeyParameters(privateKeyBytes, 0)

        val privateKey = privateKeyX25519.encoded.toUByteArray();
        val publicKey = privateKeyX25519.generatePublicKey().encoded.toUByteArray()

        val decodedData = Base64.decode(encryptedData).toUByteArray();

        val decryptedMessage = Box.sealOpen(decodedData, publicKey, privateKey)
        return String(decryptedMessage.toByteArray(), Charset.forName("UTF-8"))
    }
    catch (e: Exception) {
        Log.i("DECRYPTION", "ERROR IN DECRYPTION", e)
        return null;
    }

}




