package com.jimberisolation.android.preference

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import androidx.preference.Preference
import com.jimberisolation.android.Application.Companion.getTunnelManager
import com.jimberisolation.android.util.lifecycleScope
import kotlinx.coroutines.launch

class TestingPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    override fun getTitle() = "Testing"

    override fun onClick() {
        lifecycleScope.launch {
            try {

                val tunnelManager = getTunnelManager();
                val tunnels = tunnelManager.getTunnels();

                Log.e("HI", "HI")
                Log.e("TEST", tunnels.map { it.getDaemonId()  }.toString())
            }
            catch (ex: Exception) {
                Log.e("err", "err", ex )
            }
        }
    }

    init {
        lifecycleScope.launch {
        }
    }
}
