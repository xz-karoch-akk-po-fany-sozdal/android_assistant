package com.example.assistant.voice

import android.content.Context
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads the Vosk model only when listening starts and frees everything in [stop].
 * The model lives in assets/model-ru and is copied once to filesDir (disk only, not RAM).
 */
class VoskRecognizer(context: Context) {
    private val ctx = context.applicationContext
    private var model: Model? = null
    private var service: SpeechService? = null

    interface Callback {
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError(message: String)
    }

    val isListening: Boolean get() = service != null

    private suspend fun ensureModelDir(): File = withContext(Dispatchers.IO) {
        val target = File(ctx.filesDir, "vosk-model-ru")
        val marker = File(target, ".ok")
        if (!marker.exists()) {
            target.deleteRecursively()
            copyAssetDir("model-ru", target)
            marker.writeText("1")
        }
        target
    }

    private fun copyAssetDir(assetPath: String, dest: File) {
        val children = ctx.assets.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
            dest.parentFile?.mkdirs()
            ctx.assets.open(assetPath).use { input ->
                FileOutputStream(dest).use { out -> input.copyTo(out, 64 * 1024) }
            }
        } else {
            dest.mkdirs()
            for (c in children) copyAssetDir("$assetPath/$c", File(dest, c))
        }
    }

    suspend fun start(cb: Callback) {
        stop()
        try {
            val dir = ensureModelDir()
            val m = withContext(Dispatchers.IO) { Model(dir.absolutePath) }
            model = m
            val rec = Recognizer(m, 16000.0f)
            val s = SpeechService(rec, 16000.0f)
            service = s
            s.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    parse(hypothesis, "partial")?.let { if (it.isNotBlank()) cb.onPartial(it) }
                }
                override fun onResult(hypothesis: String?) {
                    parse(hypothesis, "text")?.let { if (it.isNotBlank()) cb.onFinal(it) }
                }
                override fun onFinalResult(hypothesis: String?) {
                    val t = parse(hypothesis, "text").orEmpty()
                    stop()
                    cb.onFinal(t)
                }
                override fun onError(exception: Exception?) {
                    stop()
                    cb.onError(exception?.message ?: "Ошибка распознавания")
                }
                override fun onTimeout() {
                    stop()
                    cb.onFinal("")
                }
            }, 10000)
        } catch (e: Throwable) {
            stop()
            cb.onError(
                if (e is java.io.FileNotFoundException || e.message?.contains("model-ru") == true)
                    "Модель Vosk не найдена в APK"
                else e.message ?: "Не удалось запустить распознавание"
            )
        }
    }

    /** Stop listening and free the model. */
    fun stop() {
        val s = service
        val m = model
        service = null
        model = null
        if (s == null && m == null) return
        // SpeechService.stop() joins its worker thread, and callbacks run on that thread,
        // so teardown must happen on another thread to avoid a self-join deadlock.
        Thread {
            try { s?.stop() } catch (_: Throwable) {}
            try { s?.shutdown() } catch (_: Throwable) {}
            try { m?.close() } catch (_: Throwable) {}
        }.start()
    }

    private fun parse(json: String?, key: String): String? {
        if (json == null) return null
        return try { JSONObject(json).optString(key) } catch (_: Exception) { null }
    }
}
