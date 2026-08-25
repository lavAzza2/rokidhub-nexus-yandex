package com.rokidhub.nexus.plugin.yandex

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class ApiResult(
    val statusCode: Int,
    val payload: JSONObject,
) {
    val successful: Boolean get() = statusCode in 200..299
    fun message(): String = payload.optString("answer")
        .ifBlank { payload.optString("error") }
        .ifBlank { "RokidHub вернул ошибку $statusCode." }
}

class RokidHubApi(
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(),
) {
    fun startPairing(installationId: String, callback: (Result<ApiResult>) -> Unit) = request(
        path = "/pairing/start",
        payload = JSONObject()
            .put("installation_id", installationId)
            .put("plugin_id", PLUGIN_ID),
        callback = callback,
    )

    fun pollPairing(
        installationId: String,
        pollSecret: String,
        callback: (Result<ApiResult>) -> Unit,
    ) = request(
        path = "/pairing/poll",
        payload = JSONObject()
            .put("installation_id", installationId)
            .put("plugin_id", PLUGIN_ID)
            .put("poll_secret", pollSecret),
        callback = callback,
    )

    fun command(
        installationId: String,
        accessToken: String,
        command: String,
        callback: (Result<ApiResult>) -> Unit,
    ) = request(
        path = "/command",
        payload = JSONObject().put("command", command),
        installationId = installationId,
        accessToken = accessToken,
        callback = callback,
    )

    fun close() = executor.shutdownNow()

    private fun request(
        path: String,
        payload: JSONObject,
        installationId: String? = null,
        accessToken: String? = null,
        callback: (Result<ApiResult>) -> Unit,
    ) {
        executor.execute {
            callback(runCatching {
                val connection = (URL(BuildConfig.ROKIDHUB_BASE_URL + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 25_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    if (installationId != null && accessToken != null) {
                        setRequestProperty("Authorization", "Bearer $accessToken")
                        setRequestProperty("X-RokidHub-Installation-ID", installationId)
                    }
                }
                try {
                    connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                    val status = connection.responseCode
                    val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                    val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    ApiResult(status, if (body.isBlank()) JSONObject() else JSONObject(body))
                } finally {
                    connection.disconnect()
                }
            })
        }
    }

    companion object {
        const val PLUGIN_ID = "rokidhub.yandex"
        val DASHBOARD_URL: String
            get() = BuildConfig.ROKIDHUB_BASE_URL.removeSuffix("/api/v1/nexus") + "/dashboard/"
    }
}
