package com.example.locationreminder.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(ReminderWorker.KEY_REMINDER_ID, -1)
        if (reminderId != -1L) {
            val workData = Data.Builder()
                .putLong(ReminderWorker.KEY_REMINDER_ID, reminderId)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInputData(workData)
                .addTag("reminder_$reminderId")
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
