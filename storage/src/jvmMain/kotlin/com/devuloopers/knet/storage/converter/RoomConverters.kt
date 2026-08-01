package com.devuloopers.knet.storage.converter

import androidx.room.TypeConverter

/**
 * Room Type Converters for [KNetDatabase].
 */
public object RoomConverters {

    @TypeConverter
    @JvmStatic
    public fun fromStringList(value: List<String>?): String {
        return value?.joinToString(";\n") ?: ""
    }

    @TypeConverter
    @JvmStatic
    public fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(";\n")
    }
}
