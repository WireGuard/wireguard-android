import com.jimberisolation.android.util.GetDaemonsNameResult
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

fun sanitizeHostname(combinedName: String): String {
    // Remove invalid characters
    var sanitized = combinedName.replace(Regex("[^a-zA-Z0-9-]"), "")

    // Ensure at most one hyphen
    if (sanitized.count { it == '-' } > 1) {
        sanitized = sanitized.replace("-", "")
    }

    // Ensure it doesn't start with a hyphen
    if (sanitized.startsWith('-')) {
        sanitized = sanitized.trimStart('-')
    }

    // Ensure length is within limits
    if (sanitized.length < 2) {
        sanitized = sanitized.padEnd(2, 'a')
    } else if (sanitized.length > 60) {
        sanitized = sanitized.take(60)
    }

    // Ensure it doesn't start with a number
    if (sanitized.first().isDigit()) {
        sanitized = sanitized.drop(1)
    }

    return sanitized
}

fun getExistingDaemon(name: String, existingNames: List<GetDaemonsNameResult>): GetDaemonsNameResult? {
    val names: List<String> = existingNames.map { it.name }

    if(names.contains(name)) {
        return existingNames.find { name == it.name };
    }

    return null;
}

fun getMobileNetworkIsolationHostname(email: String): String {
    val username = email.split("@")[0].replace(".", "")

    val hostname = getDeviceHostname()
    val baseName = "$hostname-$username"

    return baseName;
}

fun getUniqueDeviceName(email: String, existingNames: List<GetDaemonsNameResult>): String {
    val username = email.split("@")[0].replace(".", "")

    val hostname = getDeviceHostname()
    val baseName = "$hostname-$username"

    var sanitizedName = sanitizeHostname(baseName)

    var counter = 1

    val names: List<String> = existingNames.map { it.name }

    // Check if the name already exists in the database
    while (names.contains(sanitizedName)) {
        sanitizedName = sanitizeHostname("$baseName$counter")
        counter++
    }

    return sanitizedName
}