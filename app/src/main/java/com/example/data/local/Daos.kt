package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AssistantMessage
import com.example.data.model.CompletionPatternRecord
import com.example.data.model.EmailDraft
import com.example.data.model.ScheduleEvent
import com.example.data.model.TaskItem
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, urgencyScore DESC, deadlineEpochMillis ASC")
    fun getAllTasks(): Flow<List<TaskItem>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY urgencyScore DESC, deadlineEpochMillis ASC")
    fun getPendingTasks(): Flow<List<TaskItem>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND (priority = 'URGENT' OR priority = 'HIGH' OR urgencyScore >= 70) ORDER BY urgencyScore DESC")
    fun getUrgentAndHighTasks(): Flow<List<TaskItem>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 1 ORDER BY completedAtEpochMillis DESC LIMIT 50")
    fun getCompletedTasks(): Flow<List<TaskItem>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): TaskItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskItem>)

    @Update
    suspend fun updateTask(task: TaskItem)

    @Delete
    suspend fun deleteTask(task: TaskItem)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Long)

    @Query("SELECT COUNT(*) FROM tasks WHERE isCompleted = 0")
    fun getPendingCount(): Flow<Int>
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule_events ORDER BY startEpochMillis ASC")
    fun getAllEvents(): Flow<List<ScheduleEvent>>

    @Query("SELECT * FROM schedule_events WHERE startEpochMillis >= :startOfDay AND startEpochMillis < :endOfDay ORDER BY startEpochMillis ASC")
    fun getEventsForRange(startOfDay: Long, endOfDay: Long): Flow<List<ScheduleEvent>>

    @Query("SELECT * FROM schedule_events WHERE startEpochMillis >= :now ORDER BY startEpochMillis ASC LIMIT 1")
    fun getNextUpcomingEvent(now: Long): Flow<ScheduleEvent?>

    @Query("SELECT * FROM schedule_events WHERE id = :id")
    suspend fun getEventById(id: Long): ScheduleEvent?

    @Query("SELECT * FROM schedule_events WHERE startEpochMillis < :end AND endEpochMillis > :start AND id != :excludeId")
    suspend fun findOverlappingEvents(start: Long, end: Long, excludeId: Long): List<ScheduleEvent>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: ScheduleEvent): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<ScheduleEvent>)

    @Update
    suspend fun updateEvent(event: ScheduleEvent)

    @Delete
    suspend fun deleteEvent(event: ScheduleEvent)

    @Query("DELETE FROM schedule_events WHERE id = :id")
    suspend fun deleteEventById(id: Long)
}

@Dao
interface EmailDraftDao {
    @Query("SELECT * FROM email_drafts ORDER BY updatedAtEpochMillis DESC")
    fun getAllDrafts(): Flow<List<EmailDraft>>

    @Query("SELECT * FROM email_drafts WHERE id = :id")
    suspend fun getDraftById(id: Long): EmailDraft?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDraft(draft: EmailDraft): Long

    @Update
    suspend fun updateDraft(draft: EmailDraft)

    @Delete
    suspend fun deleteDraft(draft: EmailDraft)

    @Query("DELETE FROM email_drafts WHERE id = :id")
    suspend fun deleteDraftById(id: Long)
}

@Dao
interface AssistantDao {
    @Query("SELECT * FROM assistant_messages ORDER BY timestampEpochMillis ASC")
    fun getAllMessages(): Flow<List<AssistantMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: AssistantMessage): Long

    @Query("DELETE FROM assistant_messages")
    suspend fun clearHistory()
}

@Dao
interface PatternDao {
    @Query("SELECT * FROM completion_patterns ORDER BY completedAtEpochMillis DESC")
    fun getAllPatterns(): Flow<List<CompletionPatternRecord>>

    @Query("SELECT * FROM completion_patterns WHERE taskCategory = :category")
    suspend fun getPatternsByCategory(category: String): List<CompletionPatternRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPattern(pattern: CompletionPatternRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatterns(patterns: List<CompletionPatternRecord>)
}
