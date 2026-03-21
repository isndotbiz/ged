package com.gedfix.app

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.gedfix.db.DatabaseRepository
import com.gedfix.db.DriverFactory
import com.gedfix.models.GedcomExporter
import java.io.File
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Desktop entry point with Window and MenuBar.
 * Supports macOS, Windows, and Linux via JVM.
 */
fun main() = application {
    val state = rememberWindowState(width = 1280.dp, height = 800.dp)

    // Use a persistent database in the user's home directory
    val dbDir = File(System.getProperty("user.home"), ".gedfix")
    dbDir.mkdirs()
    val dbPath = File(dbDir, "gedfix.db").absolutePath
    val driverFactory = remember { DriverFactory(dbPath) }
    val db = remember { DatabaseRepository(driverFactory) }

    Window(
        onCloseRequest = ::exitApplication,
        title = "GedFix",
        state = state
    ) {
        MenuBar {
            Menu("File") {
                Item("Import GEDCOM...") {
                    val path = openFileChooser()
                    if (path != null) {
                        // Import is handled through the App composable's import dialog
                        // For now, this opens the file chooser but import is done via UI
                    }
                }
                Separator()
                Item("Export GEDCOM...") {
                    exportGedcom(db, filterLiving = false)
                }
                Item("Export GEDCOM (Privacy Filtered)...") {
                    exportGedcom(db, filterLiving = true)
                }
            }
        }

        App(driverFactory = driverFactory, existingDb = db)
    }
}

/**
 * Exports GEDCOM data to a file chosen by the user.
 */
private fun exportGedcom(db: DatabaseRepository, filterLiving: Boolean) {
    val path = saveFileChooser() ?: return

    try {
        val exporter = GedcomExporter(db)
        exporter.filterLiving = filterLiving
        val content = exporter.export()

        val file = File(path)
        file.writeText(content, Charsets.UTF_8)

        JOptionPane.showMessageDialog(
            null,
            "GEDCOM exported successfully to:\n$path",
            "Export Complete",
            JOptionPane.INFORMATION_MESSAGE
        )
    } catch (e: Exception) {
        JOptionPane.showMessageDialog(
            null,
            "Export failed: ${e.message}",
            "Export Error",
            JOptionPane.ERROR_MESSAGE
        )
    }
}

/**
 * Opens a native file chooser dialog for selecting GEDCOM files to import.
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
 * Opens a native file chooser dialog for saving GEDCOM files.
 * Returns the selected file path, or null if cancelled.
 */
fun saveFileChooser(): String? {
    val chooser = JFileChooser()
    chooser.dialogTitle = "Export GEDCOM File"
    chooser.fileFilter = FileNameExtensionFilter("GEDCOM Files (*.ged)", "ged")
    chooser.isMultiSelectionEnabled = false

    val result = chooser.showSaveDialog(null)
    return if (result == JFileChooser.APPROVE_OPTION) {
        var path = chooser.selectedFile.absolutePath
        if (!path.lowercase().endsWith(".ged")) {
            path += ".ged"
        }
        path
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
