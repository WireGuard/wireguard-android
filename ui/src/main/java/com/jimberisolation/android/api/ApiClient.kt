import com.jimberisolation.android.storage.SharedStorage
import com.jimberisolation.android.util.CreateDaemonData
import com.jimberisolation.android.util.CreatedDaemonResult
import com.jimberisolation.android.util.EmailVerificationData
import com.jimberisolation.android.util.GetDaemonsNameResult
import com.jimberisolation.android.util.GetEmailVerificationCodeData
import com.jimberisolation.android.util.RouterPublicKeyResult
import com.jimberisolation.android.util.UserAuthenticationResult
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

// Interceptor to check for 401 Unauthorized responses
class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code == 401) {
            // Handle unauthorized response here (e.g., refresh token, log out user, etc.)
            println("Unauthorized! Handle this accordingly.")
        }
        return response
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
    suspend fun  getCloudControllerPublicKey(@Path("company") company: String, @Header("Cookie") cookies: String): RouterPublicKeyResult

    @POST("companies/{company}/daemons/user/{userId}")
    suspend fun createDaemon(@Path("userId") userId: String, @Path("company") company: String, @Body createDaemonData: CreateDaemonData, @Header("Cookie") cookies: String): CreatedDaemonResult

    @POST("auth/send-user-token-code")
    suspend fun sendVerificationEmail(@Body emailVerificationData: GetEmailVerificationCodeData): Boolean

    @POST("auth/verify-email-token")
    suspend fun verifyEmailWithToken(@Body emailVerificationData: EmailVerificationData): retrofit2.Response<UserAuthenticationResult>
}

// Data class for AuthRequest
data class AuthRequest(
    val idToken: String
)

// ApiClient class
object ApiClient {
    private const val BASE_URL = "https://signal.staging.jimber.io/api/v1/"

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