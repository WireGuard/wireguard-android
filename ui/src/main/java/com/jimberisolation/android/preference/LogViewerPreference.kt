package com.jimberisolation.android.preference

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.util.Log
import android.widget.Toast
import androidx.preference.Preference
import com.jimberisolation.android.Application.Companion.getTunnelManager
import com.jimberisolation.android.activity.SignInActivity
import com.jimberisolation.android.storage.SharedStorage
import com.jimberisolation.android.util.lifecycleScope
import kotlinx.coroutines.launch
import logout

class LogViewerPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    override fun getTitle() = ""

    override fun onClick() {
        lifecycleScope.launch {
            try {
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
