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

class ChangePublicIpPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    override fun getTitle() = "Change public IP"

    override fun onClick() {
        lifecycleScope.launch {
            try {
                val manager = getTunnelManager();

                lifecycleScope.launch {
                    val tunnel = manager.getTunnels().first();

                    val currentConfig = tunnel.getConfigAsync()
                    val currentConfigString = currentConfig.toWgQuickString();

                    val oldPublicIp = currentConfig.peers.first().endpoint.get().host;
                    val newPublicIp = "1.1.1.1";

                    val updatedConfigString = currentConfigString.replace(Regex("(?<=Endpoint = )$oldPublicIp"), newPublicIp)

                    val updatedConfig = Config.parse(ByteArrayInputStream(updatedConfigString.toByteArray(StandardCharsets.UTF_8)))
                    tunnel.setConfigAsync(updatedConfig);

                    Toast.makeText(activity, "Public IP changed", Toast.LENGTH_SHORT).show()
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
