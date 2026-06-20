package com.example.locationreminder.workers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.locationreminder.data.AppDatabase
import com.example.locationreminder.repository.AppRepository
import com.example.locationreminder.service.LocationTrackingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Restart the foreground service
                LocationTrackingService.startService(context)

                // Re-arm alarms for all active reminders that have a time trigger
                val database = AppDatabase.getDatabase(context)
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

                val allReminders = database.reminderDao().getAllRemindersOnce()
                for (reminder in allReminders) {
                    if (!reminder.isActive) continue
                    val timeOfDay = reminder.timeOfDay ?: continue
                    val parts = timeOfDay.split(":")
                    if (parts.size != 2) continue
                    val hour = parts[0].toIntOrNull() ?: continue
                    val minute = parts[1].toIntOrNull() ?: continue

                    val calendar = Calendar.getInstance().apply {
                        if (reminder.specificDate != null) {
                            timeInMillis = reminder.specificDate
                        }
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    
                    // If specific date is null and today's time has already passed, schedule for tomorrow
                    if (reminder.specificDate == null && calendar.timeInMillis <= System.currentTimeMillis()) {
                        calendar.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    
                    // Advance to the next valid recurring day if applicable
                    if (reminder.isRecurring && reminder.recurringDays != null && reminder.recurringDays.isNotEmpty()) {
                        while (!reminder.recurringDays.contains(calendar.get(Calendar.DAY_OF_WEEK))) {
                            calendar.add(Calendar.DAY_OF_YEAR, 1)
                        }
                    }

                    val delay = calendar.timeInMillis - System.currentTimeMillis()
                    if (delay > 0) {
                        val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
                            putExtra(ReminderWorker.KEY_REMINDER_ID, reminder.id)
                        }
                        val pendingIntent = PendingIntent.getBroadcast(
                            context,
                            reminder.id.toInt(),
                            alarmIntent,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        )

                        try {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                if (alarmManager.canScheduleExactAlarms()) {
                                    alarmManager.setExactAndAllowWhileIdle(
                                        AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent
                                    )
                                }
                                // If exact alarms are not permitted, WorkManager alarms survive reboot
                                // via WorkManager's own boot receiver — no action needed.
                            } else {
                                alarmManager.setExactAndAllowWhileIdle(
                                    AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent
                                )
                            }
                        } catch (e: SecurityException) {
                            // Exact alarm permission revoked; WorkManager path handles it
                        }
                    }
                }
            } finally {
                result.finish()
            }
        }
    }
}
