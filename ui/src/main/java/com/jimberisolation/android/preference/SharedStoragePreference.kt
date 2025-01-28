package com.jimberisolation.android.preference

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.AttributeSet
import android.widget.Toast
import androidx.core.content.ContextCompat.getSystemService
import androidx.preference.Preference
import com.google.gson.Gson
import com.jimberisolation.android.storage.SharedStorage
import com.jimberisolation.android.util.activity
import com.jimberisolation.android.util.lifecycleScope
import kotlinx.coroutines.launch
class SharedStoragePreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    override fun getTitle() = "Copy Storage"

    override fun onClick() {
        lifecycleScope.launch {
            try {
                val data = SharedStorage.getInstance().getAll();

                // Convert the map to JSON
                val jsonData = Gson().toJson(data)
                val clip = ClipData.newPlainText("Data",jsonData)

                val clipboard = getSystemService(context, ClipboardManager::class.java)
                clipboard!!.setPrimaryClip(clip)

                Toast.makeText(activity , "Data set to clipboard", Toast.LENGTH_SHORT).show()
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
