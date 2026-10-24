package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.EmailTone
import com.example.data.model.PriorityLevel

class Converters {
    @TypeConverter
    fun fromPriority(value: PriorityLevel?): String {
        return value?.name ?: PriorityLevel.MEDIUM.name
    }

    @TypeConverter
    fun toPriority(value: String?): PriorityLevel {
        return try {
            if (value != null) PriorityLevel.valueOf(value) else PriorityLevel.MEDIUM
        } catch (e: Exception) {
            PriorityLevel.MEDIUM
        }
    }

    @TypeConverter
    fun fromTone(value: EmailTone?): String {
        return value?.name ?: EmailTone.PROFESSIONAL.name
    }

    @TypeConverter
    fun toTone(value: String?): EmailTone {
        return try {
            if (value != null) EmailTone.valueOf(value) else EmailTone.PROFESSIONAL
        } catch (e: Exception) {
            EmailTone.PROFESSIONAL
        }
    }
}
