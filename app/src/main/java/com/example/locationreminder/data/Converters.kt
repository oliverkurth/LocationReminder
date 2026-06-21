package com.example.locationreminder.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    companion object {
        private val gson = Gson()
        private val listType = object : TypeToken<List<Int>>() {}.type
    }

    @TypeConverter
    fun fromIntegerList(value: List<Int>?): String? {
        return gson.toJson(value, listType)
    }

    @TypeConverter
    fun toIntegerList(value: String?): List<Int>? {
        if (value == null) {
            return null
        }
        return gson.fromJson(value, listType)
    }
}
