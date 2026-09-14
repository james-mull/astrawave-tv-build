package com.astrawave.app.data

import android.content.Context

/** Per-profile visibility, Home pinning and ordering for AstraWave's built-in catalogs. */
class BuiltInCatalogPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("astrawave_builtin_catalog_preferences_v1", Context.MODE_PRIVATE)

    fun visible(
        mediaType: BuiltInCatalogMediaType,
        profileId: String = "default",
    ): List<BuiltInCatalogDefinition> {
        val source = when (mediaType) {
            BuiltInCatalogMediaType.MOVIE -> AstraWaveBuiltInCatalogRegistry.movies
            BuiltInCatalogMediaType.SHOW -> AstraWaveBuiltInCatalogRegistry.shows
        }
        return source
            .filterNot { isHidden(profileId, it.id) }
            .map(CatalogServiceOverrides::apply)
            .sortedWith(compareBy<BuiltInCatalogDefinition> { order(profileId, it.id) }.thenBy { it.title })
    }

    fun isHidden(profileId: String, catalogId: String): Boolean =
        prefs.getBoolean(key(profileId, catalogId, "hidden"), false)

    fun setHidden(profileId: String, catalogId: String, hidden: Boolean) {
        prefs.edit().putBoolean(key(profileId, catalogId, "hidden"), hidden).apply()
    }

    fun isPinned(profileId: String, catalogId: String): Boolean =
        prefs.getBoolean(key(profileId, catalogId, "pinned"), false)

    fun setPinned(profileId: String, catalogId: String, pinned: Boolean) {
        prefs.edit().putBoolean(key(profileId, catalogId, "pinned"), pinned).apply()
    }

    fun order(profileId: String, catalogId: String): Int =
        prefs.getInt(key(profileId, catalogId, "order"), defaultOrder(catalogId))

    fun setOrder(profileId: String, catalogId: String, order: Int) {
        prefs.edit().putInt(key(profileId, catalogId, "order"), order.coerceAtLeast(0)).apply()
    }

    fun move(profileId: String, mediaType: BuiltInCatalogMediaType, catalogId: String, delta: Int) {
        val rows = visible(mediaType, profileId).toMutableList()
        val from = rows.indexOfFirst { it.id == catalogId }
        if (from < 0) return
        val to = (from + delta).coerceIn(0, rows.lastIndex)
        if (to == from) return
        val item = rows.removeAt(from)
        rows.add(to, item)
        prefs.edit().also { editor ->
            rows.forEachIndexed { index, row -> editor.putInt(key(profileId, row.id, "order"), index) }
        }.apply()
    }

    fun reset(profileId: String) {
        val prefix = "profile_${profileId.ifBlank { "default" }}_"
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
        editor.apply()
    }

    private fun defaultOrder(catalogId: String): Int =
        AstraWaveBuiltInCatalogRegistry.all.indexOfFirst { it.id == catalogId }.takeIf { it >= 0 } ?: 9999

    private fun key(profileId: String, catalogId: String, suffix: String): String =
        "profile_${profileId.ifBlank { "default" }}_${catalogId.replace(':', '_')}_$suffix"
}
