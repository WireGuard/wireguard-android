import android.os.Build
import com.jimberisolation.android.daemon.Daemon
import com.jimberisolation.android.storage.DaemonKeyPair
import com.jimberisolation.android.storage.SharedStorage
import java.net.UnknownHostException


fun getDeviceHostname(): String {
    return try {
        val hostName = Build.MODEL.replace(Regex("[^A-Za-z0-9]"), "")
        hostName
    } catch (e: UnknownHostException) {
        "Unknown"
    }
}


fun existingKeyOnDeviceOfUserId(userId: Int): DaemonKeyPair? {
   return SharedStorage.getInstance().getDaemonKeyPairByUserId(userId);
}

fun getExistingDaemon(name: String, existingNames: List<Daemon>): Daemon? {
    val names: List<String> = existingNames.map { it.name }

    if(names.contains(name)) {
        return existingNames.find { name == it.name };
    }

    return null;
}
