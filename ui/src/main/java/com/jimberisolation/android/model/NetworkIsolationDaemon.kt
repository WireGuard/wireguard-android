import android.util.Log
import com.jimberisolation.android.api.CreateDaemonData
import com.jimberisolation.android.api.CreateDaemonResult
import com.jimberisolation.android.api.UserAuthenticationResult
import com.jimberisolation.android.storage.DaemonKeyPair
import com.jimberisolation.android.storage.SharedStorage
import com.jimberisolation.android.util.generateEd25519KeyPair
import com.jimberisolation.android.util.generateWireguardKeys

enum class AuthenticationType {
    Google,
    Microsoft
}

suspend fun authenticateUser(authToken: String, authType: AuthenticationType): Result<UserAuthenticationResult> {
    val userAuthResult = getUserAuthenticationV2(authToken, authType)
    if (userAuthResult.isFailure) {
        return Result.failure(userAuthResult.exceptionOrNull() ?: RuntimeException("Failed to authenticate user"))
    }

    return userAuthResult;
}

suspend fun createNetworkIsolationDaemonConfig(userAuthenticationResult: UserAuthenticationResult, daemonName: String): Result<CreateDaemonResult?> {
    val ed25519Keys = generateEd25519KeyPair()

    val userId = userAuthenticationResult.id
    val companyName = userAuthenticationResult.company.name

    // Fetch public key from cloud controller
    val cloudControllerResult = getCloudControllerPublicKeyV2(companyName)
    if (cloudControllerResult.isFailure) {
        return Result.failure(cloudControllerResult.exceptionOrNull() ?: RuntimeException("Failed to get cloud controller public key"))
    }

    val cloudControllerData = cloudControllerResult.getOrThrow()
    val routerPublicKey = cloudControllerData.routerPublicKey
    val endpointAddress = cloudControllerData.endpointAddress
    val cloudIpAddress = cloudControllerData.ipAddress

    val wireguardKeyPair = generateWireguardKeys(ed25519Keys.sk, routerPublicKey)

    // Fetch existing daemons
    val existingDaemonsResult = getExistingDaemonsV2(userId, companyName)
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
        val createDaemonData = CreateDaemonData(
            publicKey = ed25519Keys.pk,
            name = daemonName
        )

        val createdDaemonResult = createDaemonV2(userId, companyName, createDaemonData)
        if (createdDaemonResult.isFailure) {
            return Result.failure(createdDaemonResult.exceptionOrNull() ?: RuntimeException("Failed to create daemon"))
        }

        daemonPrivateKey = wireguardKeyPair.baseEncodedPrivateKeyInX25519
        daemonIpAddress = createdDaemonResult.getOrThrow().ipAddress
        daemonId = createdDaemonResult.getOrThrow().id;


        val daemonKeyPair = DaemonKeyPair(daemonName, daemonId, companyName, userId, wireguardKeyPair.baseEncodedCloudcontrollerPkInX25519, wireguardKeyPair.baseEncodedPrivateKeyInX25519)
        SharedStorage.getInstance().saveWireguardKeyPair(daemonKeyPair)

    } else if(matchingDaemon != null) {
        daemonPrivateKey = localDaemonKeys.sk
        daemonIpAddress = matchingDaemon.ipAddress
        daemonId = matchingDaemon.id
    }

    else {
        Log.e("NO_MATCHING_DAEMON", "Keys were found but there is no matching daemon")
        return Result.failure(Exception("Something went wrong, please contact support (100)"))
    }

    // Build WireGuard configuration
    val company = Company(companyName)
    val daemon = Daemon(daemonPrivateKey, daemonIpAddress)
    val dnsServer = DnsServer(cloudIpAddress)
    val networkController = NetworkController(wireguardKeyPair.baseEncodedCloudcontrollerPkInX25519, endpointAddress, 51820)

    val wireguardConfig = GenerateWireguardConfig(company, daemon, dnsServer, networkController)

    return Result.success(CreateDaemonResult(wireguardConfig, companyName, daemonId))
}


suspend fun createNetworkIsolationDaemonConfigFromEmailVerification(authenticationResult: UserAuthenticationResult, daemonName: String): Result<CreateDaemonResult?> {
    val ed25519Keys = generateEd25519KeyPair()

    val userId = authenticationResult.id
    val companyName = authenticationResult.company.name

    val cloudControllerResult = getCloudControllerPublicKeyV2(companyName);
    if (cloudControllerResult.isFailure) {
        return Result.failure(cloudControllerResult.exceptionOrNull() ?: RuntimeException("Unknown cloud controller error"))
    }

    val routerPublicKey = cloudControllerResult.getOrThrow().routerPublicKey
    val endpointAddress = cloudControllerResult.getOrThrow().endpointAddress
    val cloudIpAddress = cloudControllerResult.getOrThrow().ipAddress

    val wireguardKeyPair = generateWireguardKeys(ed25519Keys.sk, routerPublicKey)

    val existingDaemonsResult = getExistingDaemonsV2(userId, companyName);
    if (existingDaemonsResult.isFailure) {
        return Result.failure(existingDaemonsResult.exceptionOrNull() ?: RuntimeException("Unknown existing daemons error"))
    }

    val localDaemonKeys = existingKeyOnDeviceOfUserId(userId)

    val daemonPrivateKey: String
    val daemonIpAddress: String
    val daemonId: Number;

    val existingDaemons = existingDaemonsResult.getOrThrow()
    val matchingDaemon = getExistingDaemon(daemonName, existingDaemons)

    if (localDaemonKeys == null) {
        val createDaemonData = CreateDaemonData(
            publicKey = ed25519Keys.pk,
            name = daemonName
        )

        val createdDaemonResult = createDaemonV2(userId, companyName, createDaemonData)
        if (createdDaemonResult.isFailure) {
            return Result.failure(createdDaemonResult.exceptionOrNull() ?: RuntimeException("Failed to create daemon"))
        }

        daemonPrivateKey = wireguardKeyPair.baseEncodedPrivateKeyInX25519
        daemonIpAddress = createdDaemonResult.getOrThrow().ipAddress
        daemonId = createdDaemonResult.getOrThrow().id;

        val daemonKeyPair = DaemonKeyPair(daemonName, daemonId, companyName, userId, wireguardKeyPair.baseEncodedCloudcontrollerPkInX25519, wireguardKeyPair.baseEncodedPrivateKeyInX25519)
        SharedStorage.getInstance().saveWireguardKeyPair(daemonKeyPair)

    } else if(matchingDaemon != null) {
        daemonPrivateKey = localDaemonKeys.sk
        daemonIpAddress = matchingDaemon.ipAddress
        daemonId = matchingDaemon.id
    }

    else {
        Log.e("NO_MATCHING_DAEMON", "Keys were found but there is no matching daemon")
        return Result.failure(Exception("Something went wrong, please contact support (100)"))
    }

    // Build WireGuard configuration
    val company = Company(companyName)
    val daemon = Daemon(daemonPrivateKey, daemonIpAddress)
    val dnsServer = DnsServer(cloudIpAddress)
    val networkController = NetworkController(wireguardKeyPair.baseEncodedCloudcontrollerPkInX25519, endpointAddress, 51820)

    val wireguardConfig = GenerateWireguardConfig(company, daemon, dnsServer, networkController)

    return Result.success(CreateDaemonResult(wireguardConfig, companyName, daemonId))
}
