package com.jimberisolation.android.activity

import Company
import Daemon
import DnsServer
import GenerateWireguardConfig
import NetworkController
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.jimberisolation.android.R
import com.jimberisolation.android.util.TunnelImporter
import com.jimberisolation.android.util.createDaemon
import com.jimberisolation.android.util.generateEd25519KeyPair
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SignInParameters
import com.microsoft.identity.client.exception.MsalException
import com.jimberisolation.android.util.getCNCPublicKey
import com.jimberisolation.android.util.getUserAuthentication
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.Arrays

class SignInActivity : AppCompatActivity() {

    // Google
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var signInLauncher: ActivityResultLauncher<Intent>

    // MS
    private var mSingleAccountApp: ISingleAccountPublicClientApplication? = null

    private var actionBar: ActionBar? = null
    private var backPressedCallback: OnBackPressedCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sign_in_activity)

        actionBar = supportActionBar
        actionBar?.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM)
        actionBar?.setCustomView(R.layout.jimber_action_bar)

        getSupportActionBar()?.setDisplayHomeAsUpEnabled(true);

        backPressedCallback = onBackPressedDispatcher.addCallback(this) { handleBackPressed() }

        findViewById<View>(R.id.google_sign_in)?.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            signInLauncher.launch(signInIntent)
        }

        findViewById<View>(R.id.microsoft_sign_in)?.setOnClickListener {
            val signInParameters = SignInParameters.builder()
                .withActivity(this)
                .withLoginHint(null)
                .withScopes(Arrays.asList<String>("user.read"))
                .withCallback(getAuthInteractiveCallback())
                .build()
            mSingleAccountApp!!.signIn(signInParameters)
        }

        findViewById<View>(R.id.email_sign_in)?.setOnClickListener {
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("107269124183-7i5qr58qcfaar9u4fbeodcs43s61lmtm.apps.googleusercontent.com")
            .requestEmail()
            .build()

        signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            handleSignInResultGoogle(task)
        }

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // MS
        PublicClientApplication.createSingleAccountPublicClientApplication(
            this,
            R.raw.msal_config,
            object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                override fun onCreated(application: ISingleAccountPublicClientApplication) {
                    mSingleAccountApp = application
                }

                override fun onError(exception: MsalException) {
                    // Log the exception
                    exception.printStackTrace()
                }
            })

    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun handleSignInResultGoogle(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)

            val (authCookie, userId) = getUserAuthentication(account.idToken.toString());
            val cloudControllerDetails = getCNCPublicKey(authCookie)

            val createdDaemon = createDaemon(authCookie, userId.toString(), cloudControllerDetails.getString("routerPublicKey"))
            Log.d( "DATA", cloudControllerDetails.getString("routerPublicKey"))
            Log.d( "DATA", createdDaemon.toString())

            val (pk, sk) = generateEd25519KeyPair()

            val company = Company("Jimber")
            val daemon = Daemon(sk, createdDaemon.getString("ipAddress"))
            val dnsServer = DnsServer(cloudControllerDetails.getString("ipAddress"))
            val networkController = NetworkController(cloudControllerDetails.getString("routerPublicKey"), cloudControllerDetails.getString("endpointAddress"), 51820)
            val result = GenerateWireguardConfig(company, daemon, dnsServer, networkController)

            Log.d("NICE",  result)

            GlobalScope.launch {
                doStuff(result)
            }

        } catch (e: ApiException) {
            Log.e("ERREURSSS", "ddd", e);
        }
    }


    suspend fun doStuff(result: String) {
        TunnelImporter.importTunnel(result) { }
    }


    private fun getAuthInteractiveCallback(): AuthenticationCallback {
        return object : AuthenticationCallback {
            override fun onSuccess(authenticationResult: IAuthenticationResult) {
                Log.d("test", "")
            }

            override fun onError(exception: MsalException) {
                Log.e("erreur", "errreur", exception)
            }

            override fun onCancel() {
                /* User canceled the authentication */
                Log.d(
                    "cancelled",
                    "User cancelled login."
                )
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.settings, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // The back arrow in the action bar should act the same as the back button.
                onBackPressedDispatcher.onBackPressed()
                true
            }

            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun handleBackPressed() {
        finish()
    }
}