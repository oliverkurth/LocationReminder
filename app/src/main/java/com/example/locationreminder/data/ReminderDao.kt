package com.example.locationreminder.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY displayOrder ASC, id ASC")
    fun getAllReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getReminderById(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE locationId IS NOT NULL AND isActive = 1")
    suspend fun getLocationBasedReminders(): List<ReminderEntity>

    @Transaction
    @Query("SELECT * FROM reminders WHERE locationId IS NOT NULL AND isActive = 1")
    suspend fun getLocationBasedRemindersWithLocation(): List<ReminderWithLocation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Update
    suspend fun updateReminders(reminders: List<ReminderEntity>)

    @Delete
    suspend fun deleteReminder(reminder: ReminderEntity)
}
