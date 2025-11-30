
---

# FlowTracer 🌊

A lightweight, drop-in SDK for inspecting, debugging, and monitoring Kotlin Coroutine Flows.

FlowTracer removes the guesswork when working with reactive streams. It wraps your existing Flows and reveals detailed lifecycle logs, emission timing, threading info, and error traces—without cluttering your code with `println()`.

---

## 🚀 Features

* **Lifecycle Awareness** – Logs **Start**, **Emit**, **Error**, **Cancellation**, and **Completion**.
* **Timing Metrics** – Automatically shows elapsed time since subscription (e.g., `+150ms`).
* **Hot Flow Support** – Instantly inspects:

  * `StateFlow`: current value
  * `SharedFlow`: replay cache size
* **Thread Visibility** – Displays the dispatcher/thread handling your flow.
* **Analytics Hooks** – Send flow events to Firebase, Segment, or any analytics backend.
* **Zero Dependencies** – Pure Kotlin. Works on **Android**, **Backend**, and **Multiplatform**.
* **Single File** – Drop it directly into your project.

---

## 📦 Installation

### **Option 1: Direct Copy (Recommended & Easiest)**

Simply copy **`FlowTrace.kt`** into:

```
com.rahulpahuja.flowtracer
```

### **Option 2: JitPack (If publishing the repo)**

```gradle
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.YourUsername:FlowTracer:Tag")
}
```

---

## ⚡ Usage

### **1. Initialization (Android Example)**

```kotlin
// In Application class
FlowTrace.init(
    enabled = BuildConfig.DEBUG,   // Auto-disable in release
    showThreads = true,
    customLogger = { tag, msg ->
        Log.d("FlowTrace-$tag", msg)
    }
)
```

---

### **2. Basic Tracing**

Add `.trace("Tag")` to any flow:

```kotlin
userRepository.getUserStream()
    .map { it.toUiModel() }
    .trace("UserFlow")
    .onEach { updateUi(it) }
    .launchIn(viewModelScope)
```

**Output Example:**

```
FlowTrace-UserFlow: 🟢 START [T: main]
FlowTrace-UserFlow: ⬇️ EMIT [+24ms] -> Value: UiUser(name=Rahul) [T: main]
FlowTrace-UserFlow: 🏁 COMPLETE [+100ms] -> Finished successfully [T: main]
```

---

### **3. Watching Without Collecting**

Use `watchIn()` to observe a hot flow without manually writing `.launchIn()`:

```kotlin
someHotFlow.watchIn(viewModelScope, "HotStreamWatcher")
```

---

### **4. Analytics Integration**

Pipe FlowTracer events to your analytics provider:

```kotlin
FlowTrace.analyticsReporter = { eventName, params ->
    FirebaseAnalytics.getInstance(context)
        .logEvent(eventName, params.toBundle())
}
```

Enable emission reporting for critical flows:

```kotlin
importantFlow.trace(
    tag = "CriticalData",
    reportEmissions = true
).launchIn(scope)
```

---

## 🔍 Log Format

| Icon | Type     | Meaning                   |
| ---- | -------- | ------------------------- |
| 🟢   | START    | Flow collection started   |
| ℹ️   | INFO     | Hot flow stats logged     |
| ⬇️   | EMIT     | Value emitted with timing |
| 🔴   | ERROR    | Flow threw an exception   |
| 🚫   | CANCEL   | Flow was cancelled        |
| 🏁   | COMPLETE | Flow completed normally   |

---

## 📄 License

```
Copyright 2024 Rahul Pahuja

This library is free software; you can redistribute it and/or modify it 
under the terms of the GNU Lesser General Public License as published 
by the Free Software Foundation; either version 2.1 of the License, or 
(at your option) any later version.

This library is distributed in the hope that it will be useful, but 
WITHOUT ANY WARRANTY; without even the implied warranty of 
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU 
Lesser General Public License for more details.
```

---
