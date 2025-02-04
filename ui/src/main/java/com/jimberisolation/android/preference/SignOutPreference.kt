package com.jimberisolation.android.preference

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.util.Log
import android.widget.Toast
import androidx.preference.Preference
import com.jimberisolation.android.Application.Companion.getTunnelManager
import com.jimberisolation.android.activity.SignInActivity
import com.jimberisolation.android.authentication.logout
import com.jimberisolation.android.backend.Tunnel
import com.jimberisolation.android.storage.SharedStorage
import com.jimberisolation.android.util.lifecycleScope
import kotlinx.coroutines.launch

class SignOutPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    override fun getTitle() = "Sign out"

    override fun getSummary(): CharSequence? {
        return SharedStorage.getInstance().getCurrentUser()?.email;
    }

    override fun onClick() {
        lifecycleScope.launch {
            try {
                logout()

                val tunnelManager = getTunnelManager();
                val tunnels = tunnelManager.getTunnels();

                tunnels.forEach(){
                    val tunnel = it;
                    Log.e("Bring tunnel down", it.name)
                    tunnel.setStateAsync(Tunnel.State.DOWN)
                    Log.e("Bring tunnel down", "DONE")
                }

                val intent = Intent(context, SignInActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                context.startActivity(intent)

                SharedStorage.getInstance().clearUserLoginData()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Logout failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    init {
        lifecycleScope.launch {
        }
    }
}
