package com.capri.pet

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurityHelper {
    private const val PREFS = "capri_secure"
    private const val KEY = "api_key"

    private fun prefs(c: Context) = EncryptedSharedPreferences.create(
        c, PREFS,
        MasterKey.Builder(c).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun hasApiKey(c: Context) = prefs(c).contains(KEY)
    fun saveApiKey(c: Context, k: String) { prefs(c).edit().putString(KEY, k).apply() }
    fun getApiKey(c: Context): String? = prefs(c).getString(KEY, null)
}
