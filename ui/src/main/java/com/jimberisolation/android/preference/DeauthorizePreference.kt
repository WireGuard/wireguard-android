package com.jimberisolation.android.preference

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.widget.Toast
import androidx.preference.Preference
import com.jimberisolation.android.Application.Companion.getTunnelManager
import com.jimberisolation.android.backend.Tunnel
import com.jimberisolation.android.storage.SharedStorage
import com.jimberisolation.android.util.activity
import com.jimberisolation.android.util.lifecycleScope
import kotlinx.coroutines.launch

class DeauthorizePreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    override fun getTitle() = "Deauthorize"

    override fun onClick() {
        lifecycleScope.launch {
            try {
                lifecycleScope.launch {

                    val tunnelManager = getTunnelManager();
                    val tunnels = tunnelManager.getTunnels();

                    tunnels.forEach(){
                        val tunnel = it;
                        Log.d("DELETE_TUNNEL", it.name)
                        tunnel.setStateAsync(Tunnel.State.DOWN)
                        Log.d("DELETE_TUNNEL", "DONE")
                    }

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
