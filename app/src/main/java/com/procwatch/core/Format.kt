package com.procwatch.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object Format {

    fun kb(value: Long?): String {
        if (value == null || value <= 0) return "—"
        val mb = value / 1024.0
        return if (mb >= 1024) String.format(Locale.US, "%.2f GB", mb / 1024.0)
        else String.format(Locale.US, "%.0f MB", mb)
    }

    fun bytes(value: Long?): String {
        if (value == null || value <= 0) return "—"
        val kb = value / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1024 -> String.format(Locale.US, "%.2f GB", mb / 1024.0)
            mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
            else -> String.format(Locale.US, "%.0f KB", kb)
        }
    }

    /** Screen time: "1h 42m", "8m", "—". */
    fun duration(ms: Long?): String {
        if (ms == null || ms <= 0) return "—"
        val totalMin = ms / 60_000
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m"
            else -> "<1m"
        }
    }

    /** "3h ago", "2d ago", "never". */
    fun relativeTime(epochMs: Long?, now: Long = System.currentTimeMillis()): String {
        if (epochMs == null || epochMs <= 0) return "never"
        val diff = abs(now - epochMs)
        val min = diff / 60_000
        val hour = min / 60
        val day = hour / 24
        return when {
            min < 1 -> "just now"
            min < 60 -> "${min}m ago"
            hour < 24 -> "${hour}h ago"
            day < 30 -> "${day}d ago"
            else -> "${day / 30}mo ago"
        }
    }

    private val stamp = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US)
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun dateTime(epochMs: Long): String = stamp.format(Date(epochMs))
    fun timeOnly(epochMs: Long): String = clock.format(Date(epochMs))
}
