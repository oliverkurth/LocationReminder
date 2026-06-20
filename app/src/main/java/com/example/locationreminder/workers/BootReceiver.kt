package com.example.locationreminder.workers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.locationreminder.data.AppDatabase
import com.example.locationreminder.repository.AppRepository
import com.example.locationreminder.service.LocationTrackingService
import com.example.locationreminder.workers.AlarmScheduler
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
                    if (reminder.isActive && reminder.timeOfDay != null) {
                        AlarmScheduler.scheduleAlarm(context, reminder)
                    }
                }
            } finally {
                result.finish()
            }
        }
    }
}
