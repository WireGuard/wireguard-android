package com.jimberisolation.android.preference

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import androidx.preference.Preference
import com.jimberisolation.android.storage.SharedStorage
import com.jimberisolation.android.util.decryptData
import com.jimberisolation.android.util.lifecycleScope
import getCloudControllerPublicKeyV3
import kotlinx.coroutines.launch

class TestingPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    override fun getTitle() = "Testing"

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun onClick() {
        lifecycleScope.launch {
            try {
                Log.e("err", "lennert")


                val userId = SharedStorage.getInstance().getCurrentUserId();
                val kp = SharedStorage.getInstance().getWireguardKeyPairOfUserId(userId);

                val q = getCloudControllerPublicKeyV3("jimber", kp!!.daemonId);
                val data = q.getOrNull();

                val decrypted = decryptData(data!!.ncInformation, kp.skCurveX25519);
                Log.e("err", decrypted!!)
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
