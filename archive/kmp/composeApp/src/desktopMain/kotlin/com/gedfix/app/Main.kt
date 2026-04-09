package com.gedfix.app

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.gedfix.db.DatabaseRepository
import com.gedfix.db.DriverFactory
import com.gedfix.models.GedcomExporter
import com.gedfix.models.GedcomParser
import java.io.File
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter

fun main() = application {
    val state = rememberWindowState(width = 1280.dp, height = 800.dp)

    val dbDir = File(System.getProperty("user.home"), ".gedfix")
    dbDir.mkdirs()
    val dbPath = File(dbDir, "gedfix.db").absolutePath
    val driverFactory = remember { DriverFactory(dbPath) }
    val db = remember { DatabaseRepository(driverFactory) }

    // Auto-import on first launch if database is empty and default file exists
    var hasAutoImported by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!hasAutoImported && db.personCount() == 0) {
            val defaultFile = File(System.getProperty("user.home"), "Documents/GedFix/mallinger_cleaned.ged")
            if (defaultFile.exists()) {
                try {
                    println("Auto-importing: ${defaultFile.absolutePath}")
                    val text = defaultFile.readText(Charsets.UTF_8)
                    val result = GedcomParser.parse(text)
                    db.importParseResult(result)
                    println("Imported ${result.persons.size} persons, ${result.families.size} families, ${result.media.size} media")
                } catch (e: Exception) {
                    println("Auto-import failed: ${e.message}")
                }
            }
            hasAutoImported = true
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "GedFix — Mallinger Family Tree",
        state = state
    ) {
        MenuBar {
            Menu("File") {
                Item("Import GEDCOM...") {
                    val path = openFileChooser()
                    if (path != null) {
                        importGedcom(path, db)
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

private fun importGedcom(path: String, db: DatabaseRepository) {
    try {
        val file = File(path)
        if (!file.exists()) {
            JOptionPane.showMessageDialog(null, "File not found: $path", "Import Error", JOptionPane.ERROR_MESSAGE)
            return
        }

        println("Importing: $path")
        val text = file.readText(Charsets.UTF_8)
        val result = GedcomParser.parse(text)
        db.importParseResult(result)

        JOptionPane.showMessageDialog(
            null,
            "Imported successfully!\n\n" +
                "${result.persons.size} people\n" +
                "${result.families.size} families\n" +
                "${result.events.size} events\n" +
                "${result.media.size} media files\n" +
                "${result.sources.size} sources",
            "Import Complete",
            JOptionPane.INFORMATION_MESSAGE
        )
    } catch (e: Exception) {
        JOptionPane.showMessageDialog(null, "Import failed: ${e.message}", "Import Error", JOptionPane.ERROR_MESSAGE)
        e.printStackTrace()
    }
}

private fun exportGedcom(db: DatabaseRepository, filterLiving: Boolean) {
    val path = saveFileChooser() ?: return
    try {
        val exporter = GedcomExporter(db)
        exporter.filterLiving = filterLiving
        val content = exporter.export()
        File(path).writeText(content, Charsets.UTF_8)
        JOptionPane.showMessageDialog(null, "Exported to:\n$path", "Export Complete", JOptionPane.INFORMATION_MESSAGE)
    } catch (e: Exception) {
        JOptionPane.showMessageDialog(null, "Export failed: ${e.message}", "Export Error", JOptionPane.ERROR_MESSAGE)
    }
}

fun openFileChooser(): String? {
    val chooser = JFileChooser()
    chooser.dialogTitle = "Import GEDCOM File"
    chooser.fileFilter = FileNameExtensionFilter("GEDCOM Files (*.ged)", "ged")
    // Start in Documents/GedFix if it exists
    val gedFixDir = File(System.getProperty("user.home"), "Documents/GedFix")
    if (gedFixDir.exists()) chooser.currentDirectory = gedFixDir
    val result = chooser.showOpenDialog(null)
    return if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile.absolutePath else null
}

fun saveFileChooser(): String? {
    val chooser = JFileChooser()
    chooser.dialogTitle = "Export GEDCOM File"
    chooser.fileFilter = FileNameExtensionFilter("GEDCOM Files (*.ged)", "ged")
    val result = chooser.showSaveDialog(null)
    return if (result == JFileChooser.APPROVE_OPTION) {
        var path = chooser.selectedFile.absolutePath
        if (!path.lowercase().endsWith(".ged")) path += ".ged"
        path
    } else null
}
