package com.example.assistant.translate

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device translation. Language models are not in the APK: they are downloaded on request
 * and can be deleted. The translator object is created per call and closed right after.
 */
class TranslationManager {
    private val manager get() = RemoteModelManager.getInstance()

    val supported: List<Pair<String, String>> = listOf(
        TranslateLanguage.ENGLISH to "Английский",
        TranslateLanguage.GERMAN to "Немецкий",
        TranslateLanguage.FRENCH to "Французский",
        TranslateLanguage.SPANISH to "Испанский",
        TranslateLanguage.ITALIAN to "Итальянский",
        TranslateLanguage.UKRAINIAN to "Украинский",
        TranslateLanguage.TURKISH to "Турецкий",
        TranslateLanguage.CHINESE to "Китайский"
    )

    private fun model(code: String) = TranslateRemoteModel.Builder(code).build()

    suspend fun isDownloaded(code: String): Boolean =
        manager.isModelDownloaded(model(code)).await()

    suspend fun download(code: String, wifiOnly: Boolean) {
        val cond = DownloadConditions.Builder().apply { if (wifiOnly) requireWifi() }.build()
        manager.download(model(code), cond).await()
    }

    suspend fun delete(code: String) {
        manager.deleteDownloadedModel(model(code)).await()
    }

    /** Cyrillic -> [foreign]; anything else -> Russian. Returns null if models are missing. */
    suspend fun translateAuto(text: String, foreign: String): String? {
        val cyr = text.count { it in 'А'..'я' || it == 'ё' || it == 'Ё' }
        val letters = text.count { it.isLetter() }.coerceAtLeast(1)
        val src: String
        val dst: String
        if (cyr * 2 >= letters) { src = TranslateLanguage.RUSSIAN; dst = foreign }
        else { src = foreign; dst = TranslateLanguage.RUSSIAN }
        if (!isDownloaded(src) || !isDownloaded(dst)) return null

        val translator = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(dst).build()
        )
        return try {
            translator.translate(text.take(4000)).await()
        } finally {
            translator.close()
        }
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
    addOnCanceledListener { c.cancel() }
}
