package com.gedfix.app

import androidx.compose.ui.window.ComposeUIViewController
import com.gedfix.db.DriverFactory

/**
 * iOS entry point - creates a UIViewController hosting the Compose UI.
 * This is called from the iOS app's SwiftUI or UIKit integration layer.
 */
fun MainViewController() = ComposeUIViewController {
    App(driverFactory = DriverFactory())
}
