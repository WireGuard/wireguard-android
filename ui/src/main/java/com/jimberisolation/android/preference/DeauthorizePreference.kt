package com.jimberisolation.android.preference

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.jimberisolation.android.Application
import com.jimberisolation.android.Application.Companion.getTunnelManager
import com.jimberisolation.android.activity.SignInActivity
import com.jimberisolation.android.storage.SharedStorage
import com.jimberisolation.android.util.activity
import com.jimberisolation.android.util.lifecycleScope
import com.jimberisolation.config.Config
import kotlinx.coroutines.launch
import logout
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class DeauthorizePreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    override fun getTitle() = "Deauthorize"

    override fun onClick() {
        lifecycleScope.launch {
            try {
                lifecycleScope.launch {
                    SharedStorage.getInstance().clearRefreshToken()
                    SharedStorage.getInstance().clearAuthenticationToken()

                    Toast.makeText(activity, "Tokens cleared", Toast.LENGTH_SHORT).show()
                }
            }
            catch (_: Exception) {
            }
        }
    }

    init {
        lifecycleScope.launch {
        }
    }
}
