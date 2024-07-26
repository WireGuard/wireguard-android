package com.jimberisolation.android.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.jimberisolation.android.R

class AddConnectionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.add_connection_activity) // make sure you have a corresponding layout file
    }
}