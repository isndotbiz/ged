package com.gedfix.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.gedfix.db.DatabaseRepository
import com.gedfix.db.DriverFactory
import com.gedfix.ui.MainScreen
import com.gedfix.ui.theme.GedFixTheme
import com.gedfix.viewmodel.AppViewModel
import com.gedfix.viewmodel.PersonViewModel

/**
 * Main application composable - creates the Material 3 themed app
 * with ViewModels and navigation.
 */
@Composable
fun App(driverFactory: DriverFactory) {
    val db = remember { DatabaseRepository(driverFactory) }
    val appViewModel = remember { AppViewModel(db) }
    val personViewModel = remember { PersonViewModel(db) }

    GedFixTheme {
        MainScreen(
            appViewModel = appViewModel,
            personViewModel = personViewModel
        )
    }
}
