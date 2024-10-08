package com.jimberisolation.android.activity

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.jimberisolation.android.Application.Companion.getTunnelManager
import com.jimberisolation.android.R
import com.jimberisolation.android.storage.SharedStorage
import com.jimberisolation.android.util.EmailVerificationData
import com.jimberisolation.android.util.TunnelImporter.importTunnel
import com.jimberisolation.android.util.UserAuthenticationResult
import createNetworkIsolationDaemonConfigFromEmailVerification
import getDeviceHostname
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sendVerificationEmail
import verifyEmailWithToken

class EmailVerificationActivity : AppCompatActivity() {
    private var actionBar: ActionBar? = null
    private var backPressedCallback: OnBackPressedCallback? = null

    private var daemonName: String? = getDeviceHostname();

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.email_verification_activity)

        SharedStorage.initialize(this)

        val errorTextView: TextView = findViewById(R.id.verification_error)

        actionBar = supportActionBar
        actionBar?.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM)
        actionBar?.setCustomView(R.layout.jimber_action_bar)

        getSupportActionBar()?.setDisplayHomeAsUpEnabled(true);

        backPressedCallback = onBackPressedDispatcher.addCallback(this) { handleBackPressed() }

        val email = intent.getStringExtra("EMAIL")

        val snackView = findViewById<View>(android.R.id.content)

        val tokenInputs = listOf(
            findViewById(R.id.token_digit_1),
            findViewById(R.id.token_digit_2),
            findViewById(R.id.token_digit_3),
            findViewById(R.id.token_digit_4),
            findViewById(R.id.token_digit_5),
            findViewById<EditText>(R.id.token_digit_6)
        )

        val textView: TextView = findViewById(R.id.resend_email)

        textView.setOnClickListener {
            // Launch a coroutine in the main scope
            CoroutineScope(Dispatchers.Main).launch {
                // Call the suspending function
                val result = sendVerificationEmail(email.toString())

                if (result.isFailure) {
                    Snackbar.make(snackView, "Something went wrong with resending email, please contact support", Snackbar.LENGTH_LONG).show()
                    return@launch // Return from the coroutine if there's a failure
                }

                Snackbar.make(snackView, "Email successfully resent", Snackbar.LENGTH_LONG).show()
            }
        }

        findViewById<View>(R.id.proceed)?.setOnClickListener {
            var verificationCode = ""

            for (i in 1..7) {
                val resID = resources.getIdentifier("token_digit_$i", "id", packageName)
                val editText = findViewById<EditText>(resID)

                if (editText != null) {
                    verificationCode += editText.text.toString()
                }
            }

            if(verificationCode.length != 6) {
                errorTextView.text = "Please fill in a valid verification code"
                return@setOnClickListener;
            }

            CoroutineScope(Dispatchers.Main).launch {
                val verifyResult = verifyEmailWithToken(EmailVerificationData(email.toString(), verificationCode.toInt()))
                if(verifyResult.isFailure) {
                    val verifyException = verifyResult.exceptionOrNull();
                    errorTextView.text = verifyException?.message

                    return@launch;
                }

                errorTextView.text = ""
                val userAuthenticationResult = verifyResult.getOrThrow();
                handleEmailVerification(userAuthenticationResult);
            }
        }

        for (i in tokenInputs.indices) {
            tokenInputs[i].addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    if (s?.length == 1) {
                        if (i < tokenInputs.size - 1) {
                            tokenInputs[i + 1].requestFocus()  // Move to next input field
                        }
                    } else if (s?.length == 0 && i > 0) {
                        tokenInputs[i - 1].requestFocus()  // Move to previous input field
                    }
                }

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
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

    private fun handleEmailVerification(userAuthenticationResult: UserAuthenticationResult) {
        if(daemonName == null) return;

        lifecycleScope.launch {
            try {
                val daemonName = showNameInputDialog() ?: return@launch

                val wireguardConfigResult = createNetworkIsolationDaemonConfigFromEmailVerification(userAuthenticationResult, daemonName)

                if (wireguardConfigResult.isFailure) {
                    val createDaemonException = wireguardConfigResult.exceptionOrNull()
                    val view = findViewById<View>(android.R.id.content) // or some other view in your layout
                    Snackbar.make(view, createDaemonException?.message.toString(), Snackbar.LENGTH_LONG).show()
                    return@launch
                }

                val result = wireguardConfigResult.getOrThrow()

                val wireguardConfig = result?.wireguardConfig!!
                val companyName = result.company

                Log.d("Configuration", wireguardConfig)

                importTunnelAndNavigate(wireguardConfig, companyName)

            } catch (e: Exception) {
                Log.e("Authentication", "An error occurred", e)
                val view = findViewById<View>(android.R.id.content) // or some other view in your layout
                Snackbar.make(view, "An error occurred: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun importTunnelAndNavigate(result: String, companyName: String) {
        val manager = getTunnelManager()

        val alreadyExistingTunnel = manager.getTunnels().find { it.name == companyName }
        if(alreadyExistingTunnel == null) {
            importTunnel(result) { }
        }

        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }

    private fun handleBackPressed() {
        finish()
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

}