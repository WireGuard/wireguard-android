package com.jimberisolation.android.util

import generateRandomString
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets


fun getUserAuthentication(idToken: String): Pair<List<String?>, Int> {
    val url = URL("https://signal.staging.jimber.io/api/v1/auth/verify-google-id")

    // Create JSON object for the request body
    val jsonBody = JSONObject().apply {
        put("idToken", idToken)
    }

    // Convert the JSON object to a byte array
    val postData = jsonBody.toString().toByteArray(StandardCharsets.UTF_8)

    var userId = 0;
    with(url.openConnection() as HttpURLConnection) {
        // Set HTTP method to POST
        requestMethod = "POST"
        // Set headers
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Content-Length", postData.size.toString())
        doOutput = true

        // Write the POST data to the output stream
        outputStream.use { outputStream ->
            outputStream.write(postData)
        }

        // Check the response code
        val responseCode = responseCode
        println("Response Code: $responseCode")

        // Read the response
        inputStream.bufferedReader().use { reader ->
            val response = reader.readText()
            println("Response Body: $response")

            val parsedResponse = JSONObject(response)
            userId =  parsedResponse.getInt("id")
        }

        return Pair(headerFields["Set-Cookie"] ?: emptyList(), userId)
    }
}


fun getCNCPublicKey(cookies: List<String?>): JSONObject {
    val url = URL("https://signal.staging.jimber.io/api/v1/companies/Jimber/routers/cloud-network-controller")

    with(url.openConnection() as HttpURLConnection) {
        // Set HTTP method to GET
        requestMethod = "GET"
        // Set headers
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("Accept", "application/json")

        // For authorization
        if (cookies.isNotEmpty()) {
            setRequestProperty("Cookie", cookies.joinToString("; "))
        }

        // Optional: Remove doOutput for GET request
        // doOutput = false // This is the default, and can be omitted

        // Check the response code
        val responseCode = responseCode
        println("Response Code: $responseCode")

        // Read the response or error stream depending on the response code
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

fun createDaemon(cookies: List<String?>, userId: String, ncPublicKey: String): JSONObject {
    val url = URL("https://signal.staging.jimber.io/api/v1/companies/Jimber/daemons/user/$userId")

    val randomUuid = generateRandomString(10)

    // Create JSON object for the request body
    val jsonBody = JSONObject().apply {
        put("name", "lennertvanuitmobiel-$randomUuid")
        put("publicKey", ncPublicKey)
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
        if (cookies.isNotEmpty()) {
            setRequestProperty("Cookie", cookies.joinToString("; "))
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