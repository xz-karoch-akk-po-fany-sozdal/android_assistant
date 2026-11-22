package com.example.assistant.qa

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.AlarmClock
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Result of local processing. [handled]=false means "not understood locally". */
data class LocalResult(val handled: Boolean, val answer: String)

class LocalIntentEngine(context: Context) {
    private val ctx = context.applicationContext
    private var torchOn = false

    fun handle(raw: String): LocalResult {
        val t = raw.lowercase(Locale("ru")).trim().replace('ё', 'е')
        if (t.isEmpty()) return LocalResult(true, "Я ничего не расслышал.")

        return when {
            t.contains("сколько времени") || t.contains("который час") || t == "время" ->
                say("Сейчас " + SimpleDateFormat("HH:mm", Locale("ru")).format(Date()))

            t.contains("какое число") || t.contains("какой сегодня день") || t.contains("какая сегодня дата") ->
                say(SimpleDateFormat("d MMMM yyyy, EEEE", Locale("ru")).format(Date()))

            t.contains("погод") -> openWeather()

            t.contains("будильник") -> setAlarm(t)

            t.contains("таймер") -> setTimer(t)

            t.contains("калькулятор") -> openCalculator()

            t.contains("фонарик") || t.contains("фонарь") -> torch(
                when {
                    t.contains("выключ") || t.contains("погас") -> false
                    t.contains("включ") || t.contains("зажг") -> true
                    else -> !torchOn
                }
            )

            t.startsWith("открой ") || t.startsWith("запусти ") || t.startsWith("открыть ") ->
                openApp(t.substringAfter(' ').trim())

            else -> calc(t) ?: LocalResult(
                false,
                "Это требует интернета и облачной модели. Добавьте API-ключ в настройках, если хотите."
            )
        }
    }

    private fun say(s: String) = LocalResult(true, s)

    private fun launch(i: Intent): Boolean = try {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i); true
    } catch (_: Exception) { false }

    private fun openWeather(): LocalResult {
        val pm = ctx.packageManager
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        )
        val hit = apps.firstOrNull {
            val l = it.loadLabel(pm).toString().lowercase()
            l.contains("погод") || l.contains("weather")
        }
        if (hit != null) {
            val i = pm.getLaunchIntentForPackage(hit.activityInfo.packageName)
            if (i != null && launch(i)) return say("Открываю приложение погоды.")
        }
        return if (launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://yandex.ru/pogoda"))))
            say("Приложения погоды нет, открываю страницу в браузере (нужен интернет).")
        else say("Не нашёл приложение погоды.")
    }

    private fun setAlarm(t: String): LocalResult {
        val m = Regex("(\\d{1,2})[: .](\\d{2})").find(t)
            ?: Regex("на (\\d{1,2})").find(t)
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
        if (m != null) {
            val h = m.groupValues[1].toInt()
            val min = m.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
            if (h in 0..23 && min in 0..59) {
                i.putExtra(AlarmClock.EXTRA_HOUR, h).putExtra(AlarmClock.EXTRA_MINUTES, min)
                return if (launch(i)) say("Будильник на %02d:%02d".format(h, min)) else say("Не удалось открыть часы.")
            }
        }
        return if (launch(Intent(AlarmClock.ACTION_SHOW_ALARMS))) say("Открываю будильники. Назовите время, например: «будильник на 7:30».")
        else say("Не удалось открыть часы.")
    }

    private fun setTimer(t: String): LocalResult {
        var sec = 0
        Regex("(\\d+)\\s*(час|ч)").find(t)?.let { sec += it.groupValues[1].toInt() * 3600 }
        Regex("(\\d+)\\s*(минут|мин|м\\b)").find(t)?.let { sec += it.groupValues[1].toInt() * 60 }
        Regex("(\\d+)\\s*(секунд|сек|с\\b)").find(t)?.let { sec += it.groupValues[1].toInt() }
        if (sec == 0) Regex("(\\d+)").find(t)?.let { sec = it.groupValues[1].toInt() * 60 }
        if (sec <= 0) return say("На сколько поставить таймер? Например: «таймер на 5 минут».")
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, sec)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        return if (launch(i)) say("Таймер на ${sec / 60} мин ${sec % 60} с") else say("Не удалось открыть таймер.")
    }

    private fun openCalculator(): LocalResult {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR)
        return if (launch(i)) say("Открываю калькулятор.") else say("Калькулятор не найден.")
    }

    private fun torch(on: Boolean): LocalResult = try {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it)
                .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: throw IllegalStateException()
        cm.setTorchMode(id, on)
        torchOn = on
        say(if (on) "Фонарик включён." else "Фонарик выключен.")
    } catch (_: Exception) {
        say("Не удалось управлять фонариком.")
    }

    private fun openApp(name: String): LocalResult {
        if (name.isBlank()) return say("Какое приложение открыть?")
        val pm = ctx.packageManager
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        )
        val stem = name.trim().take(maxOf(3, name.length - 2))
        val hit = apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(stem) }
        val intent = hit?.let { pm.getLaunchIntentForPackage(it.activityInfo.packageName) }
        return if (intent != null && launch(intent)) say("Открываю ${hit?.loadLabel(pm)}.")
        else say("Не нашёл приложение «$name».")
    }

    private fun calc(t: String): LocalResult? {
        val s = t.replace("плюс", "+").replace("минус", "-")
            .replace("умножить на", "*").replace("разделить на", "/")
            .replace("х", "*").replace("x", "*").replace(",", ".")
        val m = Regex("(-?\\d+(?:\\.\\d+)?)\\s*([+\\-*/])\\s*(-?\\d+(?:\\.\\d+)?)").find(s) ?: return null
        val a = m.groupValues[1].toDouble()
        val b = m.groupValues[3].toDouble()
        val r = when (m.groupValues[2]) {
            "+" -> a + b; "-" -> a - b; "*" -> a * b
            else -> if (b == 0.0) return say("На ноль делить нельзя.") else a / b
        }
        val txt = if (r == Math.floor(r) && Math.abs(r) < 1e12) r.toLong().toString() else "%.4f".format(Locale.US, r).trimEnd('0').trimEnd('.')
        return say("Получится $txt")
    }
}
