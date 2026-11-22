package com.example.assistant.ocr

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Screen text reading.
 * 1) Text from the accessibility node tree (works for Russian, needs no model).
 * 2) ML Kit OCR on a screenshot (Latin script only: ML Kit has no Cyrillic model).
 * The recognizer is created per call and closed right after.
 */
class ScreenOcrService {

    /** Visible text taken directly from UI nodes. */
    fun readNodeText(service: AccessibilityService): String {
        val root = service.rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 60) return
            if (n.isVisibleToUser) {
                val t = n.text?.toString()?.trim().orEmpty()
                val d = n.contentDescription?.toString()?.trim().orEmpty()
                val s = if (t.isNotEmpty()) t else d
                if (s.isNotEmpty()) sb.append(s).append('\n')
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        return sb.toString().trim()
    }

    /** Screenshot via accessibility (API 30+). Returns null if unsupported or failed. */
    suspend fun screenshot(service: AccessibilityService): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return suspendCancellableCoroutine { c ->
            val executor = Executors.newSingleThreadExecutor()
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY, executor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val hw = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        val bmp = hw?.copy(Bitmap.Config.ARGB_8888, false)
                        result.hardwareBuffer.close()
                        hw?.recycle()
                        executor.shutdown()
                        c.resume(bmp)
                    }
                    override fun onFailure(errorCode: Int) {
                        executor.shutdown()
                        c.resume(null)
                    }
                }
            )
        }
    }

    suspend fun recognize(bitmap: Bitmap): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text.trim()
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }

    /** Best effort: node text first; if empty, screenshot OCR. */
    suspend fun readScreen(service: AccessibilityService): String {
        val nodes = readNodeText(service)
        if (nodes.isNotBlank()) return nodes
        val shot = screenshot(service) ?: return ""
        return try { recognize(shot) } catch (_: Exception) { "" }
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
    addOnCanceledListener { c.cancel() }
}
