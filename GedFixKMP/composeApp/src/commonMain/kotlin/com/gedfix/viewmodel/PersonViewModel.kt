package com.gedfix.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gedfix.db.DatabaseRepository
import com.gedfix.models.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Person list/detail ViewModel with search, sort, CRUD operations.
 */
@OptIn(ExperimentalUuidApi::class)
class PersonViewModel(private val db: DatabaseRepository) {
    var searchText by mutableStateOf("")
    var sortBy by mutableStateOf(PersonSort.SURNAME)
    var selectedPersonId by mutableStateOf<String?>(null)
    var showNewPersonDialog by mutableStateOf(false)
    var showEditPersonDialog by mutableStateOf(false)
    var showDeleteConfirm by mutableStateOf(false)
    var showAddEventDialog by mutableStateOf(false)
    var editingEvent by mutableStateOf<GedcomEvent?>(null)
    var refreshToken by mutableStateOf(0)

    val persons: List<GedcomPerson>
        get() = db.fetchPersons(search = searchText, sortBy = sortBy)

    val selectedPerson: GedcomPerson?
        get() = selectedPersonId?.let { id -> persons.firstOrNull { it.id == id } }

    fun fetchEvents(xref: String): List<GedcomEvent> = db.fetchEvents(xref)
    fun fetchPerson(xref: String): GedcomPerson? = db.fetchPerson(xref)
    fun fetchFamiliesAsSpouse(xref: String): List<GedcomFamily> = db.fetchFamiliesAsSpouse(xref)
    fun fetchFamiliesAsChild(xref: String): List<GedcomFamily> = db.fetchFamiliesAsChild(xref)
    fun fetchChildLinks(familyXref: String): List<GedcomChildLink> = db.fetchChildLinks(familyXref)
    fun fetchMediaForOwner(xref: String): List<GedcomMedia> = db.fetchMediaForOwner(xref)

    fun createPerson(givenName: String, surname: String, suffix: String, sex: String, isLiving: Boolean) {
        val xref = db.nextPersonXref()
        val person = GedcomPerson(
            id = Uuid.random().toString(),
            xref = xref,
            givenName = givenName.trim(),
            surname = surname.trim(),
            suffix = suffix.trim(),
            sex = sex,
            isLiving = isLiving
        )
        db.insertPerson(person)
        selectedPersonId = person.id
        showNewPersonDialog = false
        refreshToken++
    }

    fun updatePerson(person: GedcomPerson) {
        db.updatePerson(person)
        showEditPersonDialog = false
        refreshToken++
    }

    fun deletePerson(xref: String) {
        db.deletePerson(xref)
        selectedPersonId = null
        showDeleteConfirm = false
        refreshToken++
    }

    fun createEvent(ownerXref: String, ownerType: String, eventType: String, dateValue: String, place: String, description: String) {
        val event = GedcomEvent(
            id = Uuid.random().toString(),
            ownerXref = ownerXref,
            ownerType = ownerType,
            eventType = eventType,
            dateValue = dateValue.trim(),
            place = place.trim(),
            description = description.trim()
        )
        db.insertEvent(event)
        showAddEventDialog = false
        refreshToken++
    }

    fun updateEvent(event: GedcomEvent) {
        db.updateEvent(event)
        editingEvent = null
        refreshToken++
    }

    fun deleteEvent(id: String) {
        db.deleteEvent(id)
        editingEvent = null
        refreshToken++
    }
}
