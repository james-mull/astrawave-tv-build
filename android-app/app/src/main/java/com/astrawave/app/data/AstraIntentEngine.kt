package com.astrawave.app.data

import com.astrawave.app.core.AstraIntent
import com.astrawave.app.core.AstraIntentType

/** Lightweight on-device intent parser. A hosted AI model can enrich this contract later. */
object AstraIntentEngine {
    fun parse(query: String): AstraIntent {
        val raw = query.trim()
        val q = raw.lowercase()
        val runtime = Regex("(?:under|less than|within)\\s+(\\d{2,3})\\s*(?:min|minutes)").find(q)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Regex("(?:under|less than)\\s+(\\d(?:\\.\\d+)?)\\s*hours?").find(q)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let { (it * 60).toInt() }
        val rating = Regex("(?:rated|rating|over|above)\\s+(\\d(?:\\.\\d+)?)").find(q)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        val similar = Regex("(?:like|similar to)\\s+(.+)$").find(raw, RegexOption.IGNORE_CASE)?.groupValues?.getOrNull(1)?.trim()
        val team = Regex("(?:put|play|watch|show)\\s+(?:the\\s+)?(.+?)\\s+(?:game|match)").find(raw, RegexOption.IGNORE_CASE)?.groupValues?.getOrNull(1)?.trim()
        val type = when {
            q.contains("sports") && (q.contains("tonight") || q.contains("today")) -> AstraIntentType.SPORTS_TONIGHT
            team != null -> AstraIntentType.PLAY_TEAM
            q.contains("new this week") || q.contains("new this weekend") -> AstraIntentType.NEW_THIS_WEEK
            q.contains("guide") -> AstraIntentType.OPEN_GUIDE
            similar != null -> AstraIntentType.SIMILAR_TO
            runtime != null -> AstraIntentType.FIND_SHORT_WATCH
            q.contains("movie") -> AstraIntentType.FIND_MOVIE
            q.contains("show") || q.contains("series") || q.contains("tv") -> AstraIntentType.FIND_SHOW
            q.contains("find") || q.contains("recommend") || q.contains("something") -> AstraIntentType.DISCOVER
            else -> AstraIntentType.UNKNOWN
        }
        return AstraIntent(type=type,rawQuery=raw,titleHint=similar,teamHint=team,maxRuntimeMinutes=runtime,minRating=rating)
    }
}
