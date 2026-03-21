package com.gedfix.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gedfix.db.DatabaseRepository
import com.gedfix.models.*

/**
 * Main application ViewModel - manages sidebar selection, import state, and counts.
 */
class AppViewModel(val db: DatabaseRepository) {
    var selectedSection by mutableStateOf(SidebarSection.OVERVIEW)
    var selectedPersonXref by mutableStateOf<String?>(null)

    var personCount by mutableStateOf(0)
        private set
    var familyCount by mutableStateOf(0)
        private set
    var eventCount by mutableStateOf(0)
        private set
    var placeCount by mutableStateOf(0)
        private set
    var sourceCount by mutableStateOf(0)
        private set
    var mediaCount by mutableStateOf(0)
        private set

    var validatedCount by mutableStateOf(0)
        private set
    var unvalidatedCount by mutableStateOf(0)
        private set

    var issueCount by mutableStateOf(0)

    var isImporting by mutableStateOf(false)
        private set
    var importError by mutableStateOf<String?>(null)
        private set
    var showImportDialog by mutableStateOf(false)

    fun refreshCounts() {
        personCount = db.personCount()
        familyCount = db.familyCount()
        eventCount = db.eventCount()
        placeCount = db.placeCount()
        sourceCount = db.sourceCount()
        mediaCount = db.mediaCount()
        validatedCount = db.validatedPersonCount()
        unvalidatedCount = db.unvalidatedPersonCount()
    }

    val validationPercentage: Float
        get() = if (personCount > 0) validatedCount.toFloat() / personCount.toFloat() * 100f else 0f

    fun fetchUnvalidatedPersons(): List<GedcomPerson> = db.fetchUnvalidatedPersons()

    fun importGedcom(text: String) {
        isImporting = true
        importError = null
        try {
            val result = GedcomParser.parse(text)
            db.importParseResult(result)
            refreshCounts()
            selectedSection = SidebarSection.OVERVIEW
        } catch (e: Exception) {
            importError = e.message ?: "Unknown import error"
        } finally {
            isImporting = false
            showImportDialog = false
        }
    }

    fun fetchTopSurnames(limit: Int = 10): List<Pair<String, Int>> {
        return db.fetchTopSurnames(limit)
    }
}
