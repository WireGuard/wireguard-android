import com.ionspin.kotlin.crypto.LibsodiumInitializer
import com.ionspin.kotlin.crypto.signature.Signature
import com.jimberisolation.android.util.CreateDaemonData
import com.jimberisolation.android.util.UserAuthenticationResult
import com.jimberisolation.android.util.createDaemon
import com.jimberisolation.android.util.generateEd25519KeyPair
import com.jimberisolation.android.util.generateWireguardKeys
import com.jimberisolation.android.util.getCloudControllerPublicKey
import com.jimberisolation.android.util.getExistingDaemons
import com.jimberisolation.android.util.getUserAuthentication
import org.bouncycastle.util.encoders.Base64


enum class AuthenticationType {
    Google,
    Microsoft
}

fun createNetworkIsolationDaemonConfig(authToken: String, authType: AuthenticationType): Result<String?> {
    val keys = generateEd25519KeyPair()

    val authResult = getUserAuthentication(authToken, authType)
    if (authResult.isFailure) {
        val exception = authResult.exceptionOrNull()
        return if (exception != null) {
            Result.failure(exception)
        } else {
            Result.failure(RuntimeException("Unknown authentication error"))
        }
    }

    val cookies = authResult.getOrThrow().cookies;
    val userId = authResult.getOrThrow().userId.toString();
    val email =  authResult.getOrThrow().email;

    val cloudControllerResult = getCloudControllerPublicKey(cookies)
    if (cloudControllerResult.isFailure) {
        val exception = cloudControllerResult.exceptionOrNull()
        return if (exception != null) {
            Result.failure(exception)
        } else {
            Result.failure(RuntimeException("Unknown cloud controller error"))
        }
    }

    val routerPublicKey = cloudControllerResult.getOrThrow().getString("routerPublicKey")
    val endpointAddress = cloudControllerResult.getOrThrow().getString("endpointAddress")
    val ipAddress = cloudControllerResult.getOrThrow().getString("ipAddress")

    val existingDaemonsResult = getExistingDaemons(cookies, userId);
    if (existingDaemonsResult.isFailure) {
        val exception = existingDaemonsResult.exceptionOrNull()
        return if (exception != null) {
            Result.failure(exception)
        } else {
            Result.failure(RuntimeException("Unknown existing daemons error"))
        }
    }

    val existingDaemons = existingDaemonsResult.getOrThrow();
    val uniqueDaemonName = getUniqueDeviceName(email, existingDaemons)

    val createDaemonData = CreateDaemonData(
        cookies = cookies,
        userId = userId,
        pk = keys.pk,
        deviceName =  uniqueDaemonName
    )

    val wireguardKeys = generateWireguardKeys(keys.sk, routerPublicKey)

    val createdDaemonResult = createDaemon(createDaemonData)
    if (createdDaemonResult.isFailure) {
        val exception = createdDaemonResult.exceptionOrNull()
        return if (exception != null) {
            Result.failure(exception)
        } else {
            Result.failure(RuntimeException("Unknown created daemon error"))
        }
    }

    val createdDaemon = createdDaemonResult.getOrThrow();

    val createdIpAddress = createdDaemon.getString("ipAddress")

    val company = Company("Jimber")
    val daemon = Daemon(wireguardKeys.baseEncodedPrivateKeyInX25519, createdIpAddress)
    val dnsServer = DnsServer(ipAddress)
    val networkController = NetworkController(wireguardKeys.baseEncodedCloudcontrollerPkInX25519,endpointAddress, 51820)

    val wireguardConfig = GenerateWireguardConfig(company, daemon, dnsServer, networkController)

    return Result.success(wireguardConfig);
}

fun createNetworkIsolationDaemonConfigFromEmailVerification(authenticationResult: UserAuthenticationResult): Result<String?> {
    val keys = generateEd25519KeyPair()

    val cookies = authenticationResult.cookies;
    val userId = authenticationResult.userId.toString();
    val email =  authenticationResult.email;

    val cloudControllerResult = getCloudControllerPublicKey(cookies)
    if (cloudControllerResult.isFailure) {
        val exception = cloudControllerResult.exceptionOrNull()
        return if (exception != null) {
            Result.failure(exception)
        } else {
            Result.failure(RuntimeException("Unknown cloud controller error"))
        }
    }

    val routerPublicKey = cloudControllerResult.getOrThrow().getString("routerPublicKey")
    val endpointAddress = cloudControllerResult.getOrThrow().getString("endpointAddress")
    val ipAddress = cloudControllerResult.getOrThrow().getString("ipAddress")

    val existingDaemonsResult = getExistingDaemons(cookies, userId);
    if (existingDaemonsResult.isFailure) {
        val exception = existingDaemonsResult.exceptionOrNull()
        return if (exception != null) {
            Result.failure(exception)
        } else {
            Result.failure(RuntimeException("Unknown existing daemons error"))
        }
    }

    val existingDaemons = existingDaemonsResult.getOrThrow();
    val uniqueDaemonName = getUniqueDeviceName(email, existingDaemons)

    val createDaemonData = CreateDaemonData(
        cookies = cookies,
        userId = userId,
        pk = keys.pk,
        deviceName =  uniqueDaemonName
    )

    val wireguardKeys = generateWireguardKeys(keys.sk, routerPublicKey)

    val createdDaemonResult = createDaemon(createDaemonData)
    if (createdDaemonResult.isFailure) {
        val exception = createdDaemonResult.exceptionOrNull()
        return if (exception != null) {
            Result.failure(exception)
        } else {
            Result.failure(RuntimeException("Unknown created daemon error"))
        }
    }

    val createdDaemon = createdDaemonResult.getOrThrow();

    val createdIpAddress = createdDaemon.getString("ipAddress")

    val company = Company("Jimber")
    val daemon = Daemon(wireguardKeys.baseEncodedPrivateKeyInX25519, createdIpAddress)
    val dnsServer = DnsServer(ipAddress)
    val networkController = NetworkController(wireguardKeys.baseEncodedCloudcontrollerPkInX25519,endpointAddress, 51820)

    val wireguardConfig = GenerateWireguardConfig(company, daemon, dnsServer, networkController)

    return Result.success(wireguardConfig);
}
