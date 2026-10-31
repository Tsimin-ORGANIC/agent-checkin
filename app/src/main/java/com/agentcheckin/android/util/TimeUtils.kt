package com.agentcheckin.android.util

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object TimeUtils {
    val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    fun now(): ZonedDateTime = ZonedDateTime.now(zone)

    fun todayKey(): String = now().toLocalDate().toString()

    /**
     * Qoder's daily window opens at 10:00 Asia/Shanghai. Before 10:00 the
     * currently active window still belongs to the previous calendar day.
     */
    fun qoderWindowKey(): String {
        val current = now()
        val date = if (current.toLocalTime().isBefore(LocalTime.of(10, 0))) {
            current.toLocalDate().minusDays(1)
        } else {
            current.toLocalDate()
        }
        return date.toString()
    }

    fun parseTime(value: String, fallback: String): LocalTime {
        return try {
            LocalTime.parse(value)
        } catch (_: Exception) {
            LocalTime.parse(fallback)
        }
    }

    fun nextDelayMillis(hhmm: String, fallback: String): Long {
        val time = parseTime(hhmm, fallback)
        val current = now()
        var target = current.toLocalDate().atTime(time).atZone(zone)
        if (!target.isAfter(current)) {
            target = target.plusDays(1)
        }
        return Duration.between(current, target).toMillis().coerceAtLeast(0L)
    }

    fun hasPassed(hhmm: String, fallback: String): Boolean {
        return !now().toLocalTime().isBefore(parseTime(hhmm, fallback))
    }

    fun formatDateTime(epochMs: Long): String {
        return DateTimeFormatter.ofPattern("MM-dd HH:mm")
            .withZone(zone)
            .format(Instant.ofEpochMilli(epochMs))
    }
}
