import android.os.Build
import java.net.UnknownHostException


fun getDeviceHostname(): String {
    return try {
        val hostName = Build.MODEL.replace(Regex("[^A-Za-z0-9]"), "")
        hostName
    } catch (e: UnknownHostException) {
        "Unknown"
    }
}

fun isValidMobileHostname(hostname: String): Pair<Boolean, String?> {
    // Check if string contains only alphanumeric characters and hyphens
    val regex = Regex("^[a-zA-Z0-9-]+$")
    if (!regex.matches(hostname)) {
        return false to "Hostname may only contain letters, digits, and hyphens."
    }

    // Check that there is at most one hyphen
    if (hostname.count { it == '-' } > 1) {
        return false to "Hostname may contain at most one hyphen."
    }

    // Check that it doesn't start with a hyphen
    if (hostname.startsWith("-")) {
        return false to "Hostname may not start with a hyphen."
    }

    // Length must be between 2 and 63 characters
    if (hostname.length < 2 || hostname.length > 63) {
        return false to "Hostname length must be between 2 and 63 characters."
    }

    // Check if the first character is a digit
    if (hostname.first().isDigit()) {
        return false to "Hostname may not start with a digit."
    }

    return true to null
}


