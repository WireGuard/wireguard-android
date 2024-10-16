import androidx.lifecycle.MutableLiveData
import com.jimberisolation.android.util.CreateDaemonData
import com.jimberisolation.android.util.CreatedDaemonResult
import com.jimberisolation.android.util.DeleteDaemonResult
import com.jimberisolation.android.util.EmailVerificationData
import com.jimberisolation.android.util.GetDaemonsNameResult
import com.jimberisolation.android.util.GetEmailVerificationCodeData
import com.jimberisolation.android.util.RefreshResult
import com.jimberisolation.android.util.RouterPublicKeyResult
import com.jimberisolation.android.util.UserAuthenticationResult
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

object AuthEventManager {
    val authFailedEvent = MutableLiveData<Boolean>()
}

class AuthInterceptor : Interceptor {
    private val excludedUrls = listOf(
        Config.BASE_URL + "auth/refresh",
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Exclude the refresh token request
        if (excludedUrls.any { originalRequest.url.toString().startsWith(it) }) {
            return chain.proceed(originalRequest)
        }

        var response = chain.proceed(originalRequest)

        // Check for 401 Unauthorized
        if (response.code == 401) {
            response.close()

            // Attempt to renew JWT token
            runBlocking {
                val newToken = renewJwt()
                if (newToken != null) {
                    // Retry original request with the new token
                    val newRequest = originalRequest.newBuilder()
                        .header("Authorization", "Bearer $newToken")
                        .build()
                    response = chain.proceed(newRequest)
                } else {
                    // Broadcast event that user needs to log in again
                    AuthEventManager.authFailedEvent.postValue(true)
                }
            }
        }

        return response
    }

    private suspend fun renewJwt(): String? {
        return try {
            val newAccessToken = refreshToken()
            if (newAccessToken.isSuccess) {
                return newAccessToken.getOrThrow().accessToken
            }
            else{
                null
            }
        } catch (e: Exception) {
            println("Error renewing JWT: ${e.message}")
            null
        }
    }
}

// Set up the logging interceptor
val logging = HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.BODY // Log the request and response body
}

// Retrofit API service interface
interface ApiService {
    @POST("auth/verify-{type}-id")
    suspend fun getUserAuthentication(@Path("type") type: String, @Body request: AuthRequest): retrofit2.Response<UserAuthenticationResult>

    @GET("companies/{company}/daemons/user/{userId}")
    suspend fun getExistingDaemons(@Path("userId") userId: String,  @Path("company") company: String, @Header("Cookie") cookies: String): List<GetDaemonsNameResult>

    @GET("companies/{company}/routers/cloud-network-controller")
    suspend fun getCloudControllerPublicKey(@Path("company") company: String, @Header("Cookie") cookies: String): RouterPublicKeyResult

    @POST("companies/{company}/daemons/user/{userId}")
    suspend fun createDaemon(@Path("userId") userId: String, @Path("company") company: String, @Body createDaemonData: CreateDaemonData, @Header("Cookie") cookies: String): CreatedDaemonResult

    @DELETE("companies/{company}/daemons/user/{userId}/{daemonId}")
    suspend fun deleteDaemon(@Path("userId") userId: String, @Path("company") company: String, @Path("daemonId") daemonId: String, @Header("Cookie") cookies: String): retrofit2.Response<DeleteDaemonResult>

    @POST("auth/send-user-token-code")
    suspend fun sendVerificationEmail(@Body emailVerificationData: GetEmailVerificationCodeData): Boolean

    @POST("auth/verify-email-token")
    suspend fun verifyEmailWithToken(@Body emailVerificationData: EmailVerificationData): retrofit2.Response<UserAuthenticationResult>

    @GET("auth/refresh")
    suspend fun refreshToken(@Header("Cookie") cookies: String): retrofit2.Response<RefreshResult>

    @POST("auth/logout")
    suspend fun logout(@Header("Cookie") cookies: String): retrofit2.Response<Boolean>

}

// Data class for AuthRequest
data class AuthRequest(
    val idToken: String
)

// ApiClient class
object ApiClient {
    private const val BASE_URL = Config.BASE_URL

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .addInterceptor(AuthInterceptor())
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)
}