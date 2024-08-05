import com.ionspin.kotlin.crypto.LibsodiumInitializer
import com.ionspin.kotlin.crypto.signature.Signature
import com.jimberisolation.android.util.createDaemon
import com.jimberisolation.android.util.generateEd25519KeyPair
import com.jimberisolation.android.util.generateWireguardKeys
import com.jimberisolation.android.util.getCloudControllerPublicKey
import com.jimberisolation.android.util.getExistingDaemons
import com.jimberisolation.android.util.getUserAuthentication
import org.bouncycastle.util.encoders.Base64

data class CreateDaemonData(
    val cookies: List<String?>,
    val userId: String,
    val pk: String,
    val deviceName: String
)

enum class AuthenticationType {
    Google,
    Microsoft
}



fun createNetworkIsolationDaemonConfig(authToken: String, authType: AuthenticationType): String {
    val keys = generateEd25519KeyPair()

    val authResult = getUserAuthentication(authToken, authType);
    val cloudController = getCloudControllerPublicKey(authResult.cookies)

    val existingDaemons = getExistingDaemons(authResult.cookies, authResult.userId.toString());
    val uniqueDaemonName = getUniqueDeviceName(authResult.email, existingDaemons)

    val createDaemonData = CreateDaemonData(
        cookies = authResult.cookies,
        userId = authResult.userId.toString(),
        pk = keys.pk,
        deviceName =  uniqueDaemonName
    )

    val wireguardKeys = generateWireguardKeys(keys.sk, cloudController.getString("routerPublicKey"))

    val createdDaemon = createDaemon(createDaemonData)
    val createdIpAddress = createdDaemon.getString("ipAddress")

    val company = Company("Jimber")
    val daemon = Daemon(wireguardKeys.baseEncodedPrivateKeyInX25519, createdIpAddress)
    val dnsServer = DnsServer(cloudController.getString("ipAddress"))
    val networkController = NetworkController(wireguardKeys.baseEncodedCloudcontrollerPkInX25519, cloudController.getString("endpointAddress"), 51820)

    val result = GenerateWireguardConfig(company, daemon, dnsServer, networkController)
    return result;
}
