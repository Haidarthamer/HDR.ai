package com.example.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypted, device-local API keys isolated by signed-in user ID or guest identity. */
class ApiKeyStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("nexusai_api_keys", Context.MODE_PRIVATE)

    fun getKey(identity: String): String? {
        getSlot(identity, "primary")?.let { return it }
        val slot = slotFor(identity)
        val ivValue = preferences.getString("${slot}_iv", null) ?: return null
        val encryptedValue = preferences.getString("${slot}_value", null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(slot),
                GCMParameterSpec(128, Base64.decode(ivValue, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(encryptedValue, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrNull()
    }

    fun saveKey(identity: String, apiKey: String) = saveSlot(identity, "primary", apiKey)

    fun getSecondaryKey(identity: String): String? = getSlot(identity, "secondary")

    fun saveSecondaryKey(identity: String, apiKey: String) = saveSlot(identity, "secondary", apiKey)

    fun getDailyRequestCount(identity: String): Int {
        val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val slot = slotFor(identity)
        if (preferences.getString("${slot}_usage_day", null) != day) return 0
        return preferences.getInt("${slot}_daily_requests", 0)
    }

    fun incrementDailyRequestCount(identity: String): Int {
        val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val slot = slotFor(identity)
        val current = if (preferences.getString("${slot}_usage_day", null) == day) preferences.getInt("${slot}_daily_requests", 0) else 0
        val next = current + 1
        preferences.edit().putString("${slot}_usage_day", day).putInt("${slot}_daily_requests", next).apply()
        return next
    }

    private fun getSlot(identity: String, name: String): String? {
        val slot = "${slotFor(identity)}_${name}"
        val ivValue = preferences.getString("${slot}_iv", null) ?: return null
        val encryptedValue = preferences.getString("${slot}_value", null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(slot), GCMParameterSpec(128, Base64.decode(ivValue, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(encryptedValue, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun saveSlot(identity: String, name: String, apiKey: String) {
        val slot = "${slotFor(identity)}_${name}"
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(slot))
        val encrypted = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        preferences.edit().putString("${slot}_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("${slot}_value", Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    private fun slotFor(identity: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        return "key_" + digest.take(16).joinToString("") { "%02x".format(it) }
    }

    private fun getOrCreateKey(slot: String): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "nexusai_${slot}"
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}