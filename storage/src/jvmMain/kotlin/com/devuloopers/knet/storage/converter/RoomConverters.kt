package com.devuloopers.knet.storage.converter

import androidx.room.TypeConverter

/**
 * Room Type Converters for [KNetDatabase].
 */
object RoomConverters {

    @TypeConverter
    @JvmStatic
    fun fromStringList(value: List<String>?): String {
        return value?.joinToString(";\n") ?: ""
    }

    @TypeConverter
    @JvmStatic
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(";\n")
    }
}
