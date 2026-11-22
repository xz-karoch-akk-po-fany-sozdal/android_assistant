package com.example.assistant.service

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.example.assistant.App
import com.example.assistant.MainActivity
import com.example.assistant.core.Assistant
import com.example.assistant.data.CornerSide
import com.example.assistant.ui.AssistantOverlay
import com.example.assistant.ui.OverlayActions
import com.example.assistant.ui.OverlayState
import com.example.assistant.ui.Phase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Entry point of the assistant. While it is enabled it holds only two tiny transparent
 * touch zones; the Compose card, the Vosk model and the OCR/translation models exist
 * only while the assistant is being used.
 */
class GestureAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: GestureAccessibilityService? = null
    }

    private val app get() = App.instance
    private val assistant: Assistant get() = app.assistant
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var wm: WindowManager
    private val zones = mutableListOf<View>()
    private var overlay: ComposeView? = null
    private var owner: OverlayOwner? = null
    private val state = OverlayState()
    private var longFired = false
    private val longPress = Runnable {
        longFired = true
        showAssistant(startListening = true)
    }

    // ---- lifecycle ----

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        // Enabling the service in system settings means the user wants the assistant on.
        app.settings.enabled = true
        refreshZones()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        removeOverlayNow()
        removeZones()
        assistant.stopListening()
        app.tts.shutdown()
        scope.coroutineContext[Job]?.cancelChildren()
        instance = null
    }

    /** Called when the user switches the assistant off. Frees everything and disables the service. */
    fun shutdown() {
        cleanup()
        try { disableSelf() } catch (_: Throwable) {}
        app.scheduleReap()
    }

    // ---- key events: long press on Back ----

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_BACK || !app.settings.backLongPress) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    longFired = false
                    handler.postDelayed(longPress, 400)
                }
                return true
            }
            KeyEvent.ACTION_UP -> {
                handler.removeCallbacks(longPress)
                if (!longFired) {
                    if (state.visible) hideAssistant() else performGlobalAction(GLOBAL_ACTION_BACK)
                }
                return true
            }
        }
        return false
    }

    // ---- corner swipe zones ----

    @SuppressLint("ClickableViewAccessibility")
    fun refreshZones() {
        removeZones()
        if (!::wm.isInitialized) return
        val side = app.settings.cornerSide
        val d = resources.displayMetrics.density
        val size = (app.settings.cornerSizeDp * d).toInt()
        val lift = (56 * d).toInt() // stay above the nav bar / gesture pill
        val threshold = 56 * d

        fun add(gravity: Int) {
            val v = View(this)
            var downY = 0f
            var fired = false
            v.setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { downY = e.rawY; fired = false }
                    MotionEvent.ACTION_MOVE -> if (!fired && downY - e.rawY > threshold) {
                        fired = true
                        showAssistant(startListening = true)
                    }
                }
                true
            }
            val lp = WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSPARENT
            ).apply {
                this.gravity = Gravity.BOTTOM or gravity
                y = lift
            }
            try { wm.addView(v, lp); zones += v } catch (_: Exception) {}
        }
        if (side != CornerSide.RIGHT) add(Gravity.START)
        if (side != CornerSide.LEFT) add(Gravity.END)
    }

    private fun removeZones() {
        zones.forEach { try { wm.removeViewImmediate(it) } catch (_: Exception) {} }
        zones.clear()
    }

    // ---- overlay card ----

    private val actions = object : OverlayActions {
        override fun onMic() = startVoice()
        override fun onReadScreen() = readScreen(translate = false)
        override fun onTranslateScreen() = readScreen(translate = true)
        override fun onClose() = hideAssistant()
    }

    fun showAssistant(startListening: Boolean) {
        if (!::wm.isInitialized) return
        if (overlay == null) {
            val o = OverlayOwner()
            val view = ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
                o.attachTo(this)
                setContent { AssistantOverlay(state, actions, app.settings.themeMode) }
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.BOTTOM }
            try {
                wm.addView(view, lp)
                o.resume()
                overlay = view
                owner = o
            } catch (e: Exception) {
                o.destroy()
                return
            }
        }
        handler.removeCallbacksAndMessages(REMOVE_TOKEN)
        state.visible = true
        state.phase = Phase.IDLE
        state.title = "Чем помочь?"
        state.body = ""
        if (startListening) startVoice()
    }

    fun hideAssistant() {
        assistant.stopListening()
        app.tts.shutdown()
        state.visible = false
        state.phase = Phase.IDLE
        // let the exit animation finish, then destroy the Compose view completely
        handler.postAtTime({ removeOverlayNow() }, REMOVE_TOKEN, android.os.SystemClock.uptimeMillis() + 350)
    }

    private val REMOVE_TOKEN = Any()

    private fun removeOverlayNow() {
        overlay?.let { try { wm.removeViewImmediate(it) } catch (_: Exception) {} }
        overlay = null
        owner?.destroy()
        owner = null
        state.visible = false
    }

    // ---- actions ----

    private fun startVoice() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            state.phase = Phase.ANSWER
            state.title = "Нет доступа к микрофону"
            state.body = "Откройте приложение и выдайте разрешение на микрофон."
            return
        }
        state.phase = Phase.LISTENING
        state.title = "Слушаю…"
        state.body = ""
        scope.launch {
            assistant.startListening(object : Assistant.Listener {
                override fun onPartial(text: String) { scope.launch { state.body = text } }
                override fun onResult(text: String) { scope.launch { process(text) } }
                override fun onError(message: String) {
                    scope.launch {
                        state.phase = Phase.ANSWER
                        state.title = "Ошибка"
                        state.body = message
                    }
                }
            })
        }
    }

    private fun process(question: String) {
        if (question.isBlank()) {
            state.phase = Phase.ANSWER
            state.title = "Не расслышал"
            state.body = "Нажмите «Говорить» и повторите."
            return
        }
        state.phase = Phase.WORKING
        state.title = question
        state.body = "Думаю…"
        scope.launch {
            val a = assistant.answer(question)
            state.phase = Phase.ANSWER
            state.body = a
        }
    }

    private fun readScreen(translate: Boolean) {
        assistant.stopListening()
        state.phase = Phase.WORKING
        state.title = if (translate) "Перевожу экран…" else "Читаю экран…"
        scope.launch {
            // hide our own card so it is not part of the screenshot
            overlay?.visibility = View.INVISIBLE
            delay(350)
            val text = assistant.readScreenText(this@GestureAccessibilityService)
            overlay?.visibility = View.VISIBLE
            if (text.isBlank()) {
                state.phase = Phase.ANSWER
                state.title = "Текст не найден"
                state.body = "На экране нет распознаваемого текста."
                return@launch
            }
            if (translate) {
                val tr = assistant.translate(text)
                state.title = "Перевод"
                state.body = tr
            } else {
                state.title = "Текст на экране"
                state.body = text
            }
            state.phase = Phase.ANSWER
        }
    }

    /** Used by the main screen quick actions: run after the app has gone to background. */
    fun runScreenAction(translate: Boolean) {
        showAssistant(startListening = false)
        scope.launch {
            delay(600)
            readScreen(translate)
        }
    }

    fun openMainApp() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
