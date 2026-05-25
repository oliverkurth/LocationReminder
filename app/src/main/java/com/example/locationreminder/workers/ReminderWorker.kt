package com.example.locationreminder.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.locationreminder.data.AppDatabase
import com.example.locationreminder.notifications.NotificationHelper
import com.example.locationreminder.repository.AppRepository

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

        // Show notification
        notificationHelper.showReminderNotification(reminder)

        // Reset deferral state if it was a deferred reminder
        if (reminder.isDeferred) {
            repository.updateReminder(reminder.copy(isDeferred = false, deferredUntil = null))
        }

        return Result.success()
    }

    companion object {
        const val KEY_REMINDER_ID = "reminder_id"
    }
}
