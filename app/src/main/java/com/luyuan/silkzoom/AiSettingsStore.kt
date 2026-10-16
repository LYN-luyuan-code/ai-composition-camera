package com.luyuan.silkzoom

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class AiSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
    private val credentials = context.getSharedPreferences("ai_credentials", Context.MODE_PRIVATE)
    val provider: AiProvider
        get() = runCatching { AiProvider.valueOf(preferences.getString("provider", "DEEPSEEK")!!) }.getOrDefault(AiProvider.DEEPSEEK)

    fun load(selected: AiProvider = provider): AiConfiguration {
        val prefix = selected.name
        return AiConfiguration(
            selected,
            preferences.getString("${prefix}_url", selected.baseUrl)!!,
            preferences.getString("${prefix}_model", selected.defaultModel)!!,
            runCatching { decrypt(credentials.getString(prefix, null), prefix) }.getOrDefault(""),
        )
    }

    fun save(config: AiConfiguration) {
        // Encrypt before writing any metadata. No plaintext credential is ever persisted.
        val encrypted = if (config.apiKey.isBlank()) null else encrypt(config.apiKey, config.provider.name)
        check(credentials.edit().apply {
            if (encrypted == null) remove(config.provider.name) else putString(config.provider.name, encrypted)
        }.commit()) { "无法保存 API Key，请重试" }
        check(preferences.edit()
            .putString("provider", config.provider.name)
            .putString("${config.provider.name}_url", config.baseUrl)
            .putString("${config.provider.name}_model", config.model)
            .commit()) { "无法保存 AI 设置，请重试" }
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }

    private fun encrypt(value: String, provider: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        cipher.updateAAD(provider.toByteArray(Charsets.UTF_8))
        val encoded = Base64.getEncoder()
        return encoded.encodeToString(cipher.iv) + ":" + encoded.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)))
    }

    private fun decrypt(value: String?, provider: String): String {
        if (value == null) return ""
        val parts = value.split(':', limit = 2)
        val decoder = Base64.getDecoder()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, decoder.decode(parts[0])))
        cipher.updateAAD(provider.toByteArray(Charsets.UTF_8))
        return String(cipher.doFinal(decoder.decode(parts[1])), Charsets.UTF_8)
    }

    private companion object { const val KEY_ALIAS = "silkzoom_ai_credentials_v1" }
}
