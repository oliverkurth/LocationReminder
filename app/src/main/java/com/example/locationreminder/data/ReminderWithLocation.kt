package com.example.locationreminder.data

import androidx.room.Embedded
import androidx.room.Relation

data class ReminderWithLocation(
    @Embedded val reminder: ReminderEntity,
    @Relation(
        parentColumn = "locationId",
        entityColumn = "id"
    )
    val location: LocationEntity?
)
