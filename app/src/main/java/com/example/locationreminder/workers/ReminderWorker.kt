package com.example.locationreminder.workers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.locationreminder.data.AppDatabase
import com.example.locationreminder.notifications.NotificationHelper
import com.example.locationreminder.repository.AppRepository
import com.google.android.gms.location.LocationServices
import com.example.locationreminder.workers.AlarmScheduler
import kotlinx.coroutines.tasks.await

class ReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val reminderId = inputData.getLong(KEY_REMINDER_ID, -1)
        if (reminderId == -1L) return Result.failure()

        val database = AppDatabase.getDatabase(applicationContext)
        val repository = AppRepository(database.locationDao(), database.reminderDao())
        val notificationHelper = NotificationHelper(applicationContext)

        val reminder = repository.getReminderById(reminderId) ?: return Result.failure()
        if (!reminder.isActive) return Result.success()

        var shouldTrigger = true

        // If the reminder has a location trigger, verify we are at the location
        if (reminder.locationId != null) {
            shouldTrigger = false // Assume false until proven true
            val locationEntity = repository.getLocationById(reminder.locationId)
            if (locationEntity != null) {
                if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
                    try {
                        var currentLocation = fusedLocationClient.lastLocation.await()
                        if (currentLocation == null) {
                            currentLocation = com.example.locationreminder.service.LocationTrackingService.lastKnownLocation
                        }
                        if (currentLocation == null) {
                            val prefs = applicationContext.getSharedPreferences("location_prefs", Context.MODE_PRIVATE)
                            if (prefs.contains("last_lat") && prefs.contains("last_lon")) {
                                currentLocation = Location("").apply {
                                    latitude = prefs.getFloat("last_lat", 0f).toDouble()
                                    longitude = prefs.getFloat("last_lon", 0f).toDouble()
                                }
                            }
                        }

                        if (currentLocation != null) {
                            val targetLocation = Location("").apply {
                                latitude = locationEntity.latitude
                                longitude = locationEntity.longitude
                            }
                            val distance = currentLocation.distanceTo(targetLocation)
                            if (distance <= locationEntity.radius) {
                                shouldTrigger = true
                            }
                        }
                    } catch (e: Exception) {
                        // Keep shouldTrigger = false
                    }
                }
            }
        }

        if (shouldTrigger) {
            // Show notification
            notificationHelper.showReminderNotification(reminder)

            // Set a cooldown so LocationTrackingService doesn't immediately re-trigger it
            repository.updateReminder(reminder.copy(
                isDeferred = true,
                deferredUntil = System.currentTimeMillis() + 60 * 60 * 1000, // 1 hour cooldown
                lastTriggeredTimestamp = System.currentTimeMillis()
            ))
        }

        // If it's recurring, schedule the next one for tomorrow (or next valid day)
        if (reminder.isRecurring && reminder.timeOfDay != null) {
            // We need to schedule the next occurrence. AlarmScheduler.scheduleAlarm will schedule it for the next valid day
            // because the current time has already passed the alarm time for today.
            AlarmScheduler.scheduleAlarm(applicationContext, reminder)
        }

        return Result.success()
    }

    companion object {
        const val KEY_REMINDER_ID = "reminder_id"
    }
}
