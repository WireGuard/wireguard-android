package com.jimberisolation.android.activity

import AuthenticationType
import Config
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import authenticateUser
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.android.material.snackbar.Snackbar
import com.jimberisolation.android.Application.Companion.getTunnelManager
import com.jimberisolation.android.BuildConfig
import com.jimberisolation.android.R
import com.jimberisolation.android.storage.SharedStorage
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
import getDeviceHostname
import kotlinx.coroutines.CompletableDeferred
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

    private var daemonName: String? = getDeviceHostname();

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sign_in_activity)

        SharedStorage.initialize(this)

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

        println(BuildConfig.APPLICATION_ID)
        println(Config.GOOGLE_AUTH_ID)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(Config.GOOGLE_AUTH_ID)
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
        lifecycleScope.launch {
            try {
                val account = completedTask.getResult(ApiException::class.java)
                val token = account.idToken.toString()

                val userAuthenticationResult = authenticateUser(token, AuthenticationType.Google);
                if(userAuthenticationResult.isFailure) {
                    val userAuthenticationException = userAuthenticationResult.exceptionOrNull()
                    val view = findViewById<View>(android.R.id.content) // or some other view in your layout
                    Snackbar.make(view, userAuthenticationException?.message.toString(), Snackbar.LENGTH_LONG).show()
                    return@launch
                }

                val companyName = userAuthenticationResult.getOrThrow().company.name
                val daemonAlreadyInStorage = SharedStorage.getInstance().getWireguardKeyPair(companyName)
                if(daemonAlreadyInStorage == null) {
                    daemonName = showNameInputDialog() ?: return@launch
                }
                else {
                    daemonName = daemonAlreadyInStorage.daemonName
                }

                // Proceed with the WireGuard config
                val wireguardConfigResult = createNetworkIsolationDaemonConfig(userAuthenticationResult.getOrThrow(), daemonName!!)

                if (wireguardConfigResult.isFailure) {
                    val createDaemonException = wireguardConfigResult.exceptionOrNull()
                    val view = findViewById<View>(android.R.id.content) // or some other view in your layout
                    Snackbar.make(view, createDaemonException?.message.toString(), Snackbar.LENGTH_LONG).show()
                    return@launch
                }

                val wireguardConfig = wireguardConfigResult.getOrThrow()?.wireguardConfig!!
                val daemonId = wireguardConfigResult.getOrThrow()?.daemonId!!

                Log.d("Configuration", wireguardConfig)

                importTunnelAndNavigate(wireguardConfig, daemonId, companyName)
            } catch (e: ApiException) {
                Log.e("Authentication", "An error occurred", e)
            }
        }
    }


    private suspend fun importTunnelAndNavigate(result: String, daemonId: Number, companyName: String) {
        val manager = getTunnelManager()

        val alreadyExistingTunnel = manager.getTunnels().find { it.name == companyName }
        if(alreadyExistingTunnel == null) {
            importTunnel(result, daemonId) { }
        }

        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }


    private fun getAuthInteractiveCallback(): AuthenticationCallback {
        return object : AuthenticationCallback {
            override fun onSuccess(authenticationResult: IAuthenticationResult) {
                val token = authenticationResult.accessToken
                lifecycleScope.launch {
                    val userAuthenticationResult = authenticateUser(token, AuthenticationType.Microsoft);
                    if(userAuthenticationResult.isFailure) {
                        val userAuthenticationException = userAuthenticationResult.exceptionOrNull()
                        val view = findViewById<View>(android.R.id.content) // or some other view in your layout
                        Snackbar.make(view, userAuthenticationException?.message.toString(), Snackbar.LENGTH_LONG).show()
                        return@launch
                    }

                    val companyName = userAuthenticationResult.getOrThrow().company.name
                    val daemonAlreadyInStorage = SharedStorage.getInstance().getWireguardKeyPair(companyName)
                    if(daemonAlreadyInStorage == null) {
                        daemonName = showNameInputDialog() ?: return@launch
                    }
                    else {
                        daemonName = daemonAlreadyInStorage.daemonName
                    }

                    // Proceed with the WireGuard config
                    val wireguardConfigResult = createNetworkIsolationDaemonConfig(userAuthenticationResult.getOrThrow(), daemonName!!)

                    if (wireguardConfigResult.isFailure) {
                        val createDaemonException = wireguardConfigResult.exceptionOrNull()
                        val view = findViewById<View>(android.R.id.content) // or some other view in your layout
                        Snackbar.make(view, createDaemonException?.message.toString(), Snackbar.LENGTH_LONG).show()
                        return@launch
                    }

                    val wireguardConfig = wireguardConfigResult.getOrThrow()?.wireguardConfig!!
                    val daemonId = wireguardConfigResult.getOrThrow()?.daemonId!!

                    Log.d("Configuration", wireguardConfig)

                    importTunnelAndNavigate(wireguardConfig, daemonId, companyName)
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

    private suspend fun showNameInputDialog(): String? {
        // Create a deferred result to await the input
        val result = CompletableDeferred<String?>()

        // Inflate the dialog's custom view
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_input_name, null)

        // Build the dialog
        val dialogBuilder = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)

        // Get reference to EditText and buttons in the dialog
        val nameInput = dialogView.findViewById<EditText>(R.id.nameInput)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnSubmit = dialogView.findViewById<Button>(R.id.btnSubmit)

        nameInput.setText(daemonName);

        // Create the dialog
        val dialog = dialogBuilder.create()

        // Handle the Cancel button
        btnCancel.setOnClickListener {
            result.complete(null)  // Return null if canceled
            dialog.dismiss()
        }

        // Handle the Submit button
        btnSubmit.setOnClickListener {
            val name = nameInput.text.toString().trim()

            if (name.isNotEmpty()) {
                result.complete(name)  // Pass the entered name to the result
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Please enter a valid name.", Toast.LENGTH_SHORT).show()
            }
        }

        // Show the dialog
        dialog.show()

        // Await the result before proceeding
        return result.await()
    }

    private fun handleBackPressed() {
        finish()
    }
}