import android.os.Build
import com.jimberisolation.android.storage.SharedStorage
import com.jimberisolation.android.storage.WireguardKeyPair
import com.jimberisolation.android.util.GetDaemonsNameResult
import java.net.InetAddress
import java.net.UnknownHostException


fun getDeviceHostname(): String {
    return try {
        val hostName = Build.MODEL.replace(Regex("[^A-Za-z0-9]"), "")
        hostName
    } catch (e: UnknownHostException) {
        "Unknown"
    }
}


fun existingKeysOnDeviceForCompany(company: String): WireguardKeyPair? {
   return SharedStorage.getInstance().getWireguardKeyPair(company);
}

fun getExistingDaemon(name: String, existingNames: List<GetDaemonsNameResult>): GetDaemonsNameResult? {
    val names: List<String> = existingNames.map { it.name }

    if(names.contains(name)) {
        return existingNames.find { name == it.name };
    }

    return null;
}
