package com.gedfix.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

/**
 * JVM/Desktop SQLite driver factory.
 * Creates an in-memory database by default.
 * For persistent storage, pass a file path to the constructor.
 */
actual class DriverFactory(private val dbPath: String? = null) {
    actual fun createDriver(): SqlDriver {
        val url = if (dbPath != null) "jdbc:sqlite:$dbPath" else JdbcSqliteDriver.IN_MEMORY
        val driver = JdbcSqliteDriver(url)
        GedFixDatabase.Schema.create(driver)
        return driver
    }
}
