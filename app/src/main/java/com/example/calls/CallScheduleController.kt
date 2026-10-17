package com.example.calls

import android.app.AlarmManager
import android.app.BroadcastReceiver
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.MainActivity
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class CallScheduleResult(
    val kind: Kind,
    val scheduledAtMillis: Long? = null,
    val message: String
) {
    enum class Kind { SCHEDULED, NEEDS_CLARIFICATION, EVENT_SOURCE_REQUIRED }
}

object CallScheduleController {
    const val EXTRA_OPEN_CALL = "open_nexusai_call"
    const val CHANNEL_ID = "nexusai_incoming_calls"
    private const val PREFS = "nexusai_call_preferences"
    private const val KEY_WAITING_FOR_TIME = "waiting_for_call_time"
    private const val ACTION_DISMISS = "com.example.calls.DISMISS_SCHEDULED_CALL"

    fun handleMessage(context: Context, text: String): CallScheduleResult? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val normalized = text.lowercase(Locale.ROOT)
        val callRequest = listOf(
            "call me", "schedule a call", "call me when", "call me at",
            "اتصل بي", "اتصل علي", "دگلي", "دكلي", "اتصل عندما", "اتصل من"
        ).any { normalized.contains(it) }
        val eventRequest = listOf(
            "email arrives", "receive an email", "new email", "gmail",
            "github", "repository", "repo", "workflow", "إيميل", "ايميل",
            "بريد", "گيت هب", "جيت هب", "المستودع"
        ).any { normalized.contains(it) }

        if (callRequest && eventRequest) {
            prefs.edit().putBoolean(KEY_WAITING_FOR_TIME, false).apply()
            return CallScheduleResult(
                CallScheduleResult.Kind.EVENT_SOURCE_REQUIRED,
                message = "Event-triggered calls need a connected Gmail/GitHub event source. That connection is not configured yet."
            )
        }

        val parsedTime = parseRequestedTime(text)
        if (callRequest) {
            if (parsedTime == null) {
                prefs.edit().putBoolean(KEY_WAITING_FOR_TIME, true).apply()
                return CallScheduleResult(
                    CallScheduleResult.Kind.NEEDS_CLARIFICATION,
                    message = "What time should NexusAI call you? Please include a time such as 12:39 PM."
                )
            }
            prefs.edit().putBoolean(KEY_WAITING_FOR_TIME, false).apply()
            schedule(context, parsedTime)
            return CallScheduleResult(
                CallScheduleResult.Kind.SCHEDULED,
                scheduledAtMillis = parsedTime,
                message = "NexusAI call scheduled for " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(parsedTime))
            )
        }

        if (prefs.getBoolean(KEY_WAITING_FOR_TIME, false) && parsedTime != null) {
            prefs.edit().putBoolean(KEY_WAITING_FOR_TIME, false).apply()
            schedule(context, parsedTime)
            return CallScheduleResult(
                CallScheduleResult.Kind.SCHEDULED,
                scheduledAtMillis = parsedTime,
                message = "NexusAI call scheduled for " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(parsedTime))
            )
        }
        return null
    }

    fun scheduleFromText(context: Context, text: String): CallScheduleResult {
        val time = parseRequestedTime(text) ?: return CallScheduleResult(
            CallScheduleResult.Kind.NEEDS_CLARIFICATION,
            message = "Enter a clear time, such as 12:39 PM or tomorrow at 8:00 AM."
        )
        schedule(context, time)
        return CallScheduleResult(
            CallScheduleResult.Kind.SCHEDULED,
            scheduledAtMillis = time,
            message = "NexusAI call scheduled for " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))
        )
    }

    private fun parseRequestedTime(text: String): Long? {
        val match = Regex("""(?i)(\d{1,2})(?::(\d{2}))?\s*(am|pm|a\.m\.|p\.m\.|صباحاً|صباحا|الصبح|مساءً|مساءا|المساء|الظهر|ظهر)?""")
            .find(text) ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
        if (hour !in 0..23 || minute !in 0..59) return null
        val marker = match.groupValues[3].lowercase(Locale.ROOT)
        val isPm = marker == "pm" || marker == "p.m." || marker.contains("مساء") || marker.contains("ظهر")
        val isAm = marker == "am" || marker == "a.m." || marker.contains("صباح") || marker.contains("الصبح")
        if (marker.isNotBlank()) {
            if (isPm && hour < 12) hour += 12
            if (isAm && hour == 12) hour = 0
        }

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val lower = text.lowercase(Locale.ROOT)
        val explicitlyTomorrow = listOf("tomorrow", "باچر", "باجر", "غدا", "غداً").any { lower.contains(it) }
        if (explicitlyTomorrow) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        } else if (calendar.timeInMillis <= System.currentTimeMillis() + 30_000L) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    private fun schedule(context: Context, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent = Intent(context, ScheduledCallReceiver::class.java)
            .putExtra("scheduled_call_at", triggerAtMillis)
        val requestCode = (triggerAtMillis xor (triggerAtMillis ushr 32)).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    internal fun dismissNotification(context: Context, notificationId: Int) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(notificationId)
    }

    internal fun showIncomingCallNotification(context: Context, notificationId: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "NexusAI scheduled calls", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Reminders for calls you scheduled with NexusAI"
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                }
            )
        }
        val openCallIntent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_CALL, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val answerPending = PendingIntent.getActivity(
            context, notificationId, openCallIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismissIntent = Intent(context, ScheduledCallReceiver::class.java).setAction(ACTION_DISMISS)
            .putExtra("notification_id", notificationId)
        val dismissPending = PendingIntent.getBroadcast(
            context, notificationId + 1, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle("NexusAI is calling")
            .setContentText("It's time for the call you scheduled.")
            .setCategory(Notification.CATEGORY_CALL)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(answerPending)
            .addAction(Notification.Action.Builder(0, "Answer", answerPending).build())
            .addAction(Notification.Action.Builder(0, "Dismiss", dismissPending).build())
            .build()
        manager.notify(notificationId, notification)
    }
}

class ScheduledCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == "com.example.calls.DISMISS_SCHEDULED_CALL") {
            CallScheduleController.dismissNotification(context, intent.getIntExtra("notification_id", 0))
            return
        }
        val timestamp = intent?.getLongExtra("scheduled_call_at", System.currentTimeMillis()) ?: System.currentTimeMillis()
        val notificationId = (timestamp xor (timestamp ushr 32)).toInt()
        CallScheduleController.showIncomingCallNotification(context, notificationId)
    }
}
