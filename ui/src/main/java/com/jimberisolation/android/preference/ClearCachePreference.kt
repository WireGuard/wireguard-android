package com.jimberisolation.android.preference

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat.startActivity
import androidx.preference.Preference
import com.jimberisolation.android.Application.Companion.getTunnelManager
import com.jimberisolation.android.activity.SignInActivity
import com.jimberisolation.android.storage.SharedStorage
import com.jimberisolation.android.util.activity
import com.jimberisolation.android.util.lifecycleScope
import kotlinx.coroutines.launch

class ClearCachePreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    override fun getTitle() = "Clear cache"

    override fun onClick() {
        lifecycleScope.launch {
            try {
                val tunnelManager = getTunnelManager();
                val tunnels = tunnelManager.getTunnels();

                tunnels.forEach(){
                    val tunnel = it;
                    Log.d("DELETE_TUNNEL", it.name)
                    tunnel.deleteAsync()
                    Log.d("DELETE_TUNNEL", "DONE")
                }

                SharedStorage.getInstance().clearAll()
                SharedStorage.getInstance().clearAuthenticationToken()

                Toast.makeText(activity, "Cache cleared", Toast.LENGTH_SHORT).show()

                val intent = Intent(context, SignInActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                context.startActivity(intent)
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
