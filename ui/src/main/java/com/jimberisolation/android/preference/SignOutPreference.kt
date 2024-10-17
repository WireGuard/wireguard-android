package com.jimberisolation.android.preference

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.widget.Toast
import androidx.preference.Preference
import com.jimberisolation.android.activity.SignInActivity
import com.jimberisolation.android.storage.SharedStorage
import com.jimberisolation.android.util.lifecycleScope
import kotlinx.coroutines.launch
import logout

class SignOutPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    override fun getTitle() = "Sign out"

    override fun onClick() {
        lifecycleScope.launch {
            try {
                logout()

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
