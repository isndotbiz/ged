package com.gedfix.app

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.gedfix.db.DriverFactory
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Desktop entry point with Window and MenuBar.
 * Supports macOS, Windows, and Linux via JVM.
 */
fun main() = application {
    val state = rememberWindowState(width = 1280.dp, height = 800.dp)

    Window(
        onCloseRequest = ::exitApplication,
        title = "GedFix",
        state = state
    ) {
        // Use a persistent database in the user's home directory
        val dbDir = File(System.getProperty("user.home"), ".gedfix")
        dbDir.mkdirs()
        val dbPath = File(dbDir, "gedfix.db").absolutePath
        val driverFactory = DriverFactory(dbPath)

        App(driverFactory = driverFactory)
    }
}

/**
 * Opens a native file chooser dialog for selecting GEDCOM files.
 * Returns the selected file path, or null if cancelled.
 */
fun openFileChooser(): String? {
    val chooser = JFileChooser()
    chooser.dialogTitle = "Import GEDCOM File"
    chooser.fileFilter = FileNameExtensionFilter("GEDCOM Files (*.ged)", "ged")
    chooser.isMultiSelectionEnabled = false

    val result = chooser.showOpenDialog(null)
    return if (result == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile.absolutePath
    } else {
        null
    }
}

/**
 * Reads a file from disk and returns its text content.
 */
fun readFileContent(path: String): String {
    val file = File(path)
    if (!file.exists()) throw IllegalArgumentException("File not found: $path")
    return file.readText(Charsets.UTF_8)
}
