package com.astrawave.app.data

import android.content.Context

/** Persists per-profile configurable discovery rows and language. */
class UltraMaxCatalogPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("astrawave_catalog_layout", Context.MODE_PRIVATE)

    data class Layout(
        val presetId: String,
        val language: String,
        val orderedRowIds: List<String>,
        val hiddenRowIds: Set<String>,
    ) {
        fun visibleRows(kidsMode: Boolean = false): List<UltraMaxCatalogRegistry.Row> = orderedRowIds
            .mapNotNull(UltraMaxCatalogRegistry::row)
            .filterNot { it.id in hiddenRowIds }
            .filter { !kidsMode || it.kidsSafe }
    }

    fun load(profileId: String): Layout {
        val defaultPreset = UltraMaxCatalogRegistry.preset(DEFAULT_PRESET)!!
        val order = prefs.getString(key(profileId, "order"), null)
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.distinct()
            .orEmpty()
            .let { saved ->
                val known = saved.filter { UltraMaxCatalogRegistry.row(it) != null }
                val missing = defaultPreset.rowIds.filterNot { it in known }
                (known + missing).ifEmpty { defaultPreset.rowIds }
            }
        val hidden = prefs.getStringSet(key(profileId, "hidden"), emptySet()).orEmpty()
        val presetId = prefs.getString(key(profileId, "preset"), DEFAULT_PRESET)
            ?.takeIf { UltraMaxCatalogRegistry.preset(it) != null }
            ?: DEFAULT_PRESET
        val language = prefs.getString(key(profileId, "language"), DEFAULT_LANGUAGE)
            ?.takeIf { it in UltraMaxCatalogRegistry.languages }
            ?: DEFAULT_LANGUAGE
        return Layout(presetId, language, order, hidden)
    }

    fun applyPreset(profileId: String, presetId: String) {
        val preset = UltraMaxCatalogRegistry.preset(presetId) ?: return
        prefs.edit()
            .putString(key(profileId, "preset"), preset.id)
            .putString(key(profileId, "order"), preset.rowIds.joinToString(","))
            .putStringSet(key(profileId, "hidden"), emptySet())
            .apply()
    }

    fun setLanguage(profileId: String, language: String) {
        if (language !in UltraMaxCatalogRegistry.languages) return
        prefs.edit().putString(key(profileId, "language"), language).apply()
    }

    fun setVisible(profileId: String, rowId: String, visible: Boolean) {
        if (UltraMaxCatalogRegistry.row(rowId) == null) return
        val hidden = load(profileId).hiddenRowIds.toMutableSet()
        if (visible) hidden.remove(rowId) else hidden.add(rowId)
        prefs.edit().putStringSet(key(profileId, "hidden"), hidden).apply()
    }

    fun reorder(profileId: String, orderedRowIds: List<String>) {
        val valid = orderedRowIds.distinct().filter { UltraMaxCatalogRegistry.row(it) != null }
        if (valid.isEmpty()) return
        prefs.edit().putString(key(profileId, "order"), valid.joinToString(",")).apply()
    }

    fun move(profileId: String, rowId: String, delta: Int) {
        val current = load(profileId).orderedRowIds.toMutableList()
        val from = current.indexOf(rowId)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, current.lastIndex)
        if (from == to) return
        current.removeAt(from)
        current.add(to, rowId)
        reorder(profileId, current)
    }

    fun reset(profileId: String) {
        prefs.edit()
            .remove(key(profileId, "preset"))
            .remove(key(profileId, "language"))
            .remove(key(profileId, "order"))
            .remove(key(profileId, "hidden"))
            .apply()
    }

    private fun key(profileId: String, suffix: String) = "${profileId.ifBlank { "default" }}_$suffix"

    companion object {
        private const val DEFAULT_PRESET = "quick_start"
        private const val DEFAULT_LANGUAGE = "English"
    }
}
