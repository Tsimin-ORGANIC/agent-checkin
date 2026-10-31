package com.agentcheckin.android.data

import android.content.Context
import com.agentcheckin.android.util.Redact
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class AppSettings(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("agent_checkin_settings", Context.MODE_PRIVATE)

    var autoCheckin: Boolean
        get() = prefs.getBoolean(KEY_AUTO, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO, value).apply()

    var workBuddyTime: String
        get() = prefs.getString(KEY_WB_TIME, "09:00") ?: "09:00"
        set(value) = prefs.edit().putString(KEY_WB_TIME, value).apply()

    var qoderTime: String
        get() = prefs.getString(KEY_QODER_TIME, "10:05") ?: "10:05"
        set(value) = prefs.edit().putString(KEY_QODER_TIME, value).apply()

    var disclaimerAccepted: Boolean
        get() = prefs.getBoolean(KEY_DISCLAIMER, false)
        set(value) = prefs.edit().putBoolean(KEY_DISCLAIMER, value).apply()

    var qoderMachineId: String
        get() {
            val existing = prefs.getString(KEY_QODER_MACHINE_ID, null)
            if (!existing.isNullOrBlank()) return existing
            val generated = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_QODER_MACHINE_ID, generated).apply()
            return generated
        }
        set(value) = prefs.edit().putString(KEY_QODER_MACHINE_ID, value).apply()

    fun timeFor(platform: Platform): String {
        return if (platform == Platform.WORKBUDDY) workBuddyTime else qoderTime
    }

    fun setTimeFor(platform: Platform, value: String) {
        if (platform == Platform.WORKBUDDY) workBuddyTime = value else qoderTime = value
    }

    fun lastSuccessDay(platform: Platform): String? {
        return prefs.getString(KEY_LAST_SUCCESS_PREFIX + platform.storageKey, null)
    }

    fun setLastSuccessDay(platform: Platform, day: String) {
        prefs.edit().putString(KEY_LAST_SUCCESS_PREFIX + platform.storageKey, day).apply()
    }

    fun lastMessage(platform: Platform): String? {
        return prefs.getString(KEY_LAST_MESSAGE_PREFIX + platform.storageKey, null)
    }

    fun setLastMessage(platform: Platform, message: String) {
        prefs.edit()
            .putString(KEY_LAST_MESSAGE_PREFIX + platform.storageKey, Redact.text(message))
            .apply()
    }

    fun lastRunAt(platform: Platform): Long {
        return prefs.getLong(KEY_LAST_RUN_PREFIX + platform.storageKey, 0L)
    }

    fun setLastRunAt(platform: Platform, time: Long) {
        prefs.edit().putLong(KEY_LAST_RUN_PREFIX + platform.storageKey, time).apply()
    }

    fun addLog(entry: LogEntry) {
        val current = logs().toMutableList()
        current.add(
            0,
            entry.copy(message = Redact.text(entry.message, maxLength = 240))
        )
        while (current.size > MAX_LOGS) {
            current.removeAt(current.lastIndex)
        }
        val array = JSONArray()
        current.forEach { item ->
            array.put(
                JSONObject()
                    .put("time", item.time)
                    .put("platform", item.platform.name)
                    .put("success", item.success)
                    .put("message", item.message)
            )
        }
        prefs.edit().putString(KEY_LOGS, array.toString()).apply()
    }

    fun logs(): List<LogEntry> {
        val raw = prefs.getString(KEY_LOGS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val platform = runCatching {
                    Platform.valueOf(item.optString("platform"))
                }.getOrNull() ?: return@mapNotNull null
                LogEntry(
                    time = item.optLong("time"),
                    platform = platform,
                    success = item.optBoolean("success"),
                    message = item.optString("message")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun clearLogs() {
        prefs.edit().remove(KEY_LOGS).apply()
    }

    companion object {
        private const val KEY_AUTO = "auto_checkin"
        private const val KEY_WB_TIME = "wb_time"
        private const val KEY_QODER_TIME = "qoder_time"
        private const val KEY_DISCLAIMER = "disclaimer_accepted"
        private const val KEY_QODER_MACHINE_ID = "qoder_machine_id"
        private const val KEY_LAST_SUCCESS_PREFIX = "last_success_"
        private const val KEY_LAST_MESSAGE_PREFIX = "last_message_"
        private const val KEY_LAST_RUN_PREFIX = "last_run_"
        private const val KEY_LOGS = "logs"
        private const val MAX_LOGS = 50
    }
}
