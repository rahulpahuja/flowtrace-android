package com.rahulpahuja.flowtrace

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

// 1. The ViewModel holds the business logic and applies the traces
class StockViewModel : ViewModel() {

    private val _connectionState = MutableStateFlow("DISCONNECTED")
    // Trace the StateFlow so you can see whenever UI state changes
    val connectionState = _connectionState.asStateFlow()
        .trace("ConnectionState", logValues = true)

    init {
        simulateConnection()
    }

    // A simulated user action that triggers a flow
    fun trackStockPrice(symbol: String) = flow {
        emit(150.0) // Initial
        delay(500)
        emit(152.5) // Update
        delay(500)
        emit(151.0) // Dip
        delay(500)
        throw Exception("Simulated Network Error")
    }.trace(
        tag = "StockStream-$symbol",
        logValues = true,
        reportEmissions = true // Send to analytics
    )

    private fun simulateConnection() {
        viewModelScope.launch {
            delay(1000)
            _connectionState.value = "CONNECTING..."
            delay(1000)
            _connectionState.value = "CONNECTED"
        }
    }
}

// 2. The Activity initializes the SDK and consumes the flows
class MainActivity : AppCompatActivity() {

    private val viewModel = StockViewModel() // In real app: by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // A. Initialize FlowTrace (Best Practice: Do this in your Application class)
        FlowTrace.init(
            enabled = true,
            showThreads = true,
            customLogger = { tag, msg ->
                // Direct the SDK logs to Android Logcat
                Log.d("FlowTrace-$tag", msg)
            }
        )

        // Optional: Connect to your Analytics SDK (Firebase/Mixpanel)
        FlowTrace.analyticsReporter = { eventName, params ->
            // Analytics.logEvent(eventName, params)
            Log.v("FlowTrace-Analytics", "$eventName: $params")
        }

        setupObservers()
    }

    private fun setupObservers() {
        // B. Observe the traced StateFlow
        // The logs will show: "ConnectionState -> Value: CONNECTING..." etc.
        viewModel.connectionState
            .onEach { state ->
                // updateUI(state)
            }
            .launchIn(lifecycleScope)

        // C. Trigger a specific flow (e.g., on button click)
        // The logs will show emission values and the error at the end
        lifecycleScope.launch {
            try {
                viewModel.trackStockPrice("GOOGL").collect { price ->
                    Log.i("App", "Updated price: $price")
                }
            } catch (e: Exception) {
                // The SDK already logged the error with 🔴 ERROR,
                // but we catch it here to show a Snackbar to the user
                Log.e("App", "UI Error Handler: ${e.message}")
            }
        }
    }
}