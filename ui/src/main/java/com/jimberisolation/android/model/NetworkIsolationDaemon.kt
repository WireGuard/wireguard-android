import com.jimberisolation.android.storage.SharedStorage
import com.jimberisolation.android.util.CreateDaemonData
import com.jimberisolation.android.util.CreateDaemonResult
import com.jimberisolation.android.util.UserAuthenticationResult
import com.jimberisolation.android.util.generateEd25519KeyPair
import com.jimberisolation.android.util.generateWireguardKeys

enum class AuthenticationType {
    Google,
    Microsoft
}

suspend fun createNetworkIsolationDaemonConfig(authToken: String, authType: AuthenticationType): Result<CreateDaemonResult?> {
    val keys = generateEd25519KeyPair()

    val userAuthenticationResult = getUserAuthenticationV2(authToken, authType)
    if (userAuthenticationResult.isFailure) {
        return Result.failure(userAuthenticationResult.exceptionOrNull() ?: RuntimeException("Unknown user authentication error"))
    }

    val userId = userAuthenticationResult.getOrThrow().id.toString();
    val email =  userAuthenticationResult.getOrThrow().email;
    val companyName =  userAuthenticationResult.getOrThrow().company.name;

    SharedStorage.getInstance().saveCurrentUsingEmail(email);

    val cloudControllerResult = getCloudControllerPublicKeyV2(companyName);
    if (cloudControllerResult.isFailure) {
        return Result.failure(cloudControllerResult.exceptionOrNull() ?: RuntimeException("Unknown cloud controller error"))
    }

    val routerPublicKey = cloudControllerResult.getOrThrow().routerPublicKey
    val endpointAddress = cloudControllerResult.getOrThrow().endpointAddress
    val ipAddress = cloudControllerResult.getOrThrow().ipAddress

    val existingDaemonsResult = getExistingDaemonsV2(userId, companyName);
    if (existingDaemonsResult.isFailure) {
        return Result.failure(existingDaemonsResult.exceptionOrNull() ?: RuntimeException("Unknown existing daemons error"))
    }

    val existingDaemons = existingDaemonsResult.getOrThrow();
    val uniqueDaemonName = getUniqueDeviceName(email, existingDaemons)

    val createDaemonData = CreateDaemonData(
        publicKey = keys.pk,
        name =  uniqueDaemonName
    )

    val wireguardKeys = generateWireguardKeys(keys.sk, routerPublicKey)

    val createdDaemonResult = createDaemonV2(userId,companyName, createDaemonData);
    if (createdDaemonResult.isFailure) {
        return Result.failure(createdDaemonResult.exceptionOrNull() ?: RuntimeException("Unknown created daemon error"))
    }

    val createdDaemon = createdDaemonResult.getOrThrow();

    val createdIpAddress = createdDaemon.ipAddress

    val company = Company(companyName)
    val daemon = Daemon(wireguardKeys.baseEncodedPrivateKeyInX25519, createdIpAddress)
    val dnsServer = DnsServer(ipAddress)
    val networkController = NetworkController(wireguardKeys.baseEncodedCloudcontrollerPkInX25519,endpointAddress, 51820)

    val wireguardConfig = GenerateWireguardConfig(company, daemon, dnsServer, networkController)

    return Result.success(CreateDaemonResult(wireguardConfig, companyName));
}

suspend fun createNetworkIsolationDaemonConfigFromEmailVerification(authenticationResult: UserAuthenticationResult): Result<String?> {
    val keys = generateEd25519KeyPair()

    val userId = authenticationResult.id.toString()
    val email = authenticationResult.email
    val companyName = authenticationResult.company.name

    val cloudControllerResult = getCloudControllerPublicKeyV2(companyName);
    if (cloudControllerResult.isFailure) {
        return Result.failure(cloudControllerResult.exceptionOrNull() ?: RuntimeException("Unknown cloud controller error"))
    }

    val routerPublicKey = cloudControllerResult.getOrThrow().routerPublicKey
    val endpointAddress = cloudControllerResult.getOrThrow().endpointAddress
    val ipAddress = cloudControllerResult.getOrThrow().ipAddress

    val existingDaemonsResult = getExistingDaemonsV2(userId, companyName);
    if (existingDaemonsResult.isFailure) {
        return Result.failure(existingDaemonsResult.exceptionOrNull() ?: RuntimeException("Unknown existing daemons error"))
    }

    val existingDaemons = existingDaemonsResult.getOrThrow();
    val uniqueDaemonName = getUniqueDeviceName(email, existingDaemons)

    val createDaemonData = CreateDaemonData(
        publicKey = keys.pk,
        name =  uniqueDaemonName
    )

    val wireguardKeys = generateWireguardKeys(keys.sk, routerPublicKey)

    val createdDaemonResult = createDaemonV2(userId, companyName, createDaemonData);
    if (createdDaemonResult.isFailure) {
        return Result.failure(createdDaemonResult.exceptionOrNull() ?: RuntimeException("Unknown created daemon error"))
    }

    val createdDaemon = createdDaemonResult.getOrThrow();

    val createdIpAddress = createdDaemon.ipAddress

    val company = Company(companyName)
    val daemon = Daemon(wireguardKeys.baseEncodedPrivateKeyInX25519, createdIpAddress)
    val dnsServer = DnsServer(ipAddress)
    val networkController = NetworkController(wireguardKeys.baseEncodedCloudcontrollerPkInX25519, endpointAddress, 51820)

    val wireguardConfig = GenerateWireguardConfig(company, daemon, dnsServer, networkController)

    return Result.success(wireguardConfig)
}
