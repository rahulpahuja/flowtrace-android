package com.rahulpahuja.flowtrace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlin.coroutines.cancellation.CancellationException

/**
 * ============================================================================================
 * FLOWTRACE SDK (Refactored & Fixed)
 * A lightweight, drop-in SDK for inspecting Kotlin Coroutine Flows.
 * ============================================================================================
 */
object FlowTrace {
    @Volatile var isEnabled: Boolean = true
    @Volatile var showThreadInfo: Boolean = true

    // Logger generic definition: (Tag, Message) -> Unit
    // Defaults to standard println for pure Kotlin compatibility
    var logger: (tag: String, message: String) -> Unit = { tag, msg ->
        println("FlowTrace-$tag: $msg")
    }

    // Analytics Hook
    var analyticsReporter: ((eventName: String, params: Map<String, Any>) -> Unit)? = null

    /**
     * Initialize the SDK.
     * Call this in your Application class.
     *
     * Example for Android:
     * FlowTrace.init(customLogger = { tag, msg -> Log.d("FlowTrace-$tag", msg) })
     */
    fun init(
        enabled: Boolean = true,
        showThreads: Boolean = true,
        customLogger: ((String, String) -> Unit)? = null
    ) {
        this.isEnabled = enabled
        this.showThreadInfo = showThreads
        if (customLogger != null) {
            this.logger = customLogger
        }
    }

    /**
     * Resets defaults. Useful for unit tests cleanup.
     */
    fun resetDefaults() {
        isEnabled = true
        showThreadInfo = true
        logger = { tag, msg -> println("FlowTrace-$tag: $msg") }
        analyticsReporter = null
    }
}

/**
 * The main entry point. Wraps the upstream flow to intercept events.
 */
fun <T> Flow<T>.trace(
    tag: String,
    logValues: Boolean = true,
    reportEmissions: Boolean = false
): Flow<T> {
    // Fast-path: Return original flow if disabled to avoid object allocation overhead
    if (!FlowTrace.isEnabled) return this

    val upstream = this

    // We wrap the collection in a new flow builder.
    // This guarantees that 'startTime' is unique for EVERY collection (subscriber).
    return flow {
        val startTime = System.currentTimeMillis()

        // 1. Trace Start
        internalLog(tag, "🟢 START")
        internalReport(tag, "flow_trace_start", mapOf("type" to "start"))

        // Hot Flow Introspection
        if (upstream is StateFlow<*>) {
            internalLog(tag, "ℹ️ StateFlow Info", "Current Value: ${upstream.value}")
        } else if (upstream is SharedFlow<*>) {
            internalLog(tag, "ℹ️ SharedFlow Info", "ReplayCache Size: ${upstream.replayCache.size}")
        }

        // 2. Collect and Intercept
        try {
            upstream
                .onEach { value ->
                    val elapsed = System.currentTimeMillis() - startTime
                    val content = if (logValues) "Value: $value" else "Value: [HIDDEN]"
                    internalLog(tag, "⬇️ EMIT [+${elapsed}ms]", content)

                    if (reportEmissions) {
                        internalReport(tag, "flow_trace_emit", mapOf(
                            "elapsed_ms" to elapsed,
                            "value" to (if (logValues) "$value" else "[HIDDEN]")
                        ))
                    }
                }
                .catch { e ->
                    val elapsed = System.currentTimeMillis() - startTime

                    // Don't log cancellation exceptions in the error handler
                    if (e !is CancellationException) {
                        internalLog(tag, "🔴 ERROR [+${elapsed}ms]", "Exception: ${e.message}")
                        internalReport(tag, "flow_trace_error", mapOf(
                            "elapsed_ms" to elapsed,
                            "error_message" to (e.message ?: "Unknown Error"),
                            "exception_class" to (e::class.simpleName ?: "Unknown")
                        ))
                    }
                    throw e
                }
                .onCompletion { cause ->
                    val elapsed = System.currentTimeMillis() - startTime

                    if (cause is CancellationException) {
                        internalLog(tag, "🚫 CANCELLED [+${elapsed}ms]", "Reason: ${cause.message}")
                        internalReport(tag, "flow_trace_cancel", mapOf("elapsed_ms" to elapsed))
                    } else if (cause == null) {
                        internalLog(tag, "🏁 COMPLETE [+${elapsed}ms]", "Finished successfully")
                        internalReport(tag, "flow_trace_complete", mapOf("elapsed_ms" to elapsed))
                    }
                    // Non-cancellation exceptions already logged by .catch()
                }
                .collect { value ->
                    emit(value)
                }
        } catch (e: CancellationException) {
            // Re-throw cancellation to properly propagate it
            throw e
        } catch (e: Exception) {
            // All other exceptions are already handled and logged
            throw e
        }
    }
}

/**
 * Helper to watch a flow in a scope (e.g. ViewModel) without manual .collect()
 */
fun <T> Flow<T>.watchIn(scope: CoroutineScope, tag: String): Job {
    return this.trace(tag).launchIn(scope)
}

// --- Internal Helpers ---

private fun internalLog(tag: String, event: String, detail: String? = null) {
    if (!FlowTrace.isEnabled) return

    val threadInfo = if (FlowTrace.showThreadInfo) " [T: ${Thread.currentThread().name}]" else ""
    val message = buildString {
        append(event)
        if (detail != null) append(" -> $detail")
        append(threadInfo)
    }
    FlowTrace.logger(tag, message)
}

private fun internalReport(tag: String, eventName: String, data: Map<String, Any>) {
    FlowTrace.analyticsReporter?.invoke(eventName, mapOf("flow_tag" to tag) + data)
}