package com.rokidhub.nexus.plugin.yandex

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import com.anezium.rokidbus.client.ui.BusTheme
import com.anezium.rokidbus.client.ui.NexusPluginIcons
import com.anezium.rokidbus.client.ui.NexusUi
import com.yandex.authsdk.YandexAuthLoginOptions
import com.yandex.authsdk.YandexAuthOptions
import com.yandex.authsdk.YandexAuthResult
import com.yandex.authsdk.YandexAuthSdk

class RokidHubSettingsActivity : ComponentActivity() {
    private lateinit var credentials: CredentialStore
    private lateinit var loginLauncher: ActivityResultLauncher<YandexAuthLoginOptions>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        credentials = CredentialStore(applicationContext)
        val yandexSdk = YandexAuthSdk.create(YandexAuthOptions(this))
        loginLauncher = registerForActivityResult(yandexSdk.contract) { result ->
            when (result) {
                is YandexAuthResult.Success -> {
                    credentials.saveYandexAccessToken(result.token.value, result.token.expiresIn)
                    credentials.connectionMode = ConnectionMode.DIRECT
                    buildUi()
                }
                is YandexAuthResult.Failure -> showLoginFailure(result.exception.message)
                YandexAuthResult.Cancelled -> Unit
            }
        }
        buildUi()
    }

    private fun buildUi() {
        window.statusBarColor = NexusUi.BG
        window.navigationBarColor = NexusUi.BG
        val directConnected = credentials.readYandexAccessToken() != null
        val cloudConnected = credentials.readCloudAccessToken() != null
        val mode = credentials.connectionMode
        val content = NexusUi.contentColumn(this).apply {
            addView(NexusUi.sectionRow(this@RokidHubSettingsActivity, "Прямое подключение (рекомендуется)"), NexusUi.block())
            addView(BusTheme.gap(this@RokidHubSettingsActivity, 10))
            addView(
                NexusUi.cardBody(
                    this@RokidHubSettingsActivity,
                    if (directConnected) {
                        "Яндекс подключён. OAuth-токен зашифрован Android Keystore и не отправляется в RokidHub."
                    } else {
                        "Войди через официальный Яндекс LoginSDK. Нужны права iot:view и iot:control."
                    },
                ),
                NexusUi.block(),
            )
            addView(BusTheme.gap(this@RokidHubSettingsActivity, 10))
            if (!directConnected) {
                addView(actionCard("Войти через Яндекс") {
                    if (BuildConfig.YANDEX_CLIENT_ID == "not-configured") {
                        showLoginFailure("В этой сборке не задан Yandex Client ID.")
                    } else {
                        loginLauncher.launch(YandexAuthLoginOptions())
                    }
                }, NexusUi.block())
            } else {
                addView(actionCard(if (mode == ConnectionMode.DIRECT) "Прямой режим выбран" else "Использовать прямой режим") {
                    credentials.connectionMode = ConnectionMode.DIRECT
                    buildUi()
                }, NexusUi.block())
                addView(BusTheme.gap(this@RokidHubSettingsActivity, 8))
                addView(actionCard("Удалить локальный токен Яндекса") {
                    credentials.clearYandexAccessToken()
                    buildUi()
                }, NexusUi.block())
            }
            addView(BusTheme.gap(this@RokidHubSettingsActivity, 24))
            addView(NexusUi.sectionRow(this@RokidHubSettingsActivity, "Облачный режим RokidHub"), NexusUi.block())
            addView(BusTheme.gap(this@RokidHubSettingsActivity, 10))
            addView(
                NexusUi.cardBody(
                    this@RokidHubSettingsActivity,
                    if (cloudConnected) {
                        "Плагин привязан отдельным отзываемым токеном. OAuth Яндекса хранится на RokidHub."
                    } else {
                        "Альтернатива: команды обрабатывает RokidHub. Выбери режим и открой плагин на очках для кода."
                    },
                ),
                NexusUi.block(),
            )
            addView(BusTheme.gap(this@RokidHubSettingsActivity, 10))
            addView(actionCard(if (mode == ConnectionMode.CLOUD) "Облачный режим выбран" else "Использовать облачный режим") {
                credentials.connectionMode = ConnectionMode.CLOUD
                buildUi()
            }, NexusUi.block())
            addView(BusTheme.gap(this@RokidHubSettingsActivity, 8))
            addView(actionCard("Открыть кабинет RokidHub") {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RokidHubApi.DASHBOARD_URL)))
            }, NexusUi.block())
            if (cloudConnected) {
                addView(BusTheme.gap(this@RokidHubSettingsActivity, 8))
                addView(actionCard("Перепривязать облачный режим") {
                    credentials.clearCloudAccessToken()
                    credentials.connectionMode = ConnectionMode.CLOUD
                    buildUi()
                }, NexusUi.block())
            }
            addView(BusTheme.gap(this@RokidHubSettingsActivity, 24))
            addView(NexusUi.sectionRow(this@RokidHubSettingsActivity, "Плагин"), NexusUi.block())
            addView(BusTheme.gap(this@RokidHubSettingsActivity, 10))
            addView(
                NexusUi.uninstallCard(this@RokidHubSettingsActivity, "RokidHub · Яндекс") {
                    startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))
                },
                NexusUi.block(),
            )
        }
        val root = NexusUi.fixedRoot(this).apply {
            addView(
                NexusUi.pluginHeader(
                    this@RokidHubSettingsActivity,
                    NexusPluginIcons.drawableFor("bolt"),
                    "RokidHub · Яндекс",
                    "Голосовое управление умным домом · v0.1",
                ),
                NexusUi.block(),
            )
            addView(
                NexusUi.screen(this@RokidHubSettingsActivity, content),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
        setContentView(root)
    }

    private fun showLoginFailure(message: String?) {
        val text = message?.takeIf(String::isNotBlank) ?: "Авторизация Яндекса не завершена."
        setContentView(
            NexusUi.fixedRoot(this).apply {
                addView(
                    NexusUi.cardBody(this@RokidHubSettingsActivity, text),
                    NexusUi.block(),
                )
                addView(
                    actionCard("Назад к настройкам") { buildUi() },
                    NexusUi.block(),
                )
            },
        )
    }

    private fun actionCard(title: String, action: () -> Unit) = NexusUi.pressableCard(this).apply {
        addView(NexusUi.rowTitle(this@RokidHubSettingsActivity, title))
        setOnClickListener { action() }
    }
}
