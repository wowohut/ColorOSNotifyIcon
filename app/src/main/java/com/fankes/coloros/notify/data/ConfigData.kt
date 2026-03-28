package com.fankes.coloros.notify.data

import android.content.Context
import android.content.SharedPreferences
import com.fankes.coloros.notify.bean.IconDataBean
import org.json.JSONArray

object ConfigData {

    const val GROUP_CONFIG = "config"
    const val RULES_FILE_NAME = "rules.json"
    private const val PREFS_NAME = GROUP_CONFIG
    private val OBSOLETE_KEYS = arrayOf(
        "md3_style_enabled",
        "icon_corner_dp",
        "module_enabled",
        "icon_enhancement_enabled",
        "last_framework_name",
        "last_framework_version",
        "last_framework_api",
        "last_scope_list",
        "last_framework_connected_at",
    )
    const val KEY_RULES_JSON = "rules_json"
    const val KEY_RULES_COUNT = "rules_count"
    const val KEY_RULES_UPDATED_AT = "rules_updated_at"
    const val KEY_CONFIG_UPDATED_AT = "config_updated_at"
    private const val KEY_LAST_REMOTE_MIRRORED_AT = "last_remote_mirrored_at"

    private lateinit var appContext: Context

    fun initialize(context: Context) {
        if (!::appContext.isInitialized) appContext = context.applicationContext
        cleanupObsoleteKeys()
        ensureSyncMetadata()
    }

    private val prefs: SharedPreferences
        get() {
            check(::appContext.isInitialized) { "ConfigData 尚未初始化" }
            return appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

    val rulesJson: String
        get() = prefs.getString(KEY_RULES_JSON, "").orEmpty()

    val rulesCount: Int
        get() = prefs.getInt(KEY_RULES_COUNT, 0)

    val rulesUpdatedAt: Long
        get() = prefs.getLong(KEY_RULES_UPDATED_AT, 0L)

    val localConfigUpdatedAt: Long
        get() = prefs.getLong(KEY_CONFIG_UPDATED_AT, 0L)

    val hasPendingRemoteSync: Boolean
        get() = localConfigUpdatedAt > prefs.getLong(KEY_LAST_REMOTE_MIRRORED_AT, 0L)

    fun updateRules(json: String, updatedAt: Long = System.currentTimeMillis()) {
        val count = parseRules(json).size
        prefs.edit()
            .putString(KEY_RULES_JSON, json)
            .putInt(KEY_RULES_COUNT, count)
            .putLong(KEY_RULES_UPDATED_AT, updatedAt)
            .putLong(KEY_CONFIG_UPDATED_AT, updatedAt)
            .apply()
    }

    fun mirrorTo(remotePrefs: SharedPreferences): Boolean = remotePrefs.edit().apply {
        OBSOLETE_KEYS.forEach(::remove)
        putInt(KEY_RULES_COUNT, rulesCount)
        putLong(KEY_RULES_UPDATED_AT, rulesUpdatedAt)
        putLong(KEY_CONFIG_UPDATED_AT, localConfigUpdatedAt)
    }.commit()

    fun markRemoteMirrorSuccess(configUpdatedAt: Long = localConfigUpdatedAt) {
        prefs.edit().putLong(KEY_LAST_REMOTE_MIRRORED_AT, configUpdatedAt).apply()
    }

    fun parseRules(json: String): List<IconDataBean> {
        if (json.isBlank()) return emptyList()
        return buildList {
            runCatching {
                val array = JSONArray(json)
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    IconDataBean.fromJson(item)?.let(::add)
                }
            }
        }
    }

    private fun cleanupObsoleteKeys() {
        if (OBSOLETE_KEYS.none(prefs::contains)) return
        prefs.edit().apply {
            OBSOLETE_KEYS.forEach(::remove)
        }.commit()
    }

    private fun ensureSyncMetadata() {
        if (prefs.contains(KEY_CONFIG_UPDATED_AT)) return
        val hasExistingConfig = prefs.contains(KEY_RULES_JSON) ||
            prefs.contains(KEY_RULES_COUNT) ||
            prefs.contains(KEY_RULES_UPDATED_AT)
        if (!hasExistingConfig) return
        val fallbackUpdatedAt = prefs.getLong(KEY_RULES_UPDATED_AT, 0L).takeIf { it > 0L }
            ?: System.currentTimeMillis()
        prefs.edit()
            .putLong(KEY_CONFIG_UPDATED_AT, fallbackUpdatedAt)
            .apply()
    }
}
