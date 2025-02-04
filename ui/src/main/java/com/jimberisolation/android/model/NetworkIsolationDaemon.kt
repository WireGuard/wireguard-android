import com.jimberisolation.android.authentication.AuthenticationType
import com.jimberisolation.android.authentication.Company
import com.jimberisolation.android.authentication.UserAuthentication
import com.jimberisolation.android.authentication.getUserAuthentication
import com.jimberisolation.android.daemon.CreateDaemonApiRequest
import com.jimberisolation.android.daemon.Daemon
import com.jimberisolation.android.daemon.NetworkIsolationDaemon
import com.jimberisolation.android.daemon.createDaemon
import com.jimberisolation.android.networkcontroller.NetworkController
import com.jimberisolation.android.networkcontroller.getDaemonConnectionData
import com.jimberisolation.android.storage.DaemonKeyPair
import com.jimberisolation.android.storage.SharedStorage
import com.jimberisolation.android.util.generateEd25519KeyPair
import com.jimberisolation.android.util.generateWireguardConfigurationKeys
import com.jimberisolation.android.util.parseEdPublicKeyToCurveX25519


suspend fun authenticateUser(authToken: String, authType: AuthenticationType): Result<UserAuthentication> {
    val userAuthResult = getUserAuthentication(authToken, authType)
    if (userAuthResult.isFailure) {
        return Result.failure(userAuthResult.exceptionOrNull() ?: RuntimeException("Failed to authenticate user"))
    }

    return userAuthResult;
}

suspend fun register(userAuthentication: UserAuthentication, daemonName: String) : Result<NetworkIsolationDaemon> {
    val ed25519Keys = generateEd25519KeyPair()
    val wireguardConfigKeys = generateWireguardConfigurationKeys(ed25519Keys.pk, ed25519Keys.sk)

    val userId = userAuthentication.userId
    val companyName = userAuthentication.companyName

    val createDaemonData = CreateDaemonApiRequest(
        publicKey = ed25519Keys.pk,
        name = daemonName
    )

    val createdDaemonResult = createDaemon(userId, companyName, createDaemonData)
    if (createdDaemonResult.isFailure) {
        return Result.failure(createdDaemonResult.exceptionOrNull() ?: RuntimeException("Failed to create daemon"))
    }

    val daemonIpAddress = createdDaemonResult.getOrThrow().ipAddress
    val daemonId = createdDaemonResult.getOrThrow().daemonId;

    val daemonPrivateKeyX25519 = wireguardConfigKeys.base64EncodedSkCurveX25519
    val daemonPrivateKeyEd25519 = ed25519Keys.sk;

    // This function needs to be executed after the create, since the data will be verified with the daemon its pk
    val cloudControllerResult = getDaemonConnectionData(daemonId, companyName, daemonPrivateKeyEd25519)
    if (cloudControllerResult.isFailure) {
        return Result.failure(cloudControllerResult.exceptionOrNull() ?: RuntimeException("Failed to fetch network controller data"))
    }

    val cloudControllerData = cloudControllerResult.getOrThrow()
    val routerPublicKeyX25519 = parseEdPublicKeyToCurveX25519(cloudControllerData.routerPublicKey)
    val endpointAddress = cloudControllerData.endpointAddress
    val cloudIpAddress = cloudControllerData.ipAddress
    val allowedIps = cloudControllerData.allowedIps

    val daemonKeyPair = DaemonKeyPair(daemonName, daemonId, userId, companyName, ed25519Keys.pk, ed25519Keys.sk)
    SharedStorage.getInstance().saveDaemonKeyPair(daemonKeyPair)

    // Build WireGuard configuration
    val company = Company(companyName)
    val daemon = Daemon(daemonId, daemonName, daemonIpAddress, daemonPrivateKeyX25519)
    val dnsServer = DnsServer(cloudIpAddress)
    val networkController = NetworkController(routerPublicKeyX25519, daemonIpAddress, endpointAddress, allowedIps)

    val wireguardConfig = generateWireguardConfig(company, daemon, dnsServer, networkController)

    return Result.success(NetworkIsolationDaemon(daemonId = daemonId, companyName = companyName, configurationString = wireguardConfig))
}
