package com.jimberisolation.android.preference

import android.content.Context
import android.util.AttributeSet
import android.widget.Toast
import androidx.preference.Preference
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
