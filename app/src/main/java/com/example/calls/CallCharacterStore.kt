package com.example.calls

import android.content.Context

data class CallCharacter(
    val name: String,
    val avatar: String,
    val voiceLanguage: String,
    val configured: Boolean
)

object CallCharacterStore {
    private const val PREFS = "nexusai_call_preferences"
    private const val KEY_NAME = "character_name"
    private const val KEY_AVATAR = "character_avatar"
    private const val KEY_VOICE = "character_voice"
    private const val KEY_CONFIGURED = "character_configured"

    fun load(context: Context): CallCharacter {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return CallCharacter(
            name = prefs.getString(KEY_NAME, "NexusAI") ?: "NexusAI",
            avatar = prefs.getString(KEY_AVATAR, "🤖") ?: "🤖",
            voiceLanguage = prefs.getString(KEY_VOICE, "system") ?: "system",
            configured = prefs.getBoolean(KEY_CONFIGURED, false)
        )
    }

    fun save(context: Context, name: String, avatar: String, voiceLanguage: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_NAME, name.trim().ifBlank { "NexusAI" })
            .putString(KEY_AVATAR, avatar)
            .putString(KEY_VOICE, voiceLanguage)
            .putBoolean(KEY_CONFIGURED, true)
            .apply()
    }
}
