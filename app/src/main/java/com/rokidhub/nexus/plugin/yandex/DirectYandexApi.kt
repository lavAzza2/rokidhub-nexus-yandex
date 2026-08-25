package com.rokidhub.nexus.plugin.yandex

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class DirectCommandResult(val statusCode: Int, val answer: String)

class DirectYandexApi {
    private val executor = Executors.newSingleThreadExecutor()

    fun command(accessToken: String, command: String, callback: (Result<DirectCommandResult>) -> Unit) {
        executor.execute {
            callback(runCatching {
                val info = request("/v1.0/user/info", "GET", accessToken, null)
                if (info.first !in 200..299) return@runCatching errorResult(info.first)
                when (val planned = YandexCommandPlanner.plan(command, info.second)) {
                    is PlannedCommand.Answer -> DirectCommandResult(200, planned.text)
                    is PlannedCommand.SetOnOff -> {
                        val devices = JSONArray()
                        planned.deviceIds.forEach { deviceId ->
                            devices.put(
                                JSONObject()
                                    .put("id", deviceId)
                                    .put(
                                        "actions",
                                        JSONArray().put(
                                            JSONObject()
                                                .put("type", "devices.capabilities.on_off")
                                                .put(
                                                    "state",
                                                    JSONObject()
                                                        .put("instance", "on")
                                                        .put("value", planned.enabled),
                                                ),
                                        ),
                                    ),
                            )
                        }
                        val action = request(
                            "/v1.0/devices/actions",
                            "POST",
                            accessToken,
                            JSONObject().put("devices", devices),
                        )
                        if (action.first !in 200..299) return@runCatching errorResult(action.first)
                        val prefix = if (planned.enabled) "Включено" else "Выключено"
                        DirectCommandResult(200, "$prefix: ${planned.deviceNames.joinToString()}")
                    }
                }
            })
        }
    }

    fun close() = executor.shutdownNow()

    private fun request(
        path: String,
        method: String,
        accessToken: String,
        payload: JSONObject?,
    ): Pair<Int, JSONObject> {
        val connection = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 25_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            if (payload != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        return try {
            if (payload != null) connection.outputStream.use {
                it.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            status to if (body.isBlank()) JSONObject() else JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun errorResult(statusCode: Int): DirectCommandResult = DirectCommandResult(
        statusCode,
        when (statusCode) {
            401, 403 -> "Доступ Яндекса истёк или отозван. Войди заново в настройках плагина."
            429 -> "Яндекс временно ограничил число запросов. Попробуй позже."
            else -> "Яндекс Умный дом вернул ошибку $statusCode."
        },
    )

    private companion object {
        const val BASE_URL = "https://api.iot.yandex.net"
    }
}
