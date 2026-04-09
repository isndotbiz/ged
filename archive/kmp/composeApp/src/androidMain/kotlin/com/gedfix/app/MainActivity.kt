package com.gedfix.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.gedfix.db.DriverFactory

/**
 * Android Activity that hosts the Compose Multiplatform UI.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            App(driverFactory = DriverFactory(applicationContext))
        }
    }
}
