package com.gedfix.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

/**
 * iOS/Native SQLite driver factory.
 * Uses SQLDelight's NativeSqliteDriver for iOS platforms.
 */
actual class DriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(
            schema = GedFixDatabase.Schema,
            name = "gedfix.db"
        )
    }
}
