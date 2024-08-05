import com.ionspin.kotlin.crypto.LibsodiumInitializer
import com.ionspin.kotlin.crypto.signature.Signature
import com.jimberisolation.android.util.createDaemon
import com.jimberisolation.android.util.generateEd25519KeyPair
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

@OptIn(ExperimentalUnsignedTypes::class)
fun createNetworkIsolationDaemonConfig(authToken: String): String {
    var isInitialized = false;

    val keys = generateEd25519KeyPair()
    LibsodiumInitializer.initializeWithCallback {
        isInitialized = true;
    }

    while(!isInitialized) { }

    val curveSK = Signature.ed25519SkToCurve25519((Base64.decode(keys.sk).toUByteArray()));
    val baseEncodedCurveSk = Base64.toBase64String(curveSK.toByteArray());

    val authResult = getUserAuthentication(authToken);
    val cloudController = getCloudControllerPublicKey(authResult.cookies)

    val curveNetworkControllerPk = Signature.ed25519PkToCurve25519((Base64.decode(cloudController.getString("routerPublicKey")).toUByteArray()));
    val baseEncodedCurveNetworkControllerPk = Base64.toBase64String(curveNetworkControllerPk.toByteArray());

    val existingDaemons = getExistingDaemons(authResult.cookies, authResult.userId.toString());
    val uniqueDaemonName = getUniqueDeviceName(authResult.email, existingDaemons)

    val createDaemonData = CreateDaemonData(
        cookies = authResult.cookies,
        userId = authResult.userId.toString(),
        pk = keys.pk,
        deviceName =  uniqueDaemonName
    )

    val createdDaemon = createDaemon(createDaemonData)
    val createdIpAddress = createdDaemon.getString("ipAddress")

    val company = Company("Jimber")
    val daemon = Daemon(baseEncodedCurveSk, createdIpAddress)
    val dnsServer = DnsServer(cloudController.getString("ipAddress"))
    val networkController = NetworkController(baseEncodedCurveNetworkControllerPk, cloudController.getString("endpointAddress"), 51820)

    val result = GenerateWireguardConfig(company, daemon, dnsServer, networkController)
    return result;
}
