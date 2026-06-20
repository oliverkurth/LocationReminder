package com.example.locationreminder.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.locationreminder.data.AppDatabase

class AlarmSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val allReminders = database.reminderDao().getAllRemindersOnce()
        
        for (reminder in allReminders) {
            if (reminder.isActive && reminder.timeOfDay != null) {
                AlarmScheduler.scheduleAlarm(applicationContext, reminder)
            }
        }
        
        return Result.success()
    }
}
