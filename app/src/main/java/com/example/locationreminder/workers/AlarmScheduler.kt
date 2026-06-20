package com.example.locationreminder.workers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.locationreminder.data.ReminderEntity
import java.util.Calendar
import java.util.concurrent.TimeUnit

object AlarmScheduler {

    fun scheduleAlarm(context: Context, reminder: ReminderEntity) {
        if (reminder.timeOfDay == null) return
        
        val parts = reminder.timeOfDay.split(":")
        if (parts.size != 2) return
        val hour = parts[0].toIntOrNull() ?: return
        val minute = parts[1].toIntOrNull() ?: return

        val calendar = Calendar.getInstance().apply {
            if (reminder.specificDate != null) {
                timeInMillis = reminder.specificDate
            }
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (reminder.specificDate == null && calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        if (reminder.isRecurring && reminder.recurringDays != null && reminder.recurringDays.isNotEmpty()) {
            while (!reminder.recurringDays.contains(calendar.get(Calendar.DAY_OF_WEEK))) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val delay = calendar.timeInMillis - System.currentTimeMillis()
        if (delay > 0) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra(ReminderWorker.KEY_REMINDER_ID, reminder.id)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reminder.id.toInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            calendar.timeInMillis,
                            pendingIntent
                        )
                    } else {
                        scheduleWorkManagerFallback(context, reminder.id, delay)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } catch (e: SecurityException) {
                scheduleWorkManagerFallback(context, reminder.id, delay)
            }
        }
    }

    private fun scheduleWorkManagerFallback(context: Context, reminderId: Long, delayMillis: Long) {
        val workData = Data.Builder()
            .putLong(ReminderWorker.KEY_REMINDER_ID, reminderId)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(workData)
            .addTag("reminder_$reminderId")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "reminder_$reminderId",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun cancelAlarm(context: Context, reminderId: Long) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        if (pendingIntent != null) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        WorkManager.getInstance(context).cancelAllWorkByTag("reminder_$reminderId")
    }
}
