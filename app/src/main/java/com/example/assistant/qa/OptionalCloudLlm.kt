package com.example.assistant.qa

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenAI-compatible chat completion (OpenRouter by default).
 * Does nothing and never touches the network unless an API key is set.
 */
class OptionalCloudLlm(
    private val apiKey: String,
    private val baseUrl: String,
    private val model: String
) {
    val enabled: Boolean get() = apiKey.isNotBlank()

    suspend fun ask(question: String): String = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext "Облачный режим выключен (нет API-ключа)."
        val body = JSONObject()
            .put("model", model)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", "Отвечай кратко, по-русски."))
                .put(JSONObject().put("role", "user").put("content", question)))
            .toString()
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(baseUrl.trimEnd('/') + "/chat/completions").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 30000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) return@withContext "Ошибка облака ($code)."
            JSONObject(text).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content").trim()
        } catch (e: Exception) {
            "Нет связи с облаком: ${e.message ?: "ошибка сети"}"
        } finally {
            conn?.disconnect()
        }
    }
}
