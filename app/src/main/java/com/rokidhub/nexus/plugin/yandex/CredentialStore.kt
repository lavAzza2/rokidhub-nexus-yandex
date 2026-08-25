package com.rokidhub.nexus.plugin.yandex

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CredentialStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    val installationId: String
        get() = preferences.getString(INSTALLATION_ID, null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString(INSTALLATION_ID, it).apply()
        }

    var connectionMode: ConnectionMode
        get() = runCatching {
            ConnectionMode.valueOf(preferences.getString(CONNECTION_MODE, ConnectionMode.DIRECT.name)!!)
        }.getOrDefault(ConnectionMode.DIRECT)
        set(value) = preferences.edit().putString(CONNECTION_MODE, value.name).apply()

    fun readCloudAccessToken(): String? = readSecret(CLOUD_ACCESS_TOKEN, CLOUD_KEY_ALIAS)

    fun saveCloudAccessToken(token: String) = saveSecret(CLOUD_ACCESS_TOKEN, CLOUD_KEY_ALIAS, token)

    fun clearCloudAccessToken() = clearSecret(CLOUD_ACCESS_TOKEN)

    fun readYandexAccessToken(): String? {
        val token = readSecret(YANDEX_ACCESS_TOKEN, YANDEX_KEY_ALIAS) ?: return null
        val expiresAt = preferences.getLong(YANDEX_EXPIRES_AT, 0L)
        return token.takeIf { expiresAt == 0L || System.currentTimeMillis() < expiresAt - EXPIRY_SKEW_MS }
    }

    fun saveYandexAccessToken(token: String, expiresInSeconds: Long) {
        saveSecret(YANDEX_ACCESS_TOKEN, YANDEX_KEY_ALIAS, token)
        val expiresAt = if (expiresInSeconds > 0) {
            System.currentTimeMillis() + expiresInSeconds * 1000L
        } else {
            0L
        }
        preferences.edit().putLong(YANDEX_EXPIRES_AT, expiresAt).apply()
    }

    fun clearYandexAccessToken() {
        clearSecret(YANDEX_ACCESS_TOKEN)
        preferences.edit().remove(YANDEX_EXPIRES_AT).apply()
    }

    private fun readSecret(preferenceKey: String, keyAlias: String): String? {
        val encrypted = preferences.getString(preferenceKey, null) ?: return null
        return runCatching {
            val packed = Base64.decode(encrypted, Base64.NO_WRAP)
            require(packed.size > IV_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(keyAlias), GCMParameterSpec(128, packed.copyOfRange(0, IV_BYTES)))
            cipher.doFinal(packed.copyOfRange(IV_BYTES, packed.size)).toString(Charsets.UTF_8)
        }.getOrNull()?.takeIf(String::isNotBlank)
    }

    private fun saveSecret(preferenceKey: String, keyAlias: String, token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key(keyAlias))
        val encrypted = cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        preferences.edit().putString(preferenceKey, Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    private fun clearSecret(preferenceKey: String) = preferences.edit().remove(preferenceKey).apply()

    private fun key(keyAlias: String): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFERENCES = "rokidhub_nexus_credentials"
        const val INSTALLATION_ID = "installation_id"
        const val CONNECTION_MODE = "connection_mode"
        const val CLOUD_ACCESS_TOKEN = "cloud_access_token_encrypted"
        const val CLOUD_KEY_ALIAS = "rokidhub_nexus_cloud_token_v1"
        const val YANDEX_ACCESS_TOKEN = "yandex_access_token_encrypted"
        const val YANDEX_EXPIRES_AT = "yandex_token_expires_at"
        const val YANDEX_KEY_ALIAS = "rokidhub_nexus_yandex_token_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val EXPIRY_SKEW_MS = 60_000L
    }
}

enum class ConnectionMode {
    DIRECT,
    CLOUD,
}
