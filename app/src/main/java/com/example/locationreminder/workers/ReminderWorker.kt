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

        // If the reminder has a location trigger, verify we are at the location
        if (reminder.locationId != null) {
            val locationEntity = repository.getLocationById(reminder.locationId)
            if (locationEntity != null) {
                if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
                    try {
                        val currentLocation = fusedLocationClient.lastLocation.await()
                        if (currentLocation != null) {
                            val targetLocation = Location("").apply {
                                latitude = locationEntity.latitude
                                longitude = locationEntity.longitude
                            }
                            val distance = currentLocation.distanceTo(targetLocation)
                            if (distance > locationEntity.radius) {
                                // We are not at the location, so don't trigger
                                return Result.success()
                            }
                        } else {
                            // Could not get location, maybe skip or assume not there
                            return Result.success()
                        }
                    } catch (e: Exception) {
                        return Result.success()
                    }
                } else {
                    // No permission, can't check location
                    return Result.success()
                }
            }
        }

        // Show notification
        notificationHelper.showReminderNotification(reminder)

        // Set a cooldown so LocationTrackingService doesn't immediately re-trigger it
        repository.updateReminder(reminder.copy(
            isDeferred = true,
            deferredUntil = System.currentTimeMillis() + 60 * 60 * 1000, // 1 hour cooldown
            lastTriggeredTimestamp = System.currentTimeMillis()
        ))

        // If it's recurring, schedule the next one for tomorrow (or next valid day)
        if (reminder.isRecurring && reminder.timeOfDay != null) {
            val parts = reminder.timeOfDay.split(":")
            if (parts.size == 2) {
                val hour = parts[0].toIntOrNull() ?: 0
                val minute = parts[1].toIntOrNull() ?: 0
                val calendar = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, hour)
                    set(java.util.Calendar.MINUTE, minute)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                    add(java.util.Calendar.DAY_OF_YEAR, 1) // Tomorrow
                }

                if (reminder.recurringDays != null && reminder.recurringDays.isNotEmpty()) {
                    while (!reminder.recurringDays.contains(calendar.get(java.util.Calendar.DAY_OF_WEEK))) {
                        calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
                    }
                }

                val delay = calendar.timeInMillis - System.currentTimeMillis()
                if (delay > 0) {
                    val alarmManager = applicationContext.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                    val intent = android.content.Intent(applicationContext, com.example.locationreminder.workers.AlarmReceiver::class.java).apply {
                        putExtra(KEY_REMINDER_ID, reminderId)
                    }
                    val pendingIntent = android.app.PendingIntent.getBroadcast(
                        applicationContext,
                        reminderId.toInt(),
                        intent,
                        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                    )

                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            if (alarmManager.canScheduleExactAlarms()) {
                                alarmManager.setExactAndAllowWhileIdle(
                                    android.app.AlarmManager.RTC_WAKEUP,
                                    calendar.timeInMillis,
                                    pendingIntent
                                )
                            } else {
                                // Fallback
                                val workData = androidx.work.Data.Builder()
                                    .putLong(KEY_REMINDER_ID, reminderId)
                                    .build()

                                val workRequest = androidx.work.OneTimeWorkRequestBuilder<ReminderWorker>()
                                    .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
                                    .setInputData(workData)
                                    .addTag("reminder_$reminderId")
                                    .build()

                                androidx.work.WorkManager.getInstance(applicationContext).enqueue(workRequest)
                            }
                        } else {
                            alarmManager.setExactAndAllowWhileIdle(
                                android.app.AlarmManager.RTC_WAKEUP,
                                calendar.timeInMillis,
                                pendingIntent
                            )
                        }
                    } catch (e: SecurityException) {
                        // Fallback
                        val workData = androidx.work.Data.Builder()
                            .putLong(KEY_REMINDER_ID, reminderId)
                            .build()

                        val workRequest = androidx.work.OneTimeWorkRequestBuilder<ReminderWorker>()
                            .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
                            .setInputData(workData)
                            .addTag("reminder_$reminderId")
                            .build()

                        androidx.work.WorkManager.getInstance(applicationContext).enqueue(workRequest)
                    }
                }
            }
        }

        return Result.success()
    }

    companion object {
        const val KEY_REMINDER_ID = "reminder_id"
    }
}
