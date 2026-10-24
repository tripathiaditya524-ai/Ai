package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.model.AssistantMessage
import com.example.data.model.CompletionPatternRecord
import com.example.data.model.EmailDraft
import com.example.data.model.ScheduleEvent
import com.example.data.model.TaskItem

@Database(
    entities = [
        TaskItem::class,
        ScheduleEvent::class,
        EmailDraft::class,
        AssistantMessage::class,
        CompletionPatternRecord::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AuraDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun emailDraftDao(): EmailDraftDao
    abstract fun assistantDao(): AssistantDao
    abstract fun patternDao(): PatternDao

    companion object {
        @Volatile
        private var INSTANCE: AuraDatabase? = null

        fun getDatabase(context: Context): AuraDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AuraDatabase::class.java,
                    "aura_assistant_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
