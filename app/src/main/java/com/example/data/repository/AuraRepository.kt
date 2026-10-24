package com.example.data.repository

import com.example.data.local.AuraDatabase
import com.example.data.model.AssistantMessage
import com.example.data.model.CompletionPatternRecord
import com.example.data.model.EmailDraft
import com.example.data.model.EmailTone
import com.example.data.model.PriorityLevel
import com.example.data.model.ScheduleEvent
import com.example.data.model.TaskItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Calendar

class AuraRepository(private val database: AuraDatabase) {
    private val taskDao = database.taskDao()
    private val scheduleDao = database.scheduleDao()
    private val emailDraftDao = database.emailDraftDao()
    private val assistantDao = database.assistantDao()
    private val patternDao = database.patternDao()

    val allTasks: Flow<List<TaskItem>> = taskDao.getAllTasks()
    val pendingTasks: Flow<List<TaskItem>> = taskDao.getPendingTasks()
    val urgentAndHighTasks: Flow<List<TaskItem>> = taskDao.getUrgentAndHighTasks()
    val completedTasks: Flow<List<TaskItem>> = taskDao.getCompletedTasks()
    val pendingCount: Flow<Int> = taskDao.getPendingCount()

    val allEvents: Flow<List<ScheduleEvent>> = scheduleDao.getAllEvents()
    fun getNextUpcomingEvent(now: Long = System.currentTimeMillis()): Flow<ScheduleEvent?> =
        scheduleDao.getNextUpcomingEvent(now)

    val allDrafts: Flow<List<EmailDraft>> = emailDraftDao.getAllDrafts()
    val assistantMessages: Flow<List<AssistantMessage>> = assistantDao.getAllMessages()
    val allPatterns: Flow<List<CompletionPatternRecord>> = patternDao.getAllPatterns()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            seedInitialDataIfNeeded()
        }
    }

    suspend fun insertTask(task: TaskItem): Long = taskDao.insertTask(task)
    suspend fun updateTask(task: TaskItem) = taskDao.updateTask(task)
    suspend fun deleteTask(task: TaskItem) = taskDao.deleteTask(task)
    suspend fun getTaskById(id: Long): TaskItem? = taskDao.getTaskById(id)

    suspend fun completeTask(task: TaskItem, locationContext: String = "Office") {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)

        taskDao.updateTask(
            task.copy(
                isCompleted = true,
                completedAtEpochMillis = now
            )
        )

        // Record historical completion pattern for smart learning
        patternDao.insertPattern(
            CompletionPatternRecord(
                taskCategory = task.category,
                hourOfDay = hour,
                dayOfWeek = dayOfWeek,
                durationMinutes = task.estimatedMinutes,
                locationContext = locationContext,
                completedAtEpochMillis = now
            )
        )
    }

    suspend fun insertEvent(event: ScheduleEvent): Long {
        // Detect overlapping conflicts
        val overlaps = scheduleDao.findOverlappingEvents(event.startEpochMillis, event.endEpochMillis, event.id)
        val hasConflict = overlaps.isNotEmpty()
        val eventToInsert = event.copy(conflictDetected = hasConflict)
        return scheduleDao.insertEvent(eventToInsert)
    }

    suspend fun updateEvent(event: ScheduleEvent) {
        val overlaps = scheduleDao.findOverlappingEvents(event.startEpochMillis, event.endEpochMillis, event.id)
        val hasConflict = overlaps.isNotEmpty()
        scheduleDao.updateEvent(event.copy(conflictDetected = hasConflict))
    }

    suspend fun deleteEvent(event: ScheduleEvent) = scheduleDao.deleteEvent(event)

    suspend fun insertEmailDraft(draft: EmailDraft): Long = emailDraftDao.insertDraft(draft)
    suspend fun updateEmailDraft(draft: EmailDraft) = emailDraftDao.updateDraft(draft)
    suspend fun deleteEmailDraft(draft: EmailDraft) = emailDraftDao.deleteDraft(draft)

    suspend fun insertAssistantMessage(message: AssistantMessage): Long =
        assistantDao.insertMessage(message)

    suspend fun clearChatHistory() = assistantDao.clearHistory()

    private suspend fun seedInitialDataIfNeeded() {
        val existingTasks = taskDao.getPendingTasks().firstOrNull()
        if (!existingTasks.isNullOrEmpty()) return

        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = now }

        // Seed 4 sample tasks with smart priorities
        val seedTasks = listOf(
            TaskItem(
                title = "Resolve Client Enterprise API Outage",
                description = "Customer reported intermittent 502 errors on production checkout gateway. Requires immediate log triage.",
                priority = PriorityLevel.URGENT,
                urgencyScore = 95,
                urgencyReason = "Critical production incident affecting client revenue. Due within 2 hours.",
                deadlineEpochMillis = now + (2 * 3600 * 1000),
                category = "Work",
                locationTag = "Office",
                estimatedMinutes = 45
            ),
            TaskItem(
                title = "Prepare Q4 Executive Product Brief",
                description = "Consolidate KPI metrics, roadmap timeline, and hiring estimates for Friday board review.",
                priority = PriorityLevel.HIGH,
                urgencyScore = 80,
                urgencyReason = "High-visibility leadership presentation with cross-team dependencies.",
                deadlineEpochMillis = now + (24 * 3600 * 1000),
                category = "Work",
                locationTag = "Office",
                estimatedMinutes = 60
            ),
            TaskItem(
                title = "Check In for Flight SFO -> NYC (Travel Mode)",
                description = "Confirm seat assignment, download offline mobile boarding pass, and pack carry-on essentials.",
                priority = PriorityLevel.HIGH,
                urgencyScore = 75,
                urgencyReason = "Travel departure tomorrow morning. Window opens 24h prior to flight.",
                deadlineEpochMillis = now + (18 * 3600 * 1000),
                category = "Travel",
                locationTag = "Airport",
                estimatedMinutes = 15
            ),
            TaskItem(
                title = "Review Weekly Design Sprint Retrospective",
                description = "Review user feedback notes from the usability testing lab.",
                priority = PriorityLevel.MEDIUM,
                urgencyScore = 45,
                urgencyReason = "Standard weekly cadence, no immediate blockers.",
                deadlineEpochMillis = now + (48 * 3600 * 1000),
                category = "Work",
                locationTag = "Home",
                estimatedMinutes = 30
            )
        )
        taskDao.insertTasks(seedTasks)

        // Seed Schedule Events
        cal.set(Calendar.HOUR_OF_DAY, 10)
        cal.set(Calendar.MINUTE, 0)
        val event1Start = cal.timeInMillis
        val event1End = event1Start + (45 * 60 * 1000)

        cal.set(Calendar.HOUR_OF_DAY, 14)
        cal.set(Calendar.MINUTE, 0)
        val event2Start = cal.timeInMillis
        val event2End = event2Start + (60 * 60 * 1000)

        cal.set(Calendar.HOUR_OF_DAY, 16)
        cal.set(Calendar.MINUTE, 30)
        val event3Start = cal.timeInMillis
        val event3End = event3Start + (30 * 60 * 1000)

        val seedEvents = listOf(
            ScheduleEvent(
                title = "Engineering Architecture Sync",
                description = "Discuss offline sync caching architecture and API rate limiting.",
                startEpochMillis = event1Start,
                endEpochMillis = event1End,
                location = "HQ Room 4B / Meet",
                category = "Meeting",
                attendees = "Alex, Sarah, David"
            ),
            ScheduleEvent(
                title = "Q4 Product Strategy Review",
                description = "Executive walkthrough of key roadmap deliverables.",
                startEpochMillis = event2Start,
                endEpochMillis = event2End,
                location = "Executive Boardroom",
                category = "Strategy",
                attendees = "VP Product, Design Lead"
            ),
            ScheduleEvent(
                title = "Travel Prep & Airport Departure",
                description = "Head to terminal 2 for evening flight to NYC.",
                startEpochMillis = event3Start,
                endEpochMillis = event3End,
                location = "SFO Terminal 2",
                category = "Travel",
                attendees = "Solo"
            )
        )
        scheduleDao.insertEvents(seedEvents)

        // Seed Draft Email
        emailDraftDao.insertDraft(
            EmailDraft(
                recipient = "team@company.org",
                subject = "Update: Enterprise API Status and Mitigation Plan",
                body = "Hi Team,\n\nWe have identified the root cause of the intermittent 502 errors and deployed a hotfix to our gateway nodes. All services are currently nominal, and we will monitor traffic through peak hours.\n\nPlease let me know if you observe any anomalies.\n\nBest regards,\nAlex",
                tone = EmailTone.PROFESSIONAL,
                intentPrompt = "Draft team update on API incident resolution"
            )
        )

        // Seed historical pattern records
        val patternList = listOf(
            CompletionPatternRecord(taskCategory = "Work", hourOfDay = 9, dayOfWeek = 2, durationMinutes = 30, locationContext = "Office"),
            CompletionPatternRecord(taskCategory = "Work", hourOfDay = 10, dayOfWeek = 3, durationMinutes = 45, locationContext = "Office"),
            CompletionPatternRecord(taskCategory = "Work", hourOfDay = 10, dayOfWeek = 4, durationMinutes = 20, locationContext = "Office"),
            CompletionPatternRecord(taskCategory = "Urgent", hourOfDay = 9, dayOfWeek = 5, durationMinutes = 25, locationContext = "Office"),
            CompletionPatternRecord(taskCategory = "Travel", hourOfDay = 18, dayOfWeek = 5, durationMinutes = 15, locationContext = "Airport")
        )
        patternDao.insertPatterns(patternList)

        // Seed initial assistant greeting
        assistantDao.insertMessage(
            AssistantMessage(
                isUser = false,
                text = "Hello! I am Aura, your AI productivity assistant. I can manage your schedule, draft high-impact emails, and automatically prioritize urgent tasks using NLP. What would you like to accomplish today?",
                actionType = "WELCOME"
            )
        )
    }
}
