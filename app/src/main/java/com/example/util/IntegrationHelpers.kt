package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
import com.example.data.model.EmailDraft
import com.example.data.model.ScheduleEvent
import com.example.data.model.TaskItem

object IntegrationHelpers {

    /**
     * Seamless integration with device calendar (Google Calendar, Outlook, etc.)
     */
    fun exportEventToDeviceCalendar(context: Context, event: ScheduleEvent) {
        try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.startEpochMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endEpochMillis)
                putExtra(CalendarContract.Events.TITLE, event.title)
                putExtra(CalendarContract.Events.DESCRIPTION, event.description)
                putExtra(CalendarContract.Events.EVENT_LOCATION, event.location)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No calendar application found: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Seamless email sending via installed email client (Gmail, Outlook, etc.)
     */
    fun openEmailClient(context: Context, draft: EmailDraft) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:${draft.recipient}")
                putExtra(Intent.EXTRA_SUBJECT, draft.subject)
                putExtra(Intent.EXTRA_TEXT, draft.body)
                if (draft.cc.isNotBlank()) {
                    putExtra(Intent.EXTRA_CC, arrayOf(draft.cc))
                }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to standard share
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, draft.subject)
                putExtra(Intent.EXTRA_TEXT, draft.body)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(shareIntent, "Send Email via..."))
        }
    }

    /**
     * Export / share task to productivity tools (Slack, Keep, WhatsApp, Notion, etc.)
     */
    fun shareProductivityItem(context: Context, title: String, content: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "$title\n\n$content")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(intent, "Share with Productivity Tools"))
        } catch (e: Exception) {
            Toast.makeText(context, "Could not share item", Toast.LENGTH_SHORT).show()
        }
    }
}
