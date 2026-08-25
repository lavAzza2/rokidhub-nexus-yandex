package com.rokidhub.nexus.plugin.yandex

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import com.anezium.rokidbus.client.plugin.NexusCard
import com.anezium.rokidbus.client.plugin.NexusPluginService
import com.anezium.rokidbus.client.plugin.NexusSdkResult
import com.anezium.rokidbus.client.plugin.NexusSpeechCallbacks
import com.anezium.rokidbus.client.plugin.NexusSpeechError
import com.anezium.rokidbus.client.plugin.NexusSpeechSession
import com.anezium.rokidbus.client.plugin.NexusSpeechState
import com.anezium.rokidbus.client.plugin.NexusSpeechStopReason
import com.anezium.rokidbus.client.plugin.NexusSurfaceSession
import com.anezium.rokidbus.client.plugin.NexusTtsCallbacks
import com.anezium.rokidbus.client.plugin.NexusTtsDoneReason
import com.anezium.rokidbus.client.plugin.NexusTtsSession
import com.anezium.rokidbus.shared.plugin.NexusInputEvent

class RokidHubPluginService : NexusPluginService() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var credentials: CredentialStore
    private var api = RokidHubApi()
    private var directApi = DirectYandexApi()
    private var surface: NexusSurfaceSession? = null
    private var speech: NexusSpeechSession? = null
    private var tts: NexusTtsSession? = null
    private var surfaceShown = false
    private var openGeneration = 0
    private var submittedFinal = false

    override fun onCreate() {
        super.onCreate()
        credentials = CredentialStore(applicationContext)
    }

    override fun onNexusOpen() {
        openGeneration += 1
        surface = nexusSurfaceSession(SURFACE_ID)
        surfaceShown = false
        submittedFinal = false
        val generation = openGeneration
        when (credentials.connectionMode) {
            ConnectionMode.DIRECT -> {
                if (credentials.readYandexAccessToken() == null) {
                    show(
                        "Нужен вход в Яндекс",
                        listOf("Открой настройки плагина в Rokid Nexus на телефоне."),
                        "OAuth-токен останется только на телефоне",
                    )
                } else {
                    beginListening(generation)
                }
            }
            ConnectionMode.CLOUD -> {
                if (credentials.readCloudAccessToken() == null) beginPairing(generation) else beginListening(generation)
            }
        }
    }

    override fun onNexusClose() {
        openGeneration += 1
        main.removeCallbacksAndMessages(null)
        speech?.stop()
        speech = null
        tts?.close()
        tts = null
        surface = null
        surfaceShown = false
    }

    override fun onDestroy() {
        api.close()
        directApi.close()
        super.onDestroy()
    }

    override fun onNexusInput(event: NexusInputEvent) {
        if (event.action != KeyEvent.ACTION_DOWN) return
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            -> if (speech == null) {
                when (credentials.connectionMode) {
                    ConnectionMode.DIRECT -> {
                        if (credentials.readYandexAccessToken() == null) {
                            show("Нужен вход в Яндекс", listOf("Открой настройки плагина на телефоне."), null)
                        } else beginListening(openGeneration)
                    }
                    ConnectionMode.CLOUD -> {
                        if (credentials.readCloudAccessToken() == null) beginPairing(openGeneration)
                        else beginListening(openGeneration)
                    }
                }
            }
            KeyEvent.KEYCODE_BACK -> surface?.hide()
        }
    }

    private fun beginPairing(generation: Int) {
        show("Подключение", listOf("Создаю одноразовый код…"), "Подключение действует 10 минут")
        api.startPairing(credentials.installationId) { result ->
            main.post {
                if (generation != openGeneration) return@post
                val response = result.getOrNull()
                if (response == null || !response.successful) {
                    show("Нет связи с RokidHub", listOf(response?.message() ?: "Проверь интернет на телефоне."), "Нажми, чтобы повторить")
                    return@post
                }
                val code = response.payload.optString("code")
                val pollSecret = response.payload.optString("poll_secret")
                show(
                    "Привяжи RokidHub",
                    listOf("Открой rokidhub.com, войди в кабинет", "Введи код: $code"),
                    "Ожидаю подтверждение…",
                )
                pollPairing(generation, pollSecret)
            }
        }
    }

    private fun pollPairing(generation: Int, pollSecret: String) {
        main.postDelayed({
            if (generation != openGeneration) return@postDelayed
            api.pollPairing(credentials.installationId, pollSecret) { result ->
                main.post {
                    if (generation != openGeneration) return@post
                    val response = result.getOrNull()
                    when {
                        response?.successful == true && response.payload.optString("status") == "connected" -> {
                            val token = response.payload.optString("access_token")
                            if (token.isBlank()) {
                                show("Ошибка привязки", listOf("RokidHub не вернул токен плагина."), "Нажми, чтобы повторить")
                            } else {
                                credentials.saveCloudAccessToken(token)
                                beginListening(generation)
                            }
                        }
                        response?.statusCode == 410 -> show("Код истёк", listOf("Нажми, чтобы создать новый код."), null)
                        else -> pollPairing(generation, pollSecret)
                    }
                }
            }
        }, POLL_INTERVAL_MS)
    }

    private fun beginListening(generation: Int) {
        if (generation != openGeneration || speech != null) return
        submittedFinal = false
        show("RokidHub", listOf("Слушаю…"), "Скажи команду без слова «Алиса»")
        val session = nexusSpeechSession(object : NexusSpeechCallbacks {
            override fun onSpeechStarted(realtime: Boolean) = Unit

            override fun onSpeechState(state: NexusSpeechState) {
                if (state == NexusSpeechState.PROCESSING && !submittedFinal) {
                    show("RokidHub", listOf("Распознаю…"), null)
                }
            }

            override fun onSpeechPartial(text: String) {
                if (text.isNotBlank()) show("RokidHub", listOf(text.take(240)), "Слушаю…")
            }

            override fun onSpeechFinal(text: String) {
                if (submittedFinal || text.isBlank()) return
                submittedFinal = true
                submitCommand(generation, text.trim())
            }

            override fun onSpeechStopped(reason: NexusSpeechStopReason, error: NexusSpeechError?) {
                speech = null
                if (generation != openGeneration || submittedFinal) return
                val message = when (reason) {
                    NexusSpeechStopReason.NO_SPEECH -> "Не расслышал команду."
                    NexusSpeechStopReason.REVOKED -> "Разрешение STT отозвано в Nexus."
                    NexusSpeechStopReason.LINK_LOST -> "Потеряна связь с очками."
                    else -> error?.detail ?: "Распознавание остановлено."
                }
                show("RokidHub", listOf(message.take(240)), "Нажми, чтобы повторить")
            }
        })
        speech = session
        val result = session?.start("ru-RU")
        if (result != NexusSdkResult.SENT) {
            speech = null
            show("Нет доступа к речи", listOf("Разреши Speech to text для плагина в Rokid Nexus."), "Затем нажми, чтобы повторить")
        }
    }

    private fun submitCommand(generation: Int, command: String) {
        show("Выполняю", listOf(command.take(240)), null)
        when (credentials.connectionMode) {
            ConnectionMode.DIRECT -> submitDirectCommand(generation, command)
            ConnectionMode.CLOUD -> submitCloudCommand(generation, command)
        }
    }

    private fun submitDirectCommand(generation: Int, command: String) {
        val token = credentials.readYandexAccessToken()
        if (token == null) {
            show("Нужен вход в Яндекс", listOf("Открой настройки плагина на телефоне."), null)
            return
        }
        directApi.command(token, command) { result ->
            main.post {
                if (generation != openGeneration) return@post
                val response = result.getOrNull()
                if (response?.statusCode == 401 || response?.statusCode == 403) {
                    credentials.clearYandexAccessToken()
                }
                val answer = response?.answer ?: "Нет связи с Яндекс Умным домом."
                show("RokidHub", listOf(answer.take(240)), "Нажми, чтобы сказать ещё")
                speak(answer)
            }
        }
    }

    private fun submitCloudCommand(generation: Int, command: String) {
        val token = credentials.readCloudAccessToken()
        if (token == null) {
            beginPairing(generation)
            return
        }
        api.command(credentials.installationId, token, command) { result ->
            main.post {
                if (generation != openGeneration) return@post
                val response = result.getOrNull()
                if (response?.statusCode == 401) {
                    credentials.clearCloudAccessToken()
                    beginPairing(generation)
                    return@post
                }
                val answer = response?.message() ?: "Нет связи с RokidHub. Проверь интернет на телефоне."
                show("RokidHub", listOf(answer.take(240)), "Нажми, чтобы сказать ещё")
                speak(answer)
            }
        }
    }

    private fun speak(text: String) {
        val session = tts ?: nexusTtsSession(object : NexusTtsCallbacks {
            override fun onTtsStarted(utteranceId: String) = Unit
            override fun onTtsDone(utteranceId: String, reason: NexusTtsDoneReason) = Unit
        })?.also { tts = it }
        session?.speak(text.take(1024))
    }

    private fun show(title: String, lines: List<String>, footer: String?) {
        val card = NexusCard(
            title = title,
            lines = lines,
            footer = footer,
            contentKey = "rokidhub-state-${title.hashCode()}-${lines.hashCode()}",
            handlesBack = true,
        )
        val result = if (surfaceShown) surface?.updateCard(card) else surface?.showCard(card)
        if (result == NexusSdkResult.SENT) surfaceShown = true
    }

    private companion object {
        const val SURFACE_ID = "main"
        const val POLL_INTERVAL_MS = 3_000L
    }
}
