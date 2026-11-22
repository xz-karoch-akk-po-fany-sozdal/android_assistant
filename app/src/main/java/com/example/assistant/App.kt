package com.example.assistant

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import com.example.assistant.core.Assistant
import com.example.assistant.data.History
import com.example.assistant.data.SecretStore
import com.example.assistant.data.Settings
import com.example.assistant.ocr.ScreenOcrService
import com.example.assistant.qa.LocalIntentEngine
import com.example.assistant.service.GestureAccessibilityService
import com.example.assistant.translate.TranslationManager
import com.example.assistant.tts.TtsManager

/**
 * Manual DI: every dependency is created lazily on first use, so a cold process
 * holds almost nothing. (Hilt would add kapt/ksp and generated singletons for no benefit here.)
 *
 * The process also ends itself when no screen is visible and the accessibility service
 * is not connected, so nothing stays resident while the assistant is off.
 */
class App : Application() {

    val settings by lazy { Settings(this) }
    val secrets by lazy { SecretStore(this) }
    val history by lazy { History(this) }
    val tts by lazy { TtsManager(this) }
    val translation by lazy { TranslationManager() }
    val ocr by lazy { ScreenOcrService() }
    val intents by lazy { LocalIntentEngine(this) }
    val assistant by lazy { Assistant(this) }

    private var started = 0
    private val handler = Handler(Looper.getMainLooper())
    private val reaper = Runnable {
        if (started == 0 && GestureAccessibilityService.instance == null && !assistant.busy) {
            Process.killProcess(Process.myPid())
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(a: Activity) { started++; handler.removeCallbacks(reaper) }
            override fun onActivityStopped(a: Activity) {
                started--
                if (started <= 0) scheduleReap()
            }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
    }

    /** Called also by the service after it disables itself. */
    fun scheduleReap() {
        handler.removeCallbacks(reaper)
        handler.postDelayed(reaper, 2500)
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
