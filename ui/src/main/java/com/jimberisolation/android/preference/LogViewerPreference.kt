package com.jimberisolation.android.preference

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import com.jimberisolation.android.util.lifecycleScope
import kotlinx.coroutines.launch

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
