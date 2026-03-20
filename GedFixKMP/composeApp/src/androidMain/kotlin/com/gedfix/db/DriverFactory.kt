package com.gedfix.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/**
 * Android SQLite driver factory.
 * Uses Android's native SQLite through the AndroidSqliteDriver.
 */
actual class DriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(
            schema = GedFixDatabase.Schema,
            context = context,
            name = "gedfix.db"
        )
    }
}
