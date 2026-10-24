package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class PriorityLevel(val label: String, val weight: Int) {
    URGENT("Urgent", 4),
    HIGH("High", 3),
    MEDIUM("Medium", 2),
    LOW("Low", 1)
}

enum class EmailTone(val displayName: String) {
    PROFESSIONAL("Professional"),
    CONCISE("Concise"),
    URGENT("Urgent"),
    FRIENDLY("Friendly"),
    FOLLOW_UP("Follow-up")
}

@Entity(tableName = "tasks")
data class TaskItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val priority: PriorityLevel = PriorityLevel.MEDIUM,
    val urgencyScore: Int = 50, // 0 to 100
    val urgencyReason: String = "",
    val deadlineEpochMillis: Long? = null,
    val isCompleted: Boolean = false,
    val completedAtEpochMillis: Long? = null,
    val category: String = "Work",
    val locationTag: String? = null,
    val estimatedMinutes: Int = 30,
    val actualMinutes: Int = 0,
    val isSyncedToCalendar: Boolean = false,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "schedule_events")
data class ScheduleEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val location: String = "Remote / Office",
    val category: String = "Meeting",
    val attendees: String = "",
    val isNativeCalendarExported: Boolean = false,
    val conflictDetected: Boolean = false
)

@Entity(tableName = "email_drafts")
data class EmailDraft(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipient: String = "",
    val cc: String = "",
    val subject: String,
    val body: String,
    val tone: EmailTone = EmailTone.PROFESSIONAL,
    val intentPrompt: String = "",
    val updatedAtEpochMillis: Long = System.currentTimeMillis(),
    val relatedTaskId: Long? = null
)

@Entity(tableName = "assistant_messages")
data class AssistantMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val isUser: Boolean,
    val text: String,
    val actionType: String? = null,
    val actionDetail: String? = null,
    val timestampEpochMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "completion_patterns")
data class CompletionPatternRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskCategory: String,
    val hourOfDay: Int, // 0..23
    val dayOfWeek: Int, // 1..7 (Calendar.SUNDAY..SATURDAY)
    val durationMinutes: Int,
    val locationContext: String,
    val completedAtEpochMillis: Long = System.currentTimeMillis()
)
