package com.example.calls

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object GitHubWorkflowCallMonitor {
    const val ACTION_POLL = "com.example.calls.POLL_GITHUB_WORKFLOW"
    private const val PREFS = "nexusai_call_preferences"
    private const val KEY_ENABLED = "github_workflow_monitor_enabled"
    private const val KEY_OWNER = "github_monitor_owner"
    private const val KEY_REPO = "github_monitor_repo"
    private const val KEY_OUTCOME = "github_monitor_outcome"
    private const val KEY_LAST_COMPLETED_ID = "github_monitor_last_completed_id"
    private const val POLL_INTERVAL_MS = 15 * 60 * 1000L
    private const val POLL_REQUEST_CODE = 7281

    fun configureFromMessage(context: Context, text: String): CallScheduleResult {
        val linkedRepo = Regex("""(?:https?://)?github\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)""")
            .find(text)
        val owner = linkedRepo?.groupValues?.get(1) ?: "Haidarthamer"
        val repo = (linkedRepo?.groupValues?.get(2) ?: "HDR.ai").removeSuffix(".git")
        val lower = text.lowercase(Locale.ROOT)
        val requestsSuccess = listOf("success", "succeed", "passed", "succeeded", "نجاح", "نجحت", "نجح").any { lower.contains(it) }
        val requestsFailure = listOf("fail", "failed", "failure", "failing", "فشل", "فاشل", "فشلت").any { lower.contains(it) }
        val outcome = when {
            requestsSuccess && requestsFailure -> "ANY"
            requestsFailure -> "FAILURE"
            requestsSuccess -> "SUCCESS"
            else -> "ANY"
        }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_ENABLED, true)
            .putString(KEY_OWNER, owner)
            .putString(KEY_REPO, repo)
            .putString(KEY_OUTCOME, outcome)
            .putLong(KEY_LAST_COMPLETED_ID, 0L)
            .apply()
        scheduleNextPoll(context, 2_000L)

        val target = "$owner/$repo"
        val outcomeLabel = when (outcome) {
            "SUCCESS" -> "successful"
            "FAILURE" -> "failed"
            else -> "successful or failed"
        }
        return CallScheduleResult(
            CallScheduleResult.Kind.MONITORING,
            message = "GitHub monitoring enabled for public repository $target. NexusAI will notify you when a workflow finishes $outcomeLabel. Checks run about every 15 minutes."
        )
    }

    fun stopMonitoring(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ENABLED, false).apply()
        val intent = Intent(context, GitHubWorkflowMonitorReceiver::class.java).setAction(ACTION_POLL)
        val pendingIntent = PendingIntent.getBroadcast(
            context, POLL_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pendingIntent)
        pendingIntent.cancel()
    }

    fun scheduleNextPoll(context: Context, delayMillis: Long = POLL_INTERVAL_MS) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ENABLED, false)) return
        val intent = Intent(context, GitHubWorkflowMonitorReceiver::class.java).setAction(ACTION_POLL)
        val pendingIntent = PendingIntent.getBroadcast(
            context, POLL_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMillis, pendingIntent)
    }

    internal fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    internal fun restoreAfterReboot(context: Context) {
        scheduleNextPoll(context, 3_000L)
    }

    internal fun pollOnce(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ENABLED, false)) return
        val owner = prefs.getString(KEY_OWNER, "Haidarthamer") ?: "Haidarthamer"
        val repo = prefs.getString(KEY_REPO, "HDR.ai") ?: "HDR.ai"
        val outcome = prefs.getString(KEY_OUTCOME, "ANY") ?: "ANY"
        val url = URL("https://api.github.com/repos/$owner/$repo/actions/runs?per_page=20")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "NexusAI-Android-Call-Monitor")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        try {
            val responseCode = connection.responseCode
            if (responseCode == 401 || responseCode == 403 || responseCode == 404) {
                prefs.edit().putBoolean(KEY_ENABLED, false).apply()
                CallScheduleController.showIncomingCallNotification(
                    context,
                    728199,
                    "GitHub monitor needs access",
                    "Could not read $owner/$repo. This monitor currently supports public repositories only."
                )
                return
            }
            if (responseCode !in 200..299) throw IOException("GitHub returned HTTP $responseCode")
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val runs = json.optJSONArray("workflow_runs")
            if (runs == null) return
            data class Run(val id: Long, val conclusion: String, val name: String)
            val completed = mutableListOf<Run>()
            for (i in 0 until runs.length()) {
                val run = runs.optJSONObject(i) ?: continue
                val id = run.optLong("id", 0L)
                if (id <= 0L || run.optString("status", "") != "completed") continue
                completed += Run(id, run.optString("conclusion", ""), run.optString("name", "GitHub workflow"))
            }
            if (completed.isEmpty()) return
            val newestCompletedId = completed.maxOf { it.id }
            val lastProcessedId = prefs.getLong(KEY_LAST_COMPLETED_ID, 0L)
            if (lastProcessedId == 0L) {
                prefs.edit().putLong(KEY_LAST_COMPLETED_ID, newestCompletedId).apply()
                return
            }
            val newRuns = completed.filter { it.id > lastProcessedId }
            val matching = newRuns.filter { run ->
                outcome == "ANY" ||
                    (outcome == "SUCCESS" && run.conclusion == "success") ||
                    (outcome == "FAILURE" && run.conclusion == "failure")
            }
            prefs.edit().putLong(KEY_LAST_COMPLETED_ID, maxOf(lastProcessedId, newestCompletedId)).apply()
            val latestMatch = matching.maxByOrNull { it.id }
            if (latestMatch != null) {
                val state = latestMatch.conclusion.uppercase(Locale.ROOT)
                val notificationId = (latestMatch.id xor (latestMatch.id ushr 32)).toInt()
                CallScheduleController.showIncomingCallNotification(
                    context,
                    notificationId,
                    "GitHub workflow $state",
                    "$owner/$repo: ${latestMatch.name} finished with $state."
                )
            }
        } finally {
            connection.disconnect()
        }
    }
}

class GitHubWorkflowMonitorReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            GitHubWorkflowCallMonitor.restoreAfterReboot(context)
            return
        }
        if (intent?.action != GitHubWorkflowCallMonitor.ACTION_POLL ||
            !GitHubWorkflowCallMonitor.isEnabled(context)
        ) return
        val async = goAsync()
        Thread {
            try {
                GitHubWorkflowCallMonitor.pollOnce(context.applicationContext)
            } catch (_: Exception) {
                // Temporary network errors are retried on the next polling interval.
            } finally {
                GitHubWorkflowCallMonitor.scheduleNextPoll(context.applicationContext)
                async.finish()
            }
        }.start()
    }
}
