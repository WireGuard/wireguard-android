package com.jimberisolation.android.preference

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import androidx.preference.Preference
import com.jimberisolation.android.networkcontroller.getNetworkControllerPublicKey
import com.jimberisolation.android.storage.SharedStorage
import com.jimberisolation.android.util.lifecycleScope
import kotlinx.coroutines.launch

class TestingPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    override fun getTitle() = "Testing"

    override fun onClick() {
        lifecycleScope.launch {
            try {
                val userId = SharedStorage.getInstance().getCurrentUserId();
                val kp = SharedStorage.getInstance().getDaemonKeyPairByUserId(userId);

                val q = getNetworkControllerPublicKey(kp!!.daemonId, kp.companyName);
                val data = q.getOrNull();
                Log.e("err", data.toString())
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
