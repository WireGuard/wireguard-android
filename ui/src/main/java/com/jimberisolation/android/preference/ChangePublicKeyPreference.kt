package com.jimberisolation.android.preference

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.widget.Toast
import androidx.preference.Preference
import com.jimberisolation.android.Application.Companion.getTunnelManager
import com.jimberisolation.android.util.activity
import com.jimberisolation.android.util.lifecycleScope
import com.jimberisolation.config.Config
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class ChangePublicKeyPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    override fun getTitle() = "Change public key"

    override fun onClick() {
        lifecycleScope.launch {
            try {
                val manager = getTunnelManager();

                lifecycleScope.launch {
                    val tunnel = manager.getTunnels().first();

                    val currentConfig = tunnel.getConfigAsync()
                    val currentConfigString = currentConfig.toWgQuickString();

                    val oldPublicKey = currentConfig.peers.first().publicKey.toBase64();
                    val newPublicKey = "Pvjko4jNnh9kYElkVYTzzFq6QdKnDd57BY0nPKwsmTA=";

                    val updatedConfigString = currentConfigString.replace(Regex("(?<=PublicKey = )$oldPublicKey"), newPublicKey)

                    val updatedConfig = Config.parse(ByteArrayInputStream(updatedConfigString.toByteArray(StandardCharsets.UTF_8)))
                    tunnel.setConfigAsync(updatedConfig);

                    Toast.makeText(activity, "Public Key changed", Toast.LENGTH_SHORT).show()
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
