package com.mgeni.autologin.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.mgeni.autologin.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentLinkedQueue

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null
) {
    fun format(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val timeStr = dateFormat.format(Date(timestamp))
        val levelStr = level.name.padEnd(5)
        val tagStr = "[$tag]".padEnd(20)
        val errStr = throwable?.let { "\n   Stack trace: ${it.stackTraceToString()}" }.orEmpty()
        return "$timeStr $levelStr $tagStr $message$errStr"
    }
}

/**
 * Centralized, thread-safe logger for Captive Portal and Wi-Fi automation telemetry.
 * Captures all HTTP requests/responses, system telemetry, network events, and user interactions.
 * Persists everything to a persistent .log file until explicitly cleared.
 */
object AppLogger {

    private const val PERSISTENT_LOG_FILE_NAME = "portal_debug.log"
    private const val ANDROID_LOG_TAG = "WifiAuto"
    private const val MAX_IN_MEMORY_ENTRIES = 2000
    private const val MAX_LOG_FILE_BYTES = 2 * 1024 * 1024L // 2 MB cap

    private val PASSWORD_PATTERN = Regex("(?i)(password=)([^&\\s,]+)")

    private val inMemoryLogQueue = ConcurrentLinkedQueue<LogEntry>()
    private val fileLock = Any()
    private var appContext: Context? = null

    private val _logCount = MutableStateFlow(0)
    val logCount: StateFlow<Int> = _logCount.asStateFlow()

    fun sanitize(input: String): String {
        return PASSWORD_PATTERN.replace(input, "$1[REDACTED]")
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        val totalMemoryMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
        val tz = TimeZone.getDefault().id

        val systemHeader = buildString {
            appendLine("================================================================================")
            appendLine("=== WifiAuto Application Started ===")
            appendLine("Timestamp:    ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())} ($tz)")
            appendLine("App Version:  v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})")
            appendLine("Device:       ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("OS Release:   Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Heap Limit:   ${totalMemoryMb} MB")
            appendLine("Locale:       ${Locale.getDefault()}")
            appendLine("================================================================================")
        }

        i("SYS_INIT", systemHeader)
    }

    fun d(tag: String, message: String) = addEntry(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = addEntry(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String, throwable: Throwable? = null) = addEntry(LogLevel.WARN, tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = addEntry(LogLevel.ERROR, tag, message, throwable)

    fun logHttp(direction: String, url: String, details: String) {
        val msg = "$direction $url\n${sanitize(details)}"
        i("PORTAL_HTTP", msg)
    }

    private fun addEntry(level: LogLevel, tag: String, rawMessage: String, throwable: Throwable? = null) {
        val sanitizedMessage = sanitize(rawMessage)
        val entry = LogEntry(
            level = level,
            tag = tag,
            message = sanitizedMessage,
            throwable = throwable
        )

        // Logcat output
        try {
            when (level) {
                LogLevel.DEBUG -> Log.d(ANDROID_LOG_TAG, "[$tag] $sanitizedMessage", throwable)
                LogLevel.INFO -> Log.i(ANDROID_LOG_TAG, "[$tag] $sanitizedMessage", throwable)
                LogLevel.WARN -> Log.w(ANDROID_LOG_TAG, "[$tag] $sanitizedMessage", throwable)
                LogLevel.ERROR -> Log.e(ANDROID_LOG_TAG, "[$tag] $sanitizedMessage", throwable)
            }
        } catch (_: Exception) {
            // Unmocked Android Log in JVM test environment
        }

        inMemoryLogQueue.add(entry)
        while (inMemoryLogQueue.size > MAX_IN_MEMORY_ENTRIES) {
            inMemoryLogQueue.poll()
        }
        _logCount.value = inMemoryLogQueue.size

        // Persist to local log file thread-safely with 2MB FIFO capping
        synchronized(fileLock) {
            appContext?.let { context ->
                try {
                    val file = getPersistentLogFile(context)
                    if (file.exists() && file.length() > MAX_LOG_FILE_BYTES) {
                        trimLogFile(file)
                    }
                    FileWriter(file, true).use { writer ->
                        writer.write(entry.format() + "\n")
                    }
                } catch (e: Exception) {
                    inMemoryLogQueue.add(
                        LogEntry(level = LogLevel.ERROR, tag = "APP_LOGGER", message = "Failed to write log entry to disk file: ${e.localizedMessage}", throwable = e)
                    )
                }
            }
        }
    }

    private fun trimLogFile(file: File) {
        try {
            val lines = file.readLines()
            val keepLines = lines.takeLast(lines.size / 2)
            file.writeText(keepLines.joinToString("\n") + "\n")
        } catch (e: Exception) {
            inMemoryLogQueue.add(
                LogEntry(level = LogLevel.WARN, tag = "APP_LOGGER", message = "Failed to trim log file: ${e.localizedMessage}", throwable = e)
            )
        }
    }

    fun getPersistentLogFile(context: Context): File {
        val logsDir = File(context.filesDir, "logs")
        if (!logsDir.exists()) {
            logsDir.mkdirs()
        }
        return File(logsDir, PERSISTENT_LOG_FILE_NAME)
    }

    fun getFormattedLogs(): String {
        val header = "=== WifiAuto Diagnostic Logs ===\n" +
            "Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n" +
            "Total In-Memory Entries: ${inMemoryLogQueue.size}\n" +
            "================================\n\n"

        val body = inMemoryLogQueue.joinToString("\n") { it.format() }
        return header + body
    }

    /**
     * Prepares a timestamped .log file for export via Android Share sheet.
     */
    fun prepareExportFile(context: Context): File {
        val exportDir = File(context.cacheDir, "logs")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val exportFile = File(exportDir, "wifiauto_diagnostic_$timestamp.log")

        synchronized(fileLock) {
            try {
                val persistentFile = getPersistentLogFile(context)
                if (persistentFile.exists() && persistentFile.length() > 0) {
                    persistentFile.copyTo(exportFile, overwrite = true)
                } else {
                    exportFile.writeText(getFormattedLogs())
                }
            } catch (e: Exception) {
                inMemoryLogQueue.add(
                    LogEntry(level = LogLevel.WARN, tag = "APP_LOGGER", message = "Failed to copy persistent log file for export: ${e.localizedMessage}", throwable = e)
                )
                try {
                    exportFile.writeText(getFormattedLogs())
                } catch (writeErr: Exception) {
                    inMemoryLogQueue.add(
                        LogEntry(level = LogLevel.ERROR, tag = "APP_LOGGER", message = "Failed to write formatted logs during export: ${writeErr.localizedMessage}", throwable = writeErr)
                    )
                }
            }
        }

        return exportFile
    }

    fun clearLogs(context: Context? = null) {
        synchronized(fileLock) {
            inMemoryLogQueue.clear()
            _logCount.value = 0
            val ctx = context ?: appContext
            ctx?.let {
                try {
                    val persistentFile = getPersistentLogFile(it)
                    if (persistentFile.exists()) {
                        persistentFile.delete()
                    }
                    val exportDir = File(it.cacheDir, "logs")
                    if (exportDir.exists()) {
                        exportDir.deleteRecursively()
                    }
                } catch (e: Exception) {
                    inMemoryLogQueue.add(
                        LogEntry(level = LogLevel.WARN, tag = "APP_LOGGER", message = "Failed to delete log files on clear: ${e.localizedMessage}", throwable = e)
                    )
                }
            }
        }
    }
}
