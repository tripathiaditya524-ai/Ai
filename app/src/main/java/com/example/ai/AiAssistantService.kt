package com.example.ai

import android.util.Log
import com.example.data.model.EmailDraft
import com.example.data.model.EmailTone
import com.example.data.model.PriorityLevel
import com.example.data.model.ScheduleEvent
import com.example.data.model.TaskItem
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val TAG = "AiAssistantService"

data class PrioritizedTaskResult(
    val priority: PriorityLevel,
    val urgencyScore: Int,
    val urgencyReason: String,
    val suggestedCategory: String,
    val estimatedMinutes: Int
)

data class ParsedScheduleResult(
    val title: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val location: String,
    val category: String,
    val attendees: String
)

data class DraftedEmailResult(
    val subject: String,
    val body: String,
    val recipient: String,
    val tone: EmailTone
)

class AiAssistantService {

    // Lazy initialization of Gemini model to prevent startup crashes
    private val geminiModel by lazy {
        try {
            Firebase.ai.generativeModel(
                modelName = "gemini-3.8-flash",
                generationConfig = generationConfig {
                    temperature = 0.3f
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase AI model, will use offline engine", e)
            null
        }
    }

    /**
     * Prioritize a task using NLP (Online Gemini with Offline Heuristic Fallback)
     */
    suspend fun prioritizeTask(
        title: String,
        description: String,
        deadlineMillis: Long?,
        forceOffline: Boolean = false
    ): PrioritizedTaskResult = withContext(Dispatchers.IO) {
        if (!forceOffline && geminiModel != null) {
            try {
                return@withContext withTimeout(15_000L) {
                    val prompt = """
                        Analyze this task and prioritize its urgency:
                        Task Title: $title
                        Description: $description
                        Deadline: ${deadlineMillis?.let { Date(it).toString() } ?: "None specified"}
                        
                        Respond in strict raw JSON without backticks or markdown:
                        {
                          "priority": "URGENT" | "HIGH" | "MEDIUM" | "LOW",
                          "urgencyScore": 0-100,
                          "urgencyReason": "One short sentence explaining why this task has this priority level.",
                          "suggestedCategory": "Work" | "Personal" | "Travel" | "Urgent" | "Finance",
                          "estimatedMinutes": 15-180
                        }
                    """.trimIndent()

                    val response = geminiModel?.generateContent(prompt)
                    val text = response?.text?.trim()
                    if (!text.isNullOrBlank()) {
                        val cleaned = text.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                        val json = JSONObject(cleaned)
                        val prioStr = json.optString("priority", "MEDIUM")
                        val score = json.optInt("urgencyScore", 50)
                        val reason = json.optString("urgencyReason", "Prioritized based on impact and deadlines.")
                        val cat = json.optString("suggestedCategory", "Work")
                        val minutes = json.optInt("estimatedMinutes", 30)

                        val level = when (prioStr.uppercase(Locale.US)) {
                            "URGENT" -> PriorityLevel.URGENT
                            "HIGH" -> PriorityLevel.HIGH
                            "LOW" -> PriorityLevel.LOW
                            else -> PriorityLevel.MEDIUM
                        }

                        return@withTimeout PrioritizedTaskResult(
                            priority = level,
                            urgencyScore = score.coerceIn(10, 100),
                            urgencyReason = reason,
                            suggestedCategory = cat,
                            estimatedMinutes = minutes
                        )
                    }
                    fallbackPrioritizeTask(title, description, deadlineMillis)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Online Gemini prioritization failed or timed out, using offline NLP: ${e.message}")
            }
        }
        // Offline intelligent heuristic parser
        fallbackPrioritizeTask(title, description, deadlineMillis)
    }

    /**
     * Parse natural language schedule command (e.g. "Sync with Sarah tomorrow 3pm at HQ")
     */
    suspend fun parseScheduleCommand(
        prompt: String,
        forceOffline: Boolean = false
    ): ParsedScheduleResult = withContext(Dispatchers.IO) {
        if (!forceOffline && geminiModel != null) {
            try {
                return@withContext withTimeout(15_000L) {
                    val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                    val aiPrompt = """
                        Current reference time: $currentTime.
                        Parse the following schedule request: "$prompt"
                        
                        Respond in strict raw JSON without markdown:
                        {
                          "title": "Clean concise meeting title",
                          "startIso": "yyyy-MM-dd HH:mm",
                          "durationMinutes": 30,
                          "location": "Location or Virtual",
                          "category": "Meeting" | "Work" | "Travel" | "Personal",
                          "attendees": "Names mentioned"
                        }
                    """.trimIndent()

                    val response = geminiModel?.generateContent(aiPrompt)
                    val text = response?.text?.trim()
                    if (!text.isNullOrBlank()) {
                        val cleaned = text.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                        val json = JSONObject(cleaned)
                        val title = json.optString("title", prompt)
                        val startStr = json.optString("startIso")
                        val duration = json.optInt("durationMinutes", 45)
                        val location = json.optString("location", "HQ / Video Call")
                        val category = json.optString("category", "Meeting")
                        val attendees = json.optString("attendees", "")

                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                        val startDate = try {
                            sdf.parse(startStr) ?: Date(System.currentTimeMillis() + 3600_000)
                        } catch (e: Exception) {
                            Date(System.currentTimeMillis() + 3600_000)
                        }

                        val startEpoch = startDate.time
                        val endEpoch = startEpoch + (duration * 60_000L)

                        return@withTimeout ParsedScheduleResult(
                            title = title,
                            startEpochMillis = startEpoch,
                            endEpochMillis = endEpoch,
                            location = location,
                            category = category,
                            attendees = attendees
                        )
                    }
                    fallbackParseSchedule(prompt)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Online Gemini schedule parse failed, using offline engine: ${e.message}")
            }
        }
        fallbackParseSchedule(prompt)
    }

    /**
     * Draft an email using NLP
     */
    suspend fun draftEmail(
        prompt: String,
        tone: EmailTone,
        recipient: String = "",
        forceOffline: Boolean = false
    ): DraftedEmailResult = withContext(Dispatchers.IO) {
        if (!forceOffline && geminiModel != null) {
            try {
                return@withContext withTimeout(20_000L) {
                    val aiPrompt = """
                        Draft a complete, professional email based on this user request:
                        Request: "$prompt"
                        Desired Tone: ${tone.displayName}
                        Target Recipient: ${recipient.ifEmpty { "Team / Client" }}
                        
                        Respond in strict JSON:
                        {
                          "subject": "Clear, engaging email subject line",
                          "body": "Complete email body formatted with greetings, paragraphs, and sign-off",
                          "recipient": "Email or placeholder"
                        }
                    """.trimIndent()

                    val response = geminiModel?.generateContent(aiPrompt)
                    val text = response?.text?.trim()
                    if (!text.isNullOrBlank()) {
                        val cleaned = text.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                        val json = JSONObject(cleaned)
                        return@withTimeout DraftedEmailResult(
                            subject = json.optString("subject", "Follow up: $prompt"),
                            body = json.optString("body", "Hi,\n\nFollowing up on: $prompt\n\nBest regards,\nAlex"),
                            recipient = json.optString("recipient", recipient),
                            tone = tone
                        )
                    }
                    fallbackDraftEmail(prompt, tone, recipient)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Online Gemini email draft failed, using offline engine: ${e.message}")
            }
        }
        fallbackDraftEmail(prompt, tone, recipient)
    }

    /**
     * Conversational Assistant response
     */
    suspend fun processAssistantChat(
        userInput: String,
        forceOffline: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        if (!forceOffline && geminiModel != null) {
            try {
                return@withContext withTimeout(15_000L) {
                    val systemPrompt = """
                        You are Aura, an elite AI executive assistant that manages schedules, drafts emails, prioritizes urgent tasks, and coordinates productivity workflows.
                        Keep responses concise, helpful, actionable, and formatted nicely.
                        User query: $userInput
                    """.trimIndent()
                    val response = geminiModel?.generateContent(systemPrompt)
                    val text = response?.text
                    if (!text.isNullOrBlank()) return@withTimeout text
                    fallbackChat(userInput)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Gemini chat failed, fallback to offline: ${e.message}")
            }
        }
        fallbackChat(userInput)
    }

    // ---------------- Offline Heuristic NLP Engine ---------------- //

    private fun fallbackPrioritizeTask(
        title: String,
        description: String,
        deadlineMillis: Long?
    ): PrioritizedTaskResult {
        val combined = "$title $description".lowercase(Locale.ROOT)
        var score = 40
        var reason = "Standard task evaluated by on-device offline NLP engine."
        var level = PriorityLevel.MEDIUM
        var category = "Work"

        val urgentKeywords = listOf("urgent", "asap", "outage", "critical", "emergency", "immediately", "blocker", "502", "crash", "incident", "server down")
        val highKeywords = listOf("important", "deadline", "board", "presentation", "revenue", "client", "investor", "contract", "q4", "flight", "travel", "airport", "visa")
        val travelKeywords = listOf("flight", "travel", "hotel", "airport", "boarding", "ticket", "luggage", "terminal")

        val hasUrgent = urgentKeywords.any { combined.contains(it) }
        val hasHigh = highKeywords.any { combined.contains(it) }
        val hasTravel = travelKeywords.any { combined.contains(it) }

        if (hasTravel) {
            category = "Travel"
        }

        val now = System.currentTimeMillis()
        val hoursRemaining = if (deadlineMillis != null) {
            (deadlineMillis - now) / (1000 * 3600)
        } else null

        if (hasUrgent || (hoursRemaining != null && hoursRemaining <= 4)) {
            level = PriorityLevel.URGENT
            score = 92
            reason = if (hoursRemaining != null && hoursRemaining <= 4) {
                "Imminent deadline (${hoursRemaining.coerceAtLeast(1)}h remaining) with critical operational impact."
            } else {
                "Contains critical urgency indicators ($title)."
            }
        } else if (hasHigh || (hoursRemaining != null && hoursRemaining <= 24)) {
            level = PriorityLevel.HIGH
            score = 78
            reason = "High business/stakeholder impact requiring completion within 24 hours."
        } else if (combined.contains("low") || combined.contains("someday") || combined.contains("optional") || combined.contains("read later")) {
            level = PriorityLevel.LOW
            score = 25
            reason = "Non-blocking background item with flexible execution window."
        } else {
            level = PriorityLevel.MEDIUM
            score = 52
            reason = "Routine scheduled task with standard priority baseline."
        }

        val estimatedMinutes = when (level) {
            PriorityLevel.URGENT -> 45
            PriorityLevel.HIGH -> 60
            PriorityLevel.MEDIUM -> 30
            PriorityLevel.LOW -> 20
        }

        return PrioritizedTaskResult(
            priority = level,
            urgencyScore = score,
            urgencyReason = reason,
            suggestedCategory = category,
            estimatedMinutes = estimatedMinutes
        )
    }

    private fun fallbackParseSchedule(prompt: String): ParsedScheduleResult {
        val lower = prompt.lowercase(Locale.ROOT)
        val cal = Calendar.getInstance()

        // Check if tomorrow or today
        if (lower.contains("tomorrow")) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        } else if (lower.contains("friday")) {
            while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
        } else if (lower.contains("monday")) {
            while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // Time parsing (e.g. 10am, 2pm, 14:00, 3:30)
        var hour = 11
        var minute = 0

        val timeRegex = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""")
        val match = timeRegex.find(lower)
        if (match != null) {
            val h = match.groupValues[1].toIntOrNull() ?: 11
            val m = match.groupValues[2].toIntOrNull() ?: 0
            val ampm = match.groupValues[3]
            hour = if (ampm == "pm" && h < 12) h + 12 else if (ampm == "am" && h == 12) 0 else h
            minute = m
        }

        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val start = cal.timeInMillis
        val end = start + (45 * 60 * 1000) // Default 45 mins

        // Extract title
        var title = prompt
            .replace(Regex("""(?i)(schedule|book|add|set up|create)\s+"""), "")
            .replace(Regex("""(?i)(tomorrow|today|at\s+\d{1,2}(?::\d{2})?\s*(?:am|pm)?|on\s+\w+)"""), "")
            .trim()
        if (title.isBlank()) title = "Productivity Meeting"
        title = title.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }

        val location = if (lower.contains("office") || lower.contains("room")) "Office Conference Room"
        else if (lower.contains("cafe") || lower.contains("coffee")) "Cafe Lounge"
        else if (lower.contains("airport") || lower.contains("terminal")) "Airport Terminal"
        else "Google Meet / Video Call"

        return ParsedScheduleResult(
            title = title,
            startEpochMillis = start,
            endEpochMillis = end,
            location = location,
            category = if (lower.contains("flight") || lower.contains("travel")) "Travel" else "Meeting",
            attendees = "Invited Team Members"
        )
    }

    private fun fallbackDraftEmail(prompt: String, tone: EmailTone, recipient: String): DraftedEmailResult {
        val subject = when (tone) {
            EmailTone.URGENT -> "URGENT ACTION REQUIRED: ${prompt.take(40)}"
            EmailTone.FOLLOW_UP -> "Following up on: ${prompt.take(40)}"
            EmailTone.CONCISE -> "Quick Update: ${prompt.take(40)}"
            EmailTone.FRIENDLY -> "Checking in regarding: ${prompt.take(40)}"
            EmailTone.PROFESSIONAL -> "Project Overview: ${prompt.take(40)}"
        }

        val greeting = if (recipient.isNotBlank()) "Dear ${recipient.substringBefore('@')}," else "Hi Team,"

        val body = when (tone) {
            EmailTone.URGENT -> """
                $greeting
                
                Please note that immediate attention is required regarding the following matter:
                $prompt
                
                We need this addressed as soon as possible to prevent further delays. Please confirm receipt and let me know your estimated completion time.
                
                Thank you for your prompt response,
                Alex
            """.trimIndent()

            EmailTone.CONCISE -> """
                $greeting
                
                Quick update regarding:
                • $prompt
                
                Please let me know if there are any questions.
                
                Best,
                Alex
            """.trimIndent()

            EmailTone.FOLLOW_UP -> """
                $greeting
                
                I hope you are having a productive week. I am following up on our previous discussion regarding:
                $prompt
                
                Could you please share the latest status update when you have a moment?
                
                Warm regards,
                Alex
            """.trimIndent()

            EmailTone.FRIENDLY -> """
                $greeting
                
                Hope you're having a great day!
                
                I wanted to touch base with you about:
                $prompt
                
                Looking forward to catching up and getting your thoughts on this!
                
                Best wishes,
                Alex
            """.trimIndent()

            EmailTone.PROFESSIONAL -> """
                $greeting
                
                I am writing to provide a structured update regarding:
                $prompt
                
                Our objective is to ensure alignment across all stakeholders and maintain our delivery milestone schedule. Please review the above details and advise if any adjustments are recommended.
                
                Sincerely,
                Alex
            """.trimIndent()
        }

        return DraftedEmailResult(
            subject = subject,
            body = body,
            recipient = recipient,
            tone = tone
        )
    }

    private fun fallbackChat(input: String): String {
        val lower = input.lowercase(Locale.ROOT)
        return when {
            lower.contains("schedule") || lower.contains("calendar") || lower.contains("meeting") ->
                "I can schedule events directly into your calendar. Try saying: 'Schedule sync tomorrow at 2 PM' or tap the Calendar tab to export to your device calendar."
            lower.contains("email") || lower.contains("draft") ->
                "I can draft polished emails with custom tones (Professional, Concise, Urgent). Go to the Email Drafter tab or say 'Draft an email to client about project milestone'."
            lower.contains("urgent") || lower.contains("prioritize") || lower.contains("task") ->
                "I automatically prioritize your urgent tasks using NLP. Tap 'Prioritize All' in the Tasks tab to compute urgency scores and view detailed explanations."
            lower.contains("travel") || lower.contains("flight") || lower.contains("offline") ->
                "Travel Mode is active! All AI scheduling, email drafting, and priority analysis operate 100% offline using our local Room intelligence engine."
            else ->
                "I am your Aura AI assistant. I'm ready to manage your schedule, draft emails, auto-prioritize tasks, and monitor your productivity patterns. What can I do for you?"
        }
    }
}
