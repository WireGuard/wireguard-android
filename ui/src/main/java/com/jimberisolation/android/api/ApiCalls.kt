import android.util.Log
import com.jimberisolation.android.storage.SharedStorage
import com.jimberisolation.android.util.CreateDaemonData
import com.jimberisolation.android.util.CreatedDaemonResult
import com.jimberisolation.android.util.EmailVerificationData
import com.jimberisolation.android.util.GetDaemonsNameResult
import com.jimberisolation.android.util.GetEmailVerificationCodeData
import com.jimberisolation.android.util.RouterPublicKeyResult
import com.jimberisolation.android.util.UserAuthenticationResult
import org.json.JSONObject

suspend fun getUserAuthenticationV2(idToken: String, authenticationType: AuthenticationType): Result<UserAuthenticationResult> {
    val type = when (authenticationType) {
        AuthenticationType.Google -> "google"
        AuthenticationType.Microsoft -> "microsoft"
    }

    return try {
        val authRequest = AuthRequest(idToken)
        val response = ApiClient.apiService.getUserAuthentication(type, authRequest)

        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string()
            errorBody?.let {
                val jsonObject = JSONObject(it)
                val message = jsonObject.getString("message")

                return Result.failure(Exception(message))
            }
        }

        val result = response.body() ?: return Result.failure(NullPointerException("Response body is null"))
        val cookies = response.headers().values("Set-Cookie")

        saveTokens(cookies.joinToString("; "))
        Result.success(result)

    } catch (e: Exception) {
        Log.e("USER_AUTHENTICATION", "ERROR IN USER AUTHENTICATION", e)
        Result.failure(e)
    }
}

suspend fun getCloudControllerPublicKeyV2(company: String): Result<RouterPublicKeyResult> {
    return try {
        val cookies = getCookieString();
        val result = ApiClient.apiService.getCloudControllerPublicKey(company, cookies)

        Result.success(result)
    } catch (e: Exception) {
        Log.e("GET_CLOUD_CONTROLLER_PUBLIC_KEY", "ERROR IN GET_CLOUD_CONTROLLER_PUBLIC_KEY", e)
        Result.failure(e)
    }
}

suspend fun getExistingDaemonsV2(userId: String, company: String): Result<List<GetDaemonsNameResult>> {
    return try {
        val cookies = getCookieString();
        val result = ApiClient.apiService.getExistingDaemons(userId, company, cookies)

        Result.success(result)
    } catch (e: Exception) {
        Log.e("GET_EXISTING_DAEMONS", "ERROR IN GET EXISTING DAEMONS", e)
        Result.failure(e)
    }
}

suspend fun createDaemonV2(userId: String, company: String, createDaemonData: CreateDaemonData): Result<CreatedDaemonResult> {
    return try {
        val cookies = getCookieString()
        val result = ApiClient.apiService.createDaemon(userId, company, createDaemonData, cookies)

        Result.success(result)
    } catch (e: Exception) {
        Log.e("CREATE_DAEMON", "ERROR IN CREATE DAEMON", e)
        Result.failure(e)
    }
}

suspend fun sendVerificationEmail(email: String): Result<Boolean> {
    return try {
        val result = ApiClient.apiService.sendVerificationEmail(GetEmailVerificationCodeData(email)) // Adjust as needed
        Result.success(result)

    } catch (e: Exception) {
        Log.e("EMAIL_VERIFICATION_CODE", "ERROR IN EMAIL VERIFICATION CODE REQUEST", e)
        Result.failure(e)
    }
}

suspend fun verifyEmailWithToken(emailVerificationData: EmailVerificationData): Result<UserAuthenticationResult> {
    return try {
        val response = ApiClient.apiService.verifyEmailWithToken(emailVerificationData)

        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string()
            errorBody?.let {
                val jsonObject = JSONObject(it)
                val message = jsonObject.getString("message")

                return Result.failure(Exception(message))
            }
        }

        val result = response.body() ?: return Result.failure(NullPointerException("Response body is null"))
        val cookies = response.headers().values("Set-Cookie")

        saveTokens(cookies.joinToString("; "))
        Result.success(result)

        Result.success(result)

    } catch (e: Exception) {
        Log.e("EMAIL_VERIFICATION_CODE", "ERROR IN EMAIL VERIFICATION CODE REQUEST", e)
        Result.failure(e)
    }
}

fun extractToken(cookieHeader: String, tokenName: String): String? {
    val cookies = cookieHeader.split("; ")
    for (cookie in cookies) {
        if (cookie.startsWith("$tokenName=")) {
            return cookie.substringAfter("$tokenName=")
        }
    }
    return null
}

private fun saveTokens(cookies: String) {
    val authToken = extractToken(cookies, "Authentication")
    val refreshToken = extractToken(cookies, "Refresh")

    val sharedStorage = SharedStorage.getInstance()
    sharedStorage.saveRefreshToken(refreshToken ?: "")
    sharedStorage.saveAuthenticationToken(authToken ?: "")
}

private fun getCookieString(): String {
    val authToken = SharedStorage.getInstance().getAuthenticationToken();
    val refreshToken = SharedStorage.getInstance().getRefreshToken();

    return "Authentication=$authToken; Refresh=$refreshToken";
}