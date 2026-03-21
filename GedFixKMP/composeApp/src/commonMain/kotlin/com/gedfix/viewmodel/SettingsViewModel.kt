package com.gedfix.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gedfix.db.DatabaseRepository
import com.gedfix.models.DateDisplayFormat
import com.gedfix.models.NameDisplayFormat

/**
 * ViewModel for application settings, persisted in the SQLDelight settings table.
 * Each setting is stored as a key-value pair.
 */
class SettingsViewModel(private val db: DatabaseRepository) {
    var livingThreshold by mutableIntStateOf(110)
    var filterLivingDefault by mutableStateOf(false)
    var showLivingDetails by mutableStateOf(true)
    var autoSave by mutableStateOf(true)
    var dateFormat by mutableStateOf(DateDisplayFormat.RAW)
    var nameFormat by mutableStateOf(NameDisplayFormat.GIVEN_SURNAME)
    var showXrefIds by mutableStateOf(false)

    fun load() {
        val settings = db.getAllSettings()

        livingThreshold = settings["livingThreshold"]?.toIntOrNull() ?: 110
        filterLivingDefault = settings["filterLivingDefault"]?.toBooleanStrictOrNull() ?: false
        showLivingDetails = settings["showLivingDetails"]?.toBooleanStrictOrNull() ?: true
        autoSave = settings["autoSave"]?.toBooleanStrictOrNull() ?: true

        dateFormat = settings["dateFormat"]?.let { name ->
            DateDisplayFormat.entries.firstOrNull { it.name == name }
        } ?: DateDisplayFormat.RAW

        nameFormat = settings["nameFormat"]?.let { name ->
            NameDisplayFormat.entries.firstOrNull { it.name == name }
        } ?: NameDisplayFormat.GIVEN_SURNAME

        showXrefIds = settings["showXrefIds"]?.toBooleanStrictOrNull() ?: false
    }

    fun save() {
        db.setSetting("livingThreshold", livingThreshold.toString())
        db.setSetting("filterLivingDefault", filterLivingDefault.toString())
        db.setSetting("showLivingDetails", showLivingDetails.toString())
        db.setSetting("autoSave", autoSave.toString())
        db.setSetting("dateFormat", dateFormat.name)
        db.setSetting("nameFormat", nameFormat.name)
        db.setSetting("showXrefIds", showXrefIds.toString())
    }

    companion object {
        const val KEY_LIVING_THRESHOLD = "livingThreshold"
        const val KEY_FILTER_LIVING_DEFAULT = "filterLivingDefault"
        const val KEY_SHOW_LIVING_DETAILS = "showLivingDetails"
        const val KEY_AUTO_SAVE = "autoSave"
        const val KEY_DATE_FORMAT = "dateFormat"
        const val KEY_NAME_FORMAT = "nameFormat"
        const val KEY_SHOW_XREF_IDS = "showXrefIds"
    }
}
