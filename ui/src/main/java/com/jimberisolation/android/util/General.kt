import java.net.InetAddress
import java.net.UnknownHostException


fun getDeviceHostname(): String {
    return try {
        val hostName = InetAddress.getLocalHost().hostName
        hostName
    } catch (e: UnknownHostException) {
        "Unknown"
    }
}

fun getUniqueDeviceName(email: String, existingNames: List<String?>): String {
    val username = email.split("@")[0].replace(".", "")

    val hostname = getDeviceHostname()
    var combinedName = "$hostname-$username"
    var counter = 1

    // Check if the name already exists in the database
    while (existingNames.contains(combinedName)) {
        combinedName = "$hostname-$username$counter"
        counter++
    }

    return combinedName;
}