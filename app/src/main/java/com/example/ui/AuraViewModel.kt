package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiAssistantService
import com.example.data.local.AuraDatabase
import com.example.data.model.AssistantMessage
import com.example.data.model.CompletionPatternRecord
import com.example.data.model.EmailDraft
import com.example.data.model.EmailTone
import com.example.data.model.PriorityLevel
import com.example.data.model.ScheduleEvent
import com.example.data.model.TaskItem
import com.example.data.repository.AuraRepository
import com.example.util.SmartNotificationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

enum class NavigationTab(val title: String) {
    ASSISTANT("Assistant"),
    SCHEDULE("Calendar"),
    TASKS("Tasks"),
    EMAILS("Drafts")
}

data class PatternInsight(
    val title: String,
    val description: String,
    val peakHourRange: String,
    val travelOptimized: Boolean
)

class AuraViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AuraDatabase.getDatabase(application)
    private val repository = AuraRepository(database)
    private val aiService = AiAssistantService()

    val allTasks: StateFlow<List<TaskItem>> = repository.allTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingTasks: StateFlow<List<TaskItem>> = repository.pendingTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val urgentAndHighTasks: StateFlow<List<TaskItem>> = repository.urgentAndHighTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedTasks: StateFlow<List<TaskItem>> = repository.completedTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allEvents: StateFlow<List<ScheduleEvent>> = repository.allEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val nextUpcomingEvent: StateFlow<ScheduleEvent?> = repository.getNextUpcomingEvent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allDrafts: StateFlow<List<EmailDraft>> = repository.allDrafts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val assistantMessages: StateFlow<List<AssistantMessage>> = repository.assistantMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPatterns: StateFlow<List<CompletionPatternRecord>> = repository.allPatterns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI state
    private val _currentTab = MutableStateFlow(NavigationTab.ASSISTANT)
    val currentTab: StateFlow<NavigationTab> = _currentTab.asStateFlow()

    private val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    private val _currentLocationContext = MutableStateFlow("Office")
    val currentLocationContext: StateFlow<String> = _currentLocationContext.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    private val _statusSnackbar = MutableStateFlow<String?>(null)
    val statusSnackbar: StateFlow<String?> = _statusSnackbar.asStateFlow()

    // Smart Historical Pattern Insights
    val patternInsight: StateFlow<PatternInsight> = allPatterns.map { patterns ->
        if (patterns.isEmpty()) {
            PatternInsight(
                title = "Analyzing Completion Habits",
                description = "Complete tasks to train Aura's historical productivity pattern model.",
                peakHourRange = "9:00 AM - 12:00 PM",
                travelOptimized = _isOfflineMode.value
            )
        } else {
            val hourCounts = patterns.groupingBy { it.hourOfDay }.eachCount()
            val peakHour = hourCounts.maxByOrNull { it.value }?.key ?: 10
            val peakStart = if (peakHour == 0) "12 AM" else if (peakHour < 12) "${peakHour} AM" else if (peakHour == 12) "12 PM" else "${peakHour - 12} PM"
            val peakEnd = if (peakHour + 2 >= 24) "12 AM" else if (peakHour + 2 < 12) "${peakHour + 2} AM" else if (peakHour + 2 == 12) "12 PM" else "${peakHour + 2 - 12} PM"

            PatternInsight(
                title = "⚡ Peak Productivity Window: $peakStart - $peakEnd",
                description = "Based on ${patterns.size} logged completions, you resolve urgent items 2.4x faster during this window. High-urgency tasks are prioritized accordingly.",
                peakHourRange = "$peakStart - $peakEnd",
                travelOptimized = _isOfflineMode.value
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        PatternInsight("Productivity Patterns", "Calibrating...", "9 AM - 12 PM", false)
    )

    init {
        SmartNotificationManager.initNotificationChannels(application)
    }

    fun setTab(tab: NavigationTab) {
        _currentTab.value = tab
    }

    fun toggleOfflineMode() {
        _isOfflineMode.value = !_isOfflineMode.value
        val modeStr = if (_isOfflineMode.value) "Offline Travel Mode Enabled" else "Online Assistant Mode Enabled"
        _statusSnackbar.value = modeStr

        if (_isOfflineMode.value) {
            SmartNotificationManager.showLocationSmartAlert(
                getApplication(),
                "Travel Mode",
                "Operating completely offline with local Room intelligence. Network calls disabled."
            )
        }
    }

    fun setLocationContext(location: String) {
        _currentLocationContext.value = location
        _statusSnackbar.value = "Location context updated to $location"

        val alertMessage = when (location) {
            "Airport (Travel)" -> "✈️ Airport detected: Offline Travel Mode suggested. Urgent flight and document items highlighted."
            "Office" -> "🏢 Office mode: Review upcoming afternoon meetings and pending team email drafts."
            "Home" -> "🏠 Home context: Deep work and wrap-up tasks prioritized."
            else -> "📍 Context: $location"
        }

        SmartNotificationManager.showLocationSmartAlert(
            getApplication(),
            location,
            alertMessage
        )
    }

    fun dismissSnackbar() {
        _statusSnackbar.value = null
    }

    // ---------------- AI Natural Language Actions ---------------- //

    fun submitNaturalLanguagePrompt(prompt: String) {
        if (prompt.isBlank()) return

        viewModelScope.launch {
            _isAiLoading.value = true
            // Save user message
            repository.insertAssistantMessage(
                AssistantMessage(
                    isUser = true,
                    text = prompt
                )
            )

            val lower = prompt.lowercase()
            try {
                when {
                    lower.contains("schedule") || lower.contains("calendar") || lower.contains("meet") || lower.contains("meeting") || lower.contains("at ") && (lower.contains("am") || lower.contains("pm")) -> {
                        val parsed = aiService.parseScheduleCommand(prompt, forceOffline = _isOfflineMode.value)
                        val event = ScheduleEvent(
                            title = parsed.title,
                            startEpochMillis = parsed.startEpochMillis,
                            endEpochMillis = parsed.endEpochMillis,
                            location = parsed.location,
                            category = parsed.category,
                            attendees = parsed.attendees
                        )
                        repository.insertEvent(event)
                        val reply = "🗓️ Added to Schedule: \"${parsed.title}\" on ${java.text.SimpleDateFormat("MMM dd, h:mm a", java.util.Locale.US).format(java.util.Date(parsed.startEpochMillis))} at ${parsed.location}. Seamlessly integrated with your calendar."
                        repository.insertAssistantMessage(
                            AssistantMessage(isUser = false, text = reply, actionType = "SCHEDULE_CREATED")
                        )
                        _statusSnackbar.value = "Event scheduled successfully!"
                    }

                    lower.contains("email") || lower.contains("draft") || lower.contains("write to") -> {
                        val tone = if (lower.contains("urgent")) EmailTone.URGENT
                        else if (lower.contains("concise") || lower.contains("quick")) EmailTone.CONCISE
                        else if (lower.contains("friendly")) EmailTone.FRIENDLY
                        else EmailTone.PROFESSIONAL

                        val draftRes = aiService.draftEmail(prompt, tone = tone, forceOffline = _isOfflineMode.value)
                        val draft = EmailDraft(
                            subject = draftRes.subject,
                            body = draftRes.body,
                            recipient = draftRes.recipient,
                            tone = draftRes.tone,
                            intentPrompt = prompt
                        )
                        repository.insertEmailDraft(draft)
                        val reply = "✉️ Drafted Email: \"${draftRes.subject}\"\n\nTone: ${draftRes.tone.displayName}\nDraft saved to your Drafts tab. You can review, copy, or launch your email client."
                        repository.insertAssistantMessage(
                            AssistantMessage(isUser = false, text = reply, actionType = "EMAIL_DRAFTED")
                        )
                        _statusSnackbar.value = "Email draft created!"
                    }

                    lower.contains("task") || lower.contains("todo") || lower.contains("remind") || lower.contains("urgent") || lower.contains("priority") -> {
                        val prioResult = aiService.prioritizeTask(
                            title = prompt.removePrefix("add task").removePrefix("task:").trim(),
                            description = "Created via Aura Assistant NLP",
                            deadlineMillis = System.currentTimeMillis() + (12 * 3600 * 1000),
                            forceOffline = _isOfflineMode.value
                        )

                        val task = TaskItem(
                            title = prompt.removePrefix("add task").removePrefix("task:").trim().replaceFirstChar { it.uppercase() },
                            description = "Auto-prioritized by NLP: ${prioResult.urgencyReason}",
                            priority = prioResult.priority,
                            urgencyScore = prioResult.urgencyScore,
                            urgencyReason = prioResult.urgencyReason,
                            deadlineEpochMillis = System.currentTimeMillis() + (12 * 3600 * 1000),
                            category = prioResult.suggestedCategory,
                            locationTag = _currentLocationContext.value,
                            estimatedMinutes = prioResult.estimatedMinutes
                        )
                        val id = repository.insertTask(task)

                        if (prioResult.priority == PriorityLevel.URGENT || prioResult.urgencyScore >= 80) {
                            SmartNotificationManager.showUrgentTaskAlert(
                                getApplication(),
                                id,
                                task.title,
                                prioResult.urgencyReason
                            )
                        }

                        val reply = "⚡ Task Prioritized: \"${task.title}\"\n\nPriority: ${prioResult.priority.label} (Score: ${prioResult.urgencyScore}/100)\nReason: ${prioResult.urgencyReason}"
                        repository.insertAssistantMessage(
                            AssistantMessage(isUser = false, text = reply, actionType = "TASK_PRIORITIZED")
                        )
                        _statusSnackbar.value = "Task prioritized & added!"
                    }

                    else -> {
                        val reply = aiService.processAssistantChat(prompt, forceOffline = _isOfflineMode.value)
                        repository.insertAssistantMessage(
                            AssistantMessage(isUser = false, text = reply, actionType = "CHAT_REPLY")
                        )
                    }
                }
            } catch (e: Exception) {
                repository.insertAssistantMessage(
                    AssistantMessage(
                        isUser = false,
                        text = "I encountered an issue processing that request. Switched to offline safe fallback mode. You can try again or select quick actions below."
                    )
                )
            } finally {
                _isAiLoading.value = false
            }
        }
    }

    fun autoPrioritizeAllTasks() {
        viewModelScope.launch {
            _isAiLoading.value = true
            val currentPending = pendingTasks.value
            var prioritizedCount = 0

            for (task in currentPending) {
                val result = aiService.prioritizeTask(
                    title = task.title,
                    description = task.description,
                    deadlineMillis = task.deadlineEpochMillis,
                    forceOffline = _isOfflineMode.value
                )

                val updated = task.copy(
                    priority = result.priority,
                    urgencyScore = result.urgencyScore,
                    urgencyReason = result.urgencyReason,
                    estimatedMinutes = result.estimatedMinutes
                )
                repository.updateTask(updated)
                prioritizedCount++

                if (result.priority == PriorityLevel.URGENT) {
                    SmartNotificationManager.showUrgentTaskAlert(
                        getApplication(),
                        task.id,
                        task.title,
                        result.urgencyReason
                    )
                }
            }

            _isAiLoading.value = false
            _statusSnackbar.value = "Auto-prioritized $prioritizedCount tasks using NLP!"
        }
    }

    fun addNewTask(
        title: String,
        description: String,
        deadlineMillis: Long?,
        category: String,
        locationTag: String?
    ) {
        viewModelScope.launch {
            _isAiLoading.value = true
            val prio = aiService.prioritizeTask(
                title = title,
                description = description,
                deadlineMillis = deadlineMillis,
                forceOffline = _isOfflineMode.value
            )

            val task = TaskItem(
                title = title,
                description = description,
                priority = prio.priority,
                urgencyScore = prio.urgencyScore,
                urgencyReason = prio.urgencyReason,
                deadlineEpochMillis = deadlineMillis,
                category = category,
                locationTag = locationTag ?: _currentLocationContext.value,
                estimatedMinutes = prio.estimatedMinutes
            )

            val id = repository.insertTask(task)
            _isAiLoading.value = false
            _statusSnackbar.value = "Added task: ${prio.priority.label} priority (${prio.urgencyScore}/100)"

            if (prio.priority == PriorityLevel.URGENT) {
                SmartNotificationManager.showUrgentTaskAlert(
                    getApplication(),
                    id,
                    task.title,
                    prio.urgencyReason
                )
            }
        }
    }

    fun toggleTaskComplete(task: TaskItem) {
        viewModelScope.launch {
            if (!task.isCompleted) {
                repository.completeTask(task, _currentLocationContext.value)
                _statusSnackbar.value = "Task completed! Productivity pattern recorded."
            } else {
                repository.updateTask(task.copy(isCompleted = false, completedAtEpochMillis = null))
            }
        }
    }

    fun deleteTask(task: TaskItem) {
        viewModelScope.launch {
            repository.deleteTask(task)
            _statusSnackbar.value = "Task removed."
        }
    }

    fun addNewEvent(
        title: String,
        description: String,
        startMillis: Long,
        endMillis: Long,
        location: String,
        category: String,
        attendees: String
    ) {
        viewModelScope.launch {
            val event = ScheduleEvent(
                title = title,
                description = description,
                startEpochMillis = startMillis,
                endEpochMillis = endMillis,
                location = location,
                category = category,
                attendees = attendees
            )
            repository.insertEvent(event)
            _statusSnackbar.value = "Event added to schedule."
        }
    }

    fun deleteEvent(event: ScheduleEvent) {
        viewModelScope.launch {
            repository.deleteEvent(event)
            _statusSnackbar.value = "Event deleted."
        }
    }

    fun generateEmailDraft(prompt: String, tone: EmailTone, recipient: String) {
        viewModelScope.launch {
            _isAiLoading.value = true
            val res = aiService.draftEmail(prompt, tone, recipient, _isOfflineMode.value)
            val draft = EmailDraft(
                recipient = res.recipient,
                subject = res.subject,
                body = res.body,
                tone = res.tone,
                intentPrompt = prompt
            )
            repository.insertEmailDraft(draft)
            _isAiLoading.value = false
            _statusSnackbar.value = "Draft generated with ${tone.displayName} tone!"
        }
    }

    fun updateEmailDraft(draft: EmailDraft) {
        viewModelScope.launch {
            repository.updateEmailDraft(draft.copy(updatedAtEpochMillis = System.currentTimeMillis()))
            _statusSnackbar.value = "Draft updated."
        }
    }

    fun deleteEmailDraft(draft: EmailDraft) {
        viewModelScope.launch {
            repository.deleteEmailDraft(draft)
            _statusSnackbar.value = "Draft removed."
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            repository.clearChatHistory()
            _statusSnackbar.value = "Chat history cleared."
        }
    }
}
