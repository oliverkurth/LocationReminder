package com.example.locationreminder.repository

import com.example.locationreminder.data.LocationDao
import com.example.locationreminder.data.LocationEntity
import com.example.locationreminder.data.ReminderDao
import com.example.locationreminder.data.ReminderEntity
import kotlinx.coroutines.flow.Flow

class AppRepository(
    private val locationDao: LocationDao,
    private val reminderDao: ReminderDao
) {
    // Locations
    val allLocations: Flow<List<LocationEntity>> = locationDao.getAllLocations()

    suspend fun getLocationById(id: Long): LocationEntity? {
        return locationDao.getLocationById(id)
    }

    suspend fun insertLocation(location: LocationEntity): Long {
        return locationDao.insertLocation(location)
    }

    suspend fun updateLocation(location: LocationEntity) {
        locationDao.updateLocation(location)
    }

    suspend fun deleteLocation(location: LocationEntity) {
        locationDao.deleteLocation(location)
    }

    // Reminders
    val allReminders: Flow<List<ReminderEntity>> = reminderDao.getAllReminders()

    suspend fun getReminderById(id: Long): ReminderEntity? {
        return reminderDao.getReminderById(id)
    }

    suspend fun getLocationBasedReminders(): List<ReminderEntity> {
        return reminderDao.getLocationBasedReminders()
    }

    suspend fun insertReminder(reminder: ReminderEntity): Long {
        return reminderDao.insertReminder(reminder)
    }

    suspend fun updateReminder(reminder: ReminderEntity) {
        reminderDao.updateReminder(reminder)
    }

    suspend fun deleteReminder(reminder: ReminderEntity) {
        reminderDao.deleteReminder(reminder)
    }
}
