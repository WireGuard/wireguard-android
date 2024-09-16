package com.jimberisolation.android.util

import AuthenticationType
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class UserAuthenticationResult(
    val cookies: String,
    val userId: Int,
    val email: String
)

data class CreateDaemonData(
    val cookies: String,
    val userId: String,
    val pk: String,
    val deviceName: String
)

data class EmailVerificationData(
    val email: String,
    val token: Number
)


fun getUserAuthentication(idToken: String, authenticationType: AuthenticationType): Result<UserAuthenticationResult> {
    try {
        val type = when (authenticationType) {
            AuthenticationType.Google -> "google"
            AuthenticationType.Microsoft -> "microsoft"
        }

        val url = URL("https://signal.staging.jimber.io/api/v1/auth/verify-${type}-id")

        val jsonBody = JSONObject().apply {
            put("idToken", idToken)
        }

        val postData = jsonBody.toString().toByteArray(StandardCharsets.UTF_8)

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

            // Determine which stream to read: inputStream for success, errorStream for errors
            val inputStreamToUse = if (responseCode in 200..299) inputStream else errorStream

            inputStreamToUse.bufferedReader().use { reader ->
                val response = reader.readText()

                if(responseCode == 400){
                    return handleSignalAuthError(response);
                }

                println("Response Body of getUserAuthentication: $response")

                val parsedResponse = JSONObject(response)
                val userId = parsedResponse.getInt("id")
                val email = parsedResponse.getString("email")

                return Result.success(UserAuthenticationResult(
                    cookies = headerFields["Set-Cookie"]?.joinToString("; ") ?: "",
                    userId = userId,
                    email = email
                ))
            }
        }
    }

    catch (e: Exception) {
        Log.e("USER_AUTHENTICATION", "ERROR IN USER AUTHENTICATION", e)
        return Result.failure(e);
    }
}

fun handleSignalAuthError(response: String): Result<UserAuthenticationResult> {
    if(response.contains("Your email account was not found")){
        return Result.failure(Exception("Email address not found"))
    }

    return Result.failure(Exception("Unknown error occurred, please contact support"))
};


fun getExistingDaemons(cookies: String, userId: String): Result<List<String?>> {
    try {
        val url = URL("https://signal.staging.jimber.io/api/v1/companies/Jimber/daemons/user/$userId")

        with(url.openConnection() as HttpURLConnection) {
            requestMethod = "GET"

            // Set headers
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")

            if (cookies.isNotEmpty()) {
                setRequestProperty("Cookie", cookies)
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
                println("Response Body of getExistingDaemons: $response")

                val jsonArray = JSONArray(response)
                val namesList = mutableListOf<String>()

                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val name = jsonObject.getString("name")
                    namesList.add(name)
                }

                return Result.success(namesList);
            }
        }
    }
    catch(e: Exception) {
        Log.e("GET_EXISTING_DAEMONS", "ERROR IN GET EXISTING DAEMONS", e)
        return Result.failure(e);
    }
}


fun getCloudControllerPublicKey(cookies: String): Result<JSONObject> {
    try {
        val url = URL("https://signal.staging.jimber.io/api/v1/companies/Jimber/routers/cloud-network-controller")

        with(url.openConnection() as HttpURLConnection) {
            requestMethod = "GET"

            // Set headers
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")

            if (cookies.isNotEmpty()) {
                setRequestProperty("Cookie", cookies)
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
                println("Response Body of getCloudControllerPublicKey: $response")

                return Result.success(JSONObject(response));
            }
        }
    }
    catch(e: Exception) {
        Log.e("GET_CC_PUBLIC_KEY", "ERROR IN GET CLOUD CONTROLLER PUBLIC KEY", e)
        return Result.failure(e);
    }
}

fun createDaemon(createDaemonData: CreateDaemonData): Result<JSONObject> {
    try {
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
                setRequestProperty("Cookie", createDaemonData.cookies)
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
                println("Response Body of createDaemon: $response")

                return Result.success(JSONObject(response));
            }
        }
    }
    catch (e: Exception) {
        Log.e("CREATE_DAEMON", "ERROR IN CREATE DAEMON", e)
        return Result.failure(e);
    }
}

fun sendVerificationEmail(email: String): Result<Boolean> {
    try {
        val url = URL("https://signal.staging.jimber.io/api/v1/auth/send-user-token-code")

        // Create JSON object for the request body
        val jsonBody = JSONObject().apply {
            put("email", email)
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

            // Write the POST data to the output stream
            outputStream.use { outputStream ->
                outputStream.write(postData)
            }

            // Check the response code
            val responseCode = responseCode
            println("Response Code of sendVerificationEmail: $responseCode")

            val inputStream = if (responseCode in 200..299) {
                inputStream
            } else {
                errorStream
            }

            inputStream.bufferedReader().use { reader ->
                val response = reader.readText()
                println("Response Body: $response")

                return Result.success(true);
            }
        }
    }
    catch (e: Exception) {
        Log.e("EMAIL_VERIFICATION_CODE", "ERROR IN EMAIL VERIFICATION CODE REQUEST ", e)
        return Result.failure(e);
    }
}

fun verifyEmailWithToken(emailVerificationData: EmailVerificationData): Result<UserAuthenticationResult> {
    try {
        val url = URL("https://signal.staging.jimber.io/api/v1/auth/verify-email-token")

        // Create JSON object for the request body
        val jsonBody = JSONObject().apply {
            put("email", emailVerificationData.email)
            put("token", emailVerificationData.token)
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
                println("Response Body of verifyEmailWithToken: $response")

                val isSuccess = responseCode in 200..299

                val parsedObject = JSONObject(response)
                if(!isSuccess) {
                    if(parsedObject.getString("message").contains("The token you've entered is invalid")){
                        return Result.failure(Exception("Invalid code"))
                    }

                    return Result.failure(Exception("Received status code $responseCode from API while verifying with token"))
                }

                val userId = parsedObject.getInt("id")
                val email = parsedObject.getString("email")

                return Result.success(UserAuthenticationResult(
                    cookies = headerFields["Set-Cookie"]?.joinToString("; ") ?: "",
                    userId= userId,
                    email =email
                ));
            }
        }
    }
    catch (e: Exception) {
        Log.e("EMAIL_VERIFICATION_CODE", "ERROR IN EMAIL VERIFICATION CODE REQUEST ", e)
        return Result.failure(e);
    }
}