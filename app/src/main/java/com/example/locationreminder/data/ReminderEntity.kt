package com.example.locationreminder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val locationId: Long? = null,
    val timeOfDay: String? = null, // e.g., "08:00"
    val recurringDays: List<Int>? = null, // e.g., [Calendar.MONDAY, Calendar.WEDNESDAY]
    val specificDate: Long? = null, // timestamp
    val isDeferred: Boolean = false,
    val deferredUntil: Long? = null, // timestamp
    val isRecurring: Boolean = false,
    val isActive: Boolean = true
)
