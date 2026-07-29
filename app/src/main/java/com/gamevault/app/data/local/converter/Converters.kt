package com.gamevault.app.data.local.converter

import androidx.room.TypeConverter

/**
 * Type converters for Room. Currently not needed since we store enums as String,
 * but kept as extension point for future complex types.
 */
class Converters {

    @TypeConverter
    fun fromTimestamp(value: Long?): Long? = value

    @TypeConverter
    fun toTimestamp(value: Long?): Long? = value
}
