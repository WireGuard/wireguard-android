package com.jimberisolation.android.util

import CreateDaemonData
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class UserAuthenticationResult(
    val cookies: List<String?>,
    val userId: Int,
    val email: String
)


fun getUserAuthentication(idToken: String): UserAuthenticationResult {
    val url = URL("https://signal.staging.jimber.io/api/v1/auth/verify-google-id")

    val jsonBody = JSONObject().apply {
        put("idToken", idToken)
    }

    val postData = jsonBody.toString().toByteArray(StandardCharsets.UTF_8)

    var userId = 0
    var email = "empty@jimber.org"

    with(url.openConnection() as HttpURLConnection) {
        requestMethod = "POST"

        // Set headers
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Content-Length", postData.size.toString())
        doOutput = true

        outputStream.use { outputStream ->
            outputStream.write(postData)
        }

        val responseCode = responseCode
        println("Response Code: $responseCode")

        // Read the response
        inputStream.bufferedReader().use { reader ->
            val response = reader.readText()
            println("Response Body: $response")

            val parsedResponse = JSONObject(response)
            userId = parsedResponse.getInt("id")
            email = parsedResponse.getString("email")
        }

        return UserAuthenticationResult(
            cookies = headerFields["Set-Cookie"] ?: emptyList(),
            userId = userId,
            email = email
        )
    }
}

fun getExistingDaemons(cookies: List<String?>, userId: String): List<String?> {
    val url = URL("https://signal.staging.jimber.io/api/v1/companies/Jimber/daemons/user/$userId")

    with(url.openConnection() as HttpURLConnection) {
        requestMethod = "GET"

        // Set headers
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("Accept", "application/json")

        if (cookies.isNotEmpty()) {
            setRequestProperty("Cookie", cookies.joinToString("; "))
        }

        val responseCode = responseCode
        println("Response Code: $responseCode")

        val inputStream = if (responseCode in 200..299) {
            inputStream
        } else {
            errorStream
        }

        inputStream.bufferedReader().use { reader ->
            val response = reader.readText()
            println("Response Body: $response")

            val jsonArray = JSONArray(response)
            val namesList = mutableListOf<String>()

            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val name = jsonObject.getString("name")
                namesList.add(name)
            }

            return namesList
        }
    }
}


fun getCloudControllerPublicKey(cookies: List<String?>): JSONObject {
    val url = URL("https://signal.staging.jimber.io/api/v1/companies/Jimber/routers/cloud-network-controller")

    with(url.openConnection() as HttpURLConnection) {
        requestMethod = "GET"

        // Set headers
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("Accept", "application/json")

        if (cookies.isNotEmpty()) {
            setRequestProperty("Cookie", cookies.joinToString("; "))
        }

        val responseCode = responseCode
        println("Response Code: $responseCode")

        val inputStream = if (responseCode in 200..299) {
            inputStream
        } else {
            errorStream
        }

        inputStream.bufferedReader().use { reader ->
            val response = reader.readText()
            println("Response Body: $response")

            return JSONObject(response);
        }
    }
}

fun createDaemon(createDaemonData: CreateDaemonData): JSONObject {
    val url = URL("https://signal.staging.jimber.io/api/v1/companies/Jimber/daemons/user/${createDaemonData.userId}")

    // Create JSON object for the request body
    val jsonBody = JSONObject().apply {
        put("name", createDaemonData.deviceName)
        put("publicKey", createDaemonData.pk)
    }

    // Convert the JSON object to a byte array
    val postData = jsonBody.toString().toByteArray(StandardCharsets.UTF_8)

    with(url.openConnection() as HttpURLConnection) {
        // Set HTTP method to POST
        requestMethod = "POST"
        // Set headers
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Content-Length", postData.size.toString())
        doOutput = true

        // For authorization
        if (createDaemonData.cookies.isNotEmpty()) {
            setRequestProperty("Cookie", createDaemonData.cookies.joinToString("; "))
        }

        // Write the POST data to the output stream
        outputStream.use { outputStream ->
            outputStream.write(postData)
        }

        // Check the response code
        val responseCode = responseCode
        println("Response Code: $responseCode")

        val inputStream = if (responseCode in 200..299) {
            inputStream
        } else {
            errorStream
        }

        inputStream.bufferedReader().use { reader ->
            val response = reader.readText()
            println("Response Body: $response")

            return JSONObject(response);
        }
    }

}