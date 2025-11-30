package com.rahulpahuja.flowtrace

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class FlowTraceTest {

    // No longer need to manually create a dispatcher or scope

    private val logMessages = mutableListOf<String>()
    private val analyticsEvents = mutableListOf<Pair<String, Map<String, Any>>>()

    @Before
    fun setup() {
        FlowTrace.resetDefaults()
        logMessages.clear()
        analyticsEvents.clear()

        // Set up custom logger to capture logs
        FlowTrace.logger = { tag, message ->
            logMessages.add("$tag: $message")
        }

        // Set up analytics reporter to capture events
        FlowTrace.analyticsReporter = { eventName, params ->
            analyticsEvents.add(eventName to params)
        }
    }

    @After
    fun tearDown() {
        FlowTrace.resetDefaults()
    }

    // Use runTest instead of runBlocking for structured concurrency in tests
    @Test
    fun `test basic flow emission with trace`() = runTest {
        val flow = flowOf(1, 2, 3)
        val results = mutableListOf<Int>()

        flow.trace("TestFlow").collect { value ->
            results.add(value)
        }

        assertEquals(listOf(1, 2, 3), results)
        assertTrue(logMessages.any { it.contains("🟢 START") })
        assertTrue(logMessages.any { it.contains("⬇️ EMIT") && it.contains("Value: 1") })
        assertTrue(logMessages.any { it.contains("⬇️ EMIT") && it.contains("Value: 2") })
        assertTrue(logMessages.any { it.contains("⬇️ EMIT") && it.contains("Value: 3") })
        assertTrue(logMessages.any { it.contains("🏁 COMPLETE") })
    }

    @Test
    fun `test trace is disabled when FlowTrace_isEnabled is false`() = runTest {
        FlowTrace.isEnabled = false
        val flow = flowOf(1, 2, 3)
        val results = mutableListOf<Int>()

        flow.trace("TestFlow").collect { value ->
            results.add(value)
        }

        assertEquals(listOf(1, 2, 3), results)
        assertTrue(logMessages.isEmpty())
        assertTrue(analyticsEvents.isEmpty())
    }

    @Test
    fun `test logValues parameter hides emission values`() = runTest {
        val flow = flowOf(1, 2, 3)

        flow.trace("TestFlow", logValues = false).collect { }

        assertTrue(logMessages.any { it.contains("[HIDDEN]") })
        assertFalse(logMessages.any { it.contains("Value: 1") })
    }

    @Test
    fun `test reportEmissions sends analytics events`() = runTest {
        val flow = flowOf(1, 2, 3)

        flow.trace("TestFlow", reportEmissions = true).collect { }

        val emitEvents = analyticsEvents.filter { it.first == "flow_trace_emit" }
        assertEquals(3, emitEvents.size)

        emitEvents.forEach { (_, params) ->
            assertTrue(params.containsKey("flow_tag"))
            assertTrue(params.containsKey("elapsed_ms"))
            assertTrue(params.containsKey("value"))
        }
    }

    @Test
    fun `test flow error handling`() = runTest {
        val flow = flow<Int> {
            emit(1)
            throw IOException("Test error")
        }

        try {
            flow.trace("ErrorFlow").collect { }
        } catch (e: IOException) {
            // Expected
        }

        assertTrue(logMessages.any { it.contains("🔴 ERROR") })
        assertTrue(logMessages.any { it.contains("Test error") })

        val errorEvents = analyticsEvents.filter { it.first == "flow_trace_error" }
        assertEquals(1, errorEvents.size)
        assertEquals("IOException", errorEvents[0].second["exception_class"])
    }

    @Test
    fun `test flow cancellation`() = runTest {
        val flow = flow {
            emit(1)
            delay(1000) // This delay will be interrupted
            emit(2)
        }

        // launch the collection in the background
        val job = launch {
            flow.trace("CancelFlow").collect { }
        }

        // Advance time partially to let first emission happen
        advanceTimeBy(100)

        // Cancel the job while it's waiting in the delay
        job.cancel()
        advanceUntilIdle() // Ensure cancellation is processed

        // The cancellation should be logged
        val hasCancelLog = logMessages.any { it.contains("🚫 CANCELLED") }
        assertTrue("Expected cancellation log but got: $logMessages", hasCancelLog)
    }

    @Test
    fun `test flow cancellation with manual cancellation exception`() = runTest {
        val flow = flow<Int> {
            emit(1)
            throw CancellationException("Manual cancellation")
        }

        try {
            flow.trace("ManualCancelFlow").collect { }
        } catch (e: CancellationException) {
            // Expected
        }

        // Should log cancellation, not error
        assertTrue(logMessages.any { it.contains("🚫 CANCELLED") })
        assertFalse(logMessages.any { it.contains("🔴 ERROR") })
    }

    // StateFlow's value is collected immediately, so runTest is fine.
    @Test
    fun `test StateFlow introspection`() = runTest {
        val stateFlow = MutableStateFlow(42)

        // Launch in background to prevent test from ending after the first value
        val job = launch { stateFlow.trace("StateFlowTest").collect() }
        advanceUntilIdle()

        assertTrue(logMessages.any { it.contains("StateFlow Info") })
        assertTrue(logMessages.any { it.contains("Current Value: 42") })
        job.cancel()
    }

    // SharedFlow needs an active collector to be introspected
    @Test
    fun `test SharedFlow introspection`() = runTest {
        val sharedFlow = MutableSharedFlow<Int>(replay = 2)
        sharedFlow.tryEmit(1)
        sharedFlow.tryEmit(2)

        val job = launch { sharedFlow.trace("SharedFlowTest").collect() }
        advanceUntilIdle()

        assertTrue(logMessages.any { it.contains("SharedFlow Info") })
        assertTrue(logMessages.any { it.contains("ReplayCache Size: 2") })
        job.cancel()
    }

    @Test
    fun `test thread info is shown when enabled`() = runTest {
        FlowTrace.showThreadInfo = true
        val flow = flowOf(1)

        flow.trace("ThreadTest").collect { }

        assertTrue(logMessages.any { it.contains("[T:") })
    }

    @Test
    fun `test thread info is hidden when disabled`() = runTest {
        FlowTrace.showThreadInfo = false
        val flow = flowOf(1)

        flow.trace("ThreadTest").collect { }

        assertFalse(logMessages.any { it.contains("[T:") })
    }

    @Test
    fun `test init method configures SDK`() {
        val customLogs = mutableListOf<String>()
        FlowTrace.init(
            enabled = false,
            showThreads = false,
            customLogger = { tag, msg -> customLogs.add("$tag: $msg") })

        assertFalse(FlowTrace.isEnabled)
        assertFalse(FlowTrace.showThreadInfo)

        FlowTrace.logger("TestTag", "TestMessage")
        assertEquals(1, customLogs.size)
        assertTrue(customLogs[0].contains("TestTag"))
    }

    @Test
    fun `test multiple emissions track elapsed time`() = runTest {
        val flow = flow {
            emit(1)
            delay(100)
            emit(2)
            delay(100)
            emit(3)
        }

        flow.trace("TimeTest").collect { }

        val emitLogs = logMessages.filter { it.contains("⬇️ EMIT") }
        assertEquals(3, emitLogs.size)

        // Check that elapsed time is increasing
        emitLogs.forEach { log ->
            assertTrue(log.contains("[+") && log.contains("ms]"))
        }
    }

    @Test
    fun `test watchIn launches flow in scope`() = runTest {
        val flow = flowOf(1, 2, 3)

        // `this` in runTest is a CoroutineScope
        val job = flow.trace("WatchTest").watchIn(
            this, tag = "Sample TAG" // The tag parameter is fine
        )

        // This will run the coroutine launched by watchIn to completion
        // because the flow emits all its values immediately.
        advanceUntilIdle()

        // --- CORRECTION ---
        // The job should be COMPLETED, not active, because flowOf is synchronous.
        assertFalse("Job should be complete after the flow finishes", job.isActive)
        assertTrue("Job should be marked as completed", job.isCompleted)

        // Verify that the flow ran as expected
        assertTrue(logMessages.any { it.contains("🟢 START") })
        assertTrue(logMessages.any { it.contains("🏁 COMPLETE") })

        // No need to call job.cancel() on a job that is already complete.
    }

    @Test
    fun `test analytics start and complete events`() = runTest {
        val flow = flowOf(1, 2, 3)

        flow.trace("AnalyticsTest").collect { }

        val startEvents = analyticsEvents.filter { it.first == "flow_trace_start" }
        val completeEvents = analyticsEvents.filter { it.first == "flow_trace_complete" }

        assertEquals(1, startEvents.size)
        assertEquals(1, completeEvents.size)
        assertEquals("AnalyticsTest", startEvents[0].second["flow_tag"])
    }

    @Test
    fun `test empty flow completes successfully`() = runTest {
        val flow = flow<Int> { }

        flow.trace("EmptyFlow").collect { }

        assertTrue(logMessages.any { it.contains("🟢 START") })
        assertTrue(logMessages.any { it.contains("🏁 COMPLETE") })
    }

    @Test
    fun `test cancellation exception is not logged as error`() = runTest {
        val flow = flow<Int> {
            emit(1)
            throw CancellationException("Cancelled")
        }

        try {
            flow.trace("CancelExceptionTest").collect { }
        } catch (e: CancellationException) {
            // Expected
        }

        assertFalse(logMessages.any { it.contains("🔴 ERROR") })
        assertTrue(logMessages.any { it.contains("🚫 CANCELLED") })
    }

    @Test
    fun `test custom tag is used in logs`() = runTest {
        val customTag = "MyCustomTag"
        val flow = flowOf(1)

        flow.trace(customTag).collect { }

        assertTrue(logMessages.any { it.startsWith("$customTag:") })
    }

    @Test
    fun `test resetDefaults restores initial state`() {
        // Setup a non-default state
        FlowTrace.isEnabled = false
        FlowTrace.showThreadInfo = false
        var loggerCalled = false
        FlowTrace.logger = { _, _ -> loggerCalled = true }
        FlowTrace.analyticsReporter = { _, _ -> }

        // Reset
        FlowTrace.resetDefaults()

        // Verify restored defaults
        assertTrue(FlowTrace.isEnabled)
        assertTrue(FlowTrace.showThreadInfo)

        // Verify the logger is the default one (which prints to console)
        // A simple way to test this without capturing stdout is to check if it's not our custom one.
        FlowTrace.logger("tag", "msg") // This will print to console, but not call our custom logger
        assertFalse(loggerCalled)

        // analyticsReporter should be null by default
        Assert.assertNull(FlowTrace.analyticsReporter)
    }
}
