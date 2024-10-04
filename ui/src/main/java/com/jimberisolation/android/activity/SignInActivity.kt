package com.jimberisolation.android.activity

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
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.android.material.snackbar.Snackbar
import com.jimberisolation.android.R
import com.jimberisolation.android.util.TunnelImporter.importTunnel
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SignInParameters
import com.microsoft.identity.client.exception.MsalException
import createNetworkIsolationDaemonConfig
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
                .withScopes(Arrays.asList("f1373772-6623-4090-9204-3cb04b9d46c9/.default"))
                .withCallback(getAuthInteractiveCallback())
                .build()
            mSingleAccountApp!!.signIn(signInParameters)
        }

        findViewById<View>(R.id.email_sign_in)?.setOnClickListener {
            val intent = Intent(this, EmailRegistrationActivity::class.java)
            startActivity(intent)
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("107269124183-7i5qr58qcfaar9u4fbeodcs43s61lmtm.apps.googleusercontent.com")
            .requestEmail()
            .build()

        signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)

            // Always sign out
            val googleSignInClient = GoogleSignIn.getClient(this, gso)
            googleSignInClient.signOut()

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
                    getCurrentAccount();
                }

                override fun onError(exception: MsalException) {
                    // Log the exception
                    exception.printStackTrace()
                }
            })

    }

    // Function to get the current account
    fun getCurrentAccount() {
        mSingleAccountApp?.getCurrentAccountAsync(object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
            override fun onAccountLoaded(activeAccount: IAccount?) {
                if (activeAccount != null) {
                     mSingleAccountApp?.signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
                        override fun onSignOut() {
                            println("Successfully signed out.")
                        }

                        override fun onError(exception: MsalException) {
                            exception.printStackTrace()
                        }
                    })
                    val accountName = activeAccount.username
                    println("Signed in as: $accountName")
                } else {
                    // No account is signed in
                    println("No account is signed in.")
                }
            }

            override fun onAccountChanged(priorAccount: IAccount?, currentAccount: IAccount?) {
                // Account changed
                if (currentAccount != null) {
                    // Do something with the new account info
                    val accountName = currentAccount.username
                    println("Account changed to: $accountName")
                } else {
                    // Signed out
                    println("Signed out.")
                }
            }

            override fun onError(exception: MsalException) {
                // Log the exception
                exception.printStackTrace()
            }
        })
    }

    private fun handleSignInResultGoogle(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            val token = account.idToken.toString()

            // Launch a coroutine in the lifecycleScope
            lifecycleScope.launch {
                val wireguardConfigResult = createNetworkIsolationDaemonConfig(token, AuthenticationType.Google)

                if (wireguardConfigResult.isFailure) {
                    val createDaemonException = wireguardConfigResult.exceptionOrNull()
                    val view = findViewById<View>(android.R.id.content) // or some other view in your layout
                    Snackbar.make(view, createDaemonException?.message.toString(), Snackbar.LENGTH_LONG).show()
                    return@launch
                }

                val wireguardConfig = wireguardConfigResult.getOrThrow().toString()
                Log.d("Configuration", wireguardConfig)

                importTunnelAndNavigate(wireguardConfig)
            }
        } catch (e: ApiException) {
            Log.e("Authentication", "An error occurred", e)
        }
    }

    private suspend fun importTunnelAndNavigate(result: String) {
        importTunnel(result) { }

        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }


    private fun getAuthInteractiveCallback(): AuthenticationCallback {
        return object : AuthenticationCallback {
            override fun onSuccess(authenticationResult: IAuthenticationResult) {
                val token = authenticationResult.accessToken

                // Launch a coroutine in the lifecycleScope
                lifecycleScope.launch {
                    val wireguardConfigResult = createNetworkIsolationDaemonConfig(token, AuthenticationType.Google)

                    if (wireguardConfigResult.isFailure) {
                        val createDaemonException = wireguardConfigResult.exceptionOrNull()
                        val view = findViewById<View>(android.R.id.content) // or some other view in your layout
                        Snackbar.make(view, createDaemonException?.message.toString(), Snackbar.LENGTH_LONG).show()
                        return@launch
                    }

                    val wireguardConfig = wireguardConfigResult.getOrThrow().toString()
                    Log.d("Configuration", wireguardConfig)

                    importTunnelAndNavigate(wireguardConfig)
                }
            }

            override fun onError(exception: MsalException) {
                Log.e("Error", "Error", exception)
            }

            override fun onCancel() {
                /* User canceled the authentication */
                Log.d("cancelled", "User cancelled login.")
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