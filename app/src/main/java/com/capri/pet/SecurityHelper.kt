package com.capri.pet

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurityHelper {
    private const val PREFS = "capri_secure"
    private const val KEY = "api_key"

    private fun prefs(c: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(c)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            c, PREFS, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun hasApiKey(c: Context): Boolean {
        return try {
            val key = prefs(c).getString(KEY, null)
            key != null && key.isNotEmpty()
        } catch (e: Exception) {
            // Si el sistema de seguridad se corrompe, borramos la carpeta y pedimos de nuevo
            c.deleteSharedPreferences(PREFS)
            false
        }
    }

    fun saveApiKey(c: Context, k: String) {
        try {
            prefs(c).edit().putString(KEY, k).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getApiKey(c: Context): String? = try {
        prefs(c).getString(KEY, null)
    } catch (e: Exception) {
        null
    }
}
