package com.example.locationreminder.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.locationreminder.data.AppDatabase
import com.example.locationreminder.repository.AppRepository
import com.example.locationreminder.workers.ReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

import com.example.locationreminder.workers.AlarmScheduler

class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1)
        if (reminderId == -1L) return

        val pendingResult = goAsync()

        val notificationHelper = NotificationHelper(context)
        notificationHelper.cancelNotification(reminderId)

        val database = AppDatabase.getDatabase(context)
        val repository = AppRepository(database.locationDao(), database.reminderDao())

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminder = repository.getReminderById(reminderId) ?: return@launch

                when (intent.action) {
                    ACTION_DEFER -> {
                        // Defer for 10 minutes
                        val deferredTime = System.currentTimeMillis() + 10 * 60 * 1000
                        repository.updateReminder(
                            reminder.copy(
                                isDeferred = true,
                                deferredUntil = deferredTime
                            )
                        )

                        AlarmScheduler.scheduleDeferAlarm(context, reminderId)
                    }
                    ACTION_DISMISS -> {
                        // Just dismiss, reset deferred state if needed
                        val updatedReminder = if (!reminder.isRecurring) {
                            reminder.copy(isDeferred = false, deferredUntil = null, isActive = false)
                        } else {
                            reminder.copy(isDeferred = false, deferredUntil = null)
                        }
                        repository.updateReminder(updatedReminder)
                    }
                    ACTION_CLICK -> {
                        if (!reminder.isRecurring) {
                            repository.updateReminder(reminder.copy(isDeferred = false, deferredUntil = null, isActive = false))
                        }
                        val mainIntent = Intent(context, com.example.locationreminder.MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        context.startActivity(mainIntent)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_DEFER = "com.example.locationreminder.ACTION_DEFER"
        const val ACTION_DISMISS = "com.example.locationreminder.ACTION_DISMISS"
        const val ACTION_CLICK = "com.example.locationreminder.ACTION_CLICK"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
    }
}
