import android.util.Log
import com.jimberisolation.android.authentication.AuthenticationType
import com.jimberisolation.android.authentication.Company
import com.jimberisolation.android.authentication.UserAuthentication
import com.jimberisolation.android.authentication.getUserAuthentication
import com.jimberisolation.android.daemon.CreateDaemonApiRequest
import com.jimberisolation.android.daemon.Daemon
import com.jimberisolation.android.daemon.NetworkIsolationDaemon
import com.jimberisolation.android.daemon.createDaemon
import com.jimberisolation.android.daemon.getExistingDaemons
import com.jimberisolation.android.networkcontroller.NetworkController
import com.jimberisolation.android.networkcontroller.getNetworkControllerPublicKey
import com.jimberisolation.android.storage.DaemonKeyPair
import com.jimberisolation.android.storage.SharedStorage
import com.jimberisolation.android.util.generateEd25519KeyPair
import com.jimberisolation.android.util.generateWireguardConfigurationKeys
import com.jimberisolation.android.util.parseEdPrivateKeyToCurveX25519
import com.jimberisolation.android.util.parseEdPublicKeyToCurveX25519


suspend fun authenticateUser(authToken: String, authType: AuthenticationType): Result<UserAuthentication> {
    val userAuthResult = getUserAuthentication(authToken, authType)
    if (userAuthResult.isFailure) {
        return Result.failure(userAuthResult.exceptionOrNull() ?: RuntimeException("Failed to authenticate user"))
    }

    return userAuthResult;
}

suspend fun createNetworkIsolationDaemonConfig(userAuthentication: UserAuthentication, daemonName: String): Result<NetworkIsolationDaemon?> {
    val ed25519Keys = generateEd25519KeyPair()

    val userId = userAuthentication.userId
    val companyName = userAuthentication.companyName

    val wireguardConfigKeys = generateWireguardConfigurationKeys(ed25519Keys.pk, ed25519Keys.sk)

    // Fetch existing daemons
    val existingDaemonsResult = getExistingDaemons(userId, companyName)
    if (existingDaemonsResult.isFailure) {
        return Result.failure(existingDaemonsResult.exceptionOrNull() ?: RuntimeException("Failed to fetch existing daemons"))
    }

    val localDaemonKeys = existingKeyOnDeviceOfUserId(userId)

    val daemonPrivateKey: String
    val daemonIpAddress: String
    val daemonId: Int;

    val existingDaemons = existingDaemonsResult.getOrThrow()
    val matchingDaemon = getExistingDaemon(daemonName, existingDaemons)


    if (localDaemonKeys == null) {
        val createDaemonData = CreateDaemonApiRequest(
            publicKey = ed25519Keys.pk,
            name = daemonName
        )

        val createdDaemonResult = createDaemon(userId, companyName, createDaemonData)
        if (createdDaemonResult.isFailure) {
            return Result.failure(createdDaemonResult.exceptionOrNull() ?: RuntimeException("Failed to create daemon"))
        }

        daemonPrivateKey = wireguardConfigKeys.base64EncodedSkCurveX25519
        daemonIpAddress = createdDaemonResult.getOrThrow().ipAddress
        daemonId = createdDaemonResult.getOrThrow().daemonId;

        val daemonKeyPair = DaemonKeyPair(daemonName, daemonId, userId, companyName, ed25519Keys.pk, ed25519Keys.sk)
        SharedStorage.getInstance().saveDaemonKeyPair(daemonKeyPair)

    } else if(matchingDaemon != null) {
        daemonPrivateKey = parseEdPrivateKeyToCurveX25519(localDaemonKeys.baseEncodedSkEd25519)
        daemonIpAddress = matchingDaemon.ipAddress
        daemonId = matchingDaemon.daemonId
    }
    else {
        Log.e("NO_MATCHING_DAEMON", "Keys were found but there is no matching daemon")
        return Result.failure(Exception("Something went wrong, please contact support (100)"))
    }

    val cloudControllerResult = getNetworkControllerPublicKey(daemonId, companyName)
    if (cloudControllerResult.isFailure) {
        return Result.failure(cloudControllerResult.exceptionOrNull() ?: RuntimeException("Failed to get cloud controller public key"))
    }

    val cloudControllerData = cloudControllerResult.getOrThrow()
    val routerPublicKeyX25519 = parseEdPublicKeyToCurveX25519(cloudControllerData.routerPublicKey)
    val endpointAddress = cloudControllerData.endpointAddress
    val cloudIpAddress = cloudControllerData.ipAddress
    val allowedIps = cloudControllerData.allowedIps

    // Build WireGuard configuration
    val company = Company(companyName)
    val daemon = Daemon(daemonId, daemonName, daemonIpAddress, daemonPrivateKey)
    val dnsServer = DnsServer(cloudIpAddress)
    val networkController = NetworkController(routerPublicKeyX25519, daemonIpAddress, endpointAddress, allowedIps)

    val wireguardConfig = generateWireguardConfig(company, daemon, dnsServer, networkController)

    return Result.success(NetworkIsolationDaemon(daemonId = daemonId, companyName = companyName, configurationString = wireguardConfig))
}


suspend fun createNetworkIsolationDaemonConfigFromEmailVerification(userAuthentication: UserAuthentication, daemonName: String): Result<NetworkIsolationDaemon?> {
    val ed25519Keys = generateEd25519KeyPair()

    val userId = userAuthentication.userId
    val companyName = userAuthentication.companyName

    val wireguardKeyPair = generateWireguardConfigurationKeys(ed25519Keys.pk, ed25519Keys.sk)

    val existingDaemonsResult = getExistingDaemons(userId, companyName);
    if (existingDaemonsResult.isFailure) {
        return Result.failure(existingDaemonsResult.exceptionOrNull() ?: RuntimeException("Unknown existing daemons error"))
    }

    val localDaemonKeys = existingKeyOnDeviceOfUserId(userId)

    val daemonPrivateKey: String
    val daemonIpAddress: String
    val daemonId: Int;

    val existingDaemons = existingDaemonsResult.getOrThrow()
    val matchingDaemon = getExistingDaemon(daemonName, existingDaemons)

    if (localDaemonKeys == null) {
        val createDaemonData = CreateDaemonApiRequest(
            publicKey = ed25519Keys.pk,
            name = daemonName
        )

        val createdDaemonResult = createDaemon(userId, companyName, createDaemonData)
        if (createdDaemonResult.isFailure) {
            return Result.failure(createdDaemonResult.exceptionOrNull() ?: RuntimeException("Failed to create daemon"))
        }

        daemonPrivateKey = wireguardKeyPair.base64EncodedSkCurveX25519
        daemonIpAddress = createdDaemonResult.getOrThrow().ipAddress
        daemonId = createdDaemonResult.getOrThrow().daemonId;

        val daemonKeyPair = DaemonKeyPair(daemonName, daemonId, userId, companyName, ed25519Keys.pk, ed25519Keys.sk)
        SharedStorage.getInstance().saveDaemonKeyPair(daemonKeyPair)

    } else if(matchingDaemon != null) {
        daemonPrivateKey = parseEdPrivateKeyToCurveX25519(localDaemonKeys.baseEncodedSkEd25519)
        daemonIpAddress = matchingDaemon.ipAddress
        daemonId = matchingDaemon.daemonId
    }

    else {
        Log.e("NO_MATCHING_DAEMON", "Keys were found but there is no matching daemon")
        return Result.failure(Exception("Something went wrong, please contact support (100)"))
    }

    val cloudControllerResult = getNetworkControllerPublicKey(daemonId, companyName)
    if (cloudControllerResult.isFailure) {
        return Result.failure(cloudControllerResult.exceptionOrNull() ?: RuntimeException("Failed to get cloud controller public key"))
    }

    val cloudControllerData = cloudControllerResult.getOrThrow()
    val routerPublicKeyX25519 = parseEdPublicKeyToCurveX25519(cloudControllerData.routerPublicKey)
    val endpointAddress = cloudControllerData.endpointAddress
    val cloudIpAddress = cloudControllerData.ipAddress
    val allowedIps = cloudControllerData.allowedIps

    // Build WireGuard configuration
    val company = Company(companyName)
    val daemon = Daemon(daemonId, daemonName, daemonIpAddress, daemonPrivateKey)
    val dnsServer = DnsServer(cloudIpAddress)
    val networkController = NetworkController(routerPublicKeyX25519, daemonIpAddress, endpointAddress, allowedIps)

    val wireguardConfig = generateWireguardConfig(company, daemon, dnsServer, networkController)

    return Result.success(NetworkIsolationDaemon(daemonId = daemonId, companyName =  companyName, configurationString = wireguardConfig))
}
