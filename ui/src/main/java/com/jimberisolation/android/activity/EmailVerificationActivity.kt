package com.jimberisolation.android.activity

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.api.ApiException
import com.google.android.material.snackbar.Snackbar
import com.jimberisolation.android.R
import com.jimberisolation.android.util.EmailVerificationData
import com.jimberisolation.android.util.TunnelImporter.importTunnel
import com.jimberisolation.android.util.UserAuthenticationResult
import com.jimberisolation.android.util.sendVerificationEmail
import com.jimberisolation.android.util.verifyEmailWithToken
import createNetworkIsolationDaemonConfigFromEmailVerification
import kotlinx.coroutines.launch

class EmailVerificationActivity : AppCompatActivity() {
    private var actionBar: ActionBar? = null
    private var backPressedCallback: OnBackPressedCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.email_verification_activity)

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
            val result = sendVerificationEmail(email.toString())

            if(result.isFailure) {
                Snackbar.make(snackView,"Something went wrong with resending email, please contact support", Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }

            Snackbar.make(snackView,"Email successfully resent", Snackbar.LENGTH_LONG).show()
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
                Snackbar.make(snackView,"Please fill in a valid verification code", Snackbar.LENGTH_LONG).show()
                return@setOnClickListener;
            }

            val verifyResult = verifyEmailWithToken(EmailVerificationData(email.toString(), verificationCode.toInt()))
            if(verifyResult.isFailure) {
                val verifyException = verifyResult.exceptionOrNull();
                val view = findViewById<View>(android.R.id.content) // or some other view in your layout
                Snackbar.make(view, verifyException?.message.toString(), Snackbar.LENGTH_LONG).show()

                return@setOnClickListener;
            }

            val userAuthenticationResult = verifyResult.getOrThrow();
            handleEmailVerification(userAuthenticationResult);
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
        try {
            val wireguardConfigResult = createNetworkIsolationDaemonConfigFromEmailVerification(userAuthenticationResult)
            if(wireguardConfigResult.isFailure) {
                val createDaemonException = wireguardConfigResult.exceptionOrNull();
                val view = findViewById<View>(android.R.id.content) // or some other view in your layout
                Snackbar.make(view, createDaemonException?.message.toString(), Snackbar.LENGTH_LONG).show()
                return;
            }

            val wireguardConfig = wireguardConfigResult.getOrThrow().toString()
            Log.d("Configuration", wireguardConfig)

            lifecycleScope.launch {
                importTunnelAndNavigate(wireguardConfig)
            }

        } catch (e: ApiException) {
            Log.e("Authentication", "An error occurred", e);
        }
    }

    private suspend fun importTunnelAndNavigate(result: String) {
        importTunnel(result) { }

        val intent = Intent(this, MainActivity::class.java) // Missing intent assignment
        startActivity(intent)
        finish()

    }

    private fun handleBackPressed() {
        finish()
    }
}