package com.jimberisolation.android.authentication

fun extractToken(cookieHeader: String, tokenName: String): String? {
    val cookies = cookieHeader.split("; ")
    for (cookie in cookies) {
        if (cookie.startsWith("$tokenName=")) {
            return cookie.substringAfter("$tokenName=")
        }
    }
    return null
}