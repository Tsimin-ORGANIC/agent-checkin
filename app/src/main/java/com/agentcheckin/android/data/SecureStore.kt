package com.agentcheckin.android.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStore(context: Context) {
    private val appContext = context.applicationContext

    var usingEncryptedStorage: Boolean = true
        private set

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "agent_checkin_secrets",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Throwable) {
        usingEncryptedStorage = false
        appContext.getSharedPreferences("agent_checkin_secrets_fallback", Context.MODE_PRIVATE)
    }

    var wbAccessToken: String?
        get() = prefs.getString(KEY_WB_ACCESS, null)
        set(value) = prefs.edit().putString(KEY_WB_ACCESS, value).apply()

    var wbRefreshToken: String?
        get() = prefs.getString(KEY_WB_REFRESH, null)
        set(value) = prefs.edit().putString(KEY_WB_REFRESH, value).apply()

    var wbUid: String?
        get() = prefs.getString(KEY_WB_UID, null)
        set(value) = prefs.edit().putString(KEY_WB_UID, value).apply()

    var wbLabel: String?
        get() = prefs.getString(KEY_WB_LABEL, null)
        set(value) = prefs.edit().putString(KEY_WB_LABEL, value).apply()

    var wbApiBase: String?
        get() = prefs.getString(KEY_WB_API_BASE, null)
        set(value) = prefs.edit().putString(KEY_WB_API_BASE, value).apply()

    var qoderPat: String?
        get() = prefs.getString(KEY_QODER_PAT, null)
        set(value) = prefs.edit().putString(KEY_QODER_PAT, value).apply()

    var qoderJobToken: String?
        get() = prefs.getString(KEY_QODER_JOB, null)
        set(value) = prefs.edit().putString(KEY_QODER_JOB, value).apply()

    var qoderJobRefresh: String?
        get() = prefs.getString(KEY_QODER_JOB_REFRESH, null)
        set(value) = prefs.edit().putString(KEY_QODER_JOB_REFRESH, value).apply()

    var qoderJobExpiresAt: Long
        get() = prefs.getLong(KEY_QODER_EXPIRES, 0L)
        set(value) = prefs.edit().putLong(KEY_QODER_EXPIRES, value).apply()

    var qoderUid: String?
        get() = prefs.getString(KEY_QODER_UID, null)
        set(value) = prefs.edit().putString(KEY_QODER_UID, value).apply()

    var qoderLabel: String?
        get() = prefs.getString(KEY_QODER_LABEL, null)
        set(value) = prefs.edit().putString(KEY_QODER_LABEL, value).apply()

    fun isLoggedIn(platform: Platform): Boolean {
        return if (platform == Platform.WORKBUDDY) {
            !wbAccessToken.isNullOrBlank()
        } else {
            !qoderPat.isNullOrBlank()
        }
    }

    fun labelFor(platform: Platform): String? {
        return if (platform == Platform.WORKBUDDY) wbLabel else qoderLabel
    }

    fun clearWorkBuddySession() {
        prefs.edit()
            .remove(KEY_WB_ACCESS)
            .remove(KEY_WB_REFRESH)
            .remove(KEY_WB_API_BASE)
            .apply()
    }

    fun clearWorkBuddy() {
        prefs.edit()
            .remove(KEY_WB_ACCESS)
            .remove(KEY_WB_REFRESH)
            .remove(KEY_WB_UID)
            .remove(KEY_WB_LABEL)
            .remove(KEY_WB_API_BASE)
            .apply()
    }

    fun clearQoderSession() {
        prefs.edit()
            .remove(KEY_QODER_PAT)
            .remove(KEY_QODER_JOB)
            .remove(KEY_QODER_JOB_REFRESH)
            .remove(KEY_QODER_EXPIRES)
            .apply()
    }

    fun clearQoder() {
        prefs.edit()
            .remove(KEY_QODER_PAT)
            .remove(KEY_QODER_JOB)
            .remove(KEY_QODER_JOB_REFRESH)
            .remove(KEY_QODER_EXPIRES)
            .remove(KEY_QODER_UID)
            .remove(KEY_QODER_LABEL)
            .apply()
    }

    companion object {
        private const val KEY_WB_ACCESS = "wb_access_token"
        private const val KEY_WB_REFRESH = "wb_refresh_token"
        private const val KEY_WB_UID = "wb_uid"
        private const val KEY_WB_LABEL = "wb_label"
        private const val KEY_WB_API_BASE = "wb_api_base"
        private const val KEY_QODER_PAT = "qoder_pat"
        private const val KEY_QODER_JOB = "qoder_job_token"
        private const val KEY_QODER_JOB_REFRESH = "qoder_job_refresh"
        private const val KEY_QODER_EXPIRES = "qoder_job_expires_at"
        private const val KEY_QODER_UID = "qoder_uid"
        private const val KEY_QODER_LABEL = "qoder_label"
    }
}
