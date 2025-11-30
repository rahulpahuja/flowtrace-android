FlowTracer 🌊

A lightweight, drop-in SDK for inspecting, debugging, and monitoring Kotlin Coroutine Flows.

FlowTracer eliminates the guesswork when debugging reactive streams. It wraps your existing Flows to provide detailed lifecycle logging, emission timing, thread information, and error tracking without cluttering your logic with println statements.

🚀 Key Features

Lifecycle Awareness: Logs Start, Emit, Error, Cancellation, and Completion events.

Timing: automatically calculates the elapsed time from subscription to emission/completion (e.g., [+150ms]).

Hot Flow Support: Instantly inspects StateFlow current values and SharedFlow replay cache sizes upon collection.

Thread Info: See exactly which thread/dispatcher your flow is operating on.

Analytics Hooks: Pipe flow events to your analytics provider (Firebase, Segment, etc.) to debug production issues.

Zero Dependencies: Pure Kotlin. Works on Android, Backend, and Multiplatform.

📦 Installation

Option 1: Copy the File (Easiest)

Since FlowTrace is a single file, you can simply copy FlowTrace.kt into your project package com.rahulpahuja.flowtracer.

Option 2: JitPack (If you host this repo)

Add the repository to your build file:

repositories {
    maven { url = uri("[https://jitpack.io](https://jitpack.io)") }
}

dependencies {
    implementation("com.github.YourUsername:FlowTracer:Tag")
}


⚡ Usage

1. Initialization (Android Example)

Initialize the SDK in your Application class or main entry point. You can pipe logs to Logcat instead of standard output.

// Android Application class
FlowTrace.init(
    enabled = BuildConfig.DEBUG, // Disable in release automatically
    showThreads = true,
    customLogger = { tag, msg -> 
        Log.d("FlowTrace-$tag", msg) 
    }
)


2. Basic Tracing

Simply add .trace("Tag") to any flow chain.

userRepository.getUserStream()
    .map { it.toUiModel() }
    .trace("UserFlow") // <--- Add this line
    .onEach { updateUi(it) }
    .launchIn(viewModelScope)


Output:

FlowTrace-UserFlow: 🟢 START [T: main]
FlowTrace-UserFlow: ⬇️ EMIT [+24ms] -> Value: UiUser(name=Rahul) [T: main]
FlowTrace-UserFlow: 🏁 COMPLETE [+100ms] -> Finished successfully [T: main]


3. Watching without Collecting

If you just want to keep a stream active in a scope and debug it (without manually writing .launchIn), use watchIn.

// In a ViewModel
someHotFlow.watchIn(viewModelScope, "HotStreamWatcher")


4. Advanced: Analytics Integration

You can route flow events to an analytics backend to track flow health in production.

FlowTrace.analyticsReporter = { eventName, params ->
    // Example: Firebase Analytics
    FirebaseAnalytics.getInstance(context).logEvent(eventName, params.toBundle())
}

// Enable reporting for specific critical flows
importantFlow.trace(
    tag = "CriticalData", 
    reportEmissions = true
).launchIn(scope)


🔍 Log Format Guide

Icon

Meaning

Description

🟢

START

The flow has been collected.

ℹ️

INFO

Stats for StateFlow (Current Value) or SharedFlow (Replay Cache).

⬇️

EMIT

A value was emitted. Shows time elapsed since start.

🔴

ERROR

The flow threw an exception.

🚫

CANCEL

The flow was cancelled (e.g., Scope died).

🏁

COMPLETE

The flow finished successfully.

📄 License

Copyright 2024 Rahul Pahuja

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 2.1 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
