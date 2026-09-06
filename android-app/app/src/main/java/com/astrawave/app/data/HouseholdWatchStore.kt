package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.core.HouseholdCandidate
import com.astrawave.app.core.HouseholdVote
import com.astrawave.app.core.HouseholdVoteValue
import com.astrawave.app.core.HouseholdWatchSession
import org.json.JSONArray
import org.json.JSONObject

/** Local-first household Movie Night / Watch Night voting state. */
class HouseholdWatchStore(context: Context) {
    private val prefs = context.getSharedPreferences("astrawave_household_watch_v1", Context.MODE_PRIVATE)

    fun sessions(): List<HouseholdWatchSession> = decode().sortedByDescending { it.createdAtEpochMs }
    fun session(id: String): HouseholdWatchSession? = sessions().firstOrNull { it.id == id }

    fun save(session: HouseholdWatchSession) {
        val all = decode().filterNot { it.id == session.id }
        write(all + session)
    }

    fun vote(sessionId: String, profileId: String, mediaId: String, value: HouseholdVoteValue): HouseholdWatchSession? {
        val current = session(sessionId) ?: return null
        val nextVotes = current.votes.filterNot { it.profileId == profileId && it.mediaId == mediaId } + HouseholdVote(profileId, mediaId, value)
        val next = current.copy(votes = nextVotes)
        save(next)
        return next
    }

    fun remove(sessionId: String) { write(decode().filterNot { it.id == sessionId }) }

    private fun decode(): List<HouseholdWatchSession> {
        val raw=prefs.getString(KEY,null)?:return emptyList()
        return runCatching {
            val a=JSONArray(raw);buildList {
                for(i in 0 until a.length()){
                    val o=a.getJSONObject(i)
                    val profiles=o.optJSONArray("profiles")?:JSONArray();val profileIds=buildList{for(j in 0 until profiles.length())add(profiles.getString(j))}
                    val candidatesRaw=o.optJSONArray("candidates")?:JSONArray();val candidates=buildList{for(j in 0 until candidatesRaw.length()){val c=candidatesRaw.getJSONObject(j);add(HouseholdCandidate(c.getString("mediaId"),c.optString("mediaType","movie"),c.optString("title","Untitled"),c.optString("posterUrl").takeIf{it.isNotBlank()}))}}
                    val votesRaw=o.optJSONArray("votes")?:JSONArray();val votes=buildList{for(j in 0 until votesRaw.length()){val v=votesRaw.getJSONObject(j);add(HouseholdVote(v.getString("profileId"),v.getString("mediaId"),runCatching{HouseholdVoteValue.valueOf(v.getString("value"))}.getOrDefault(HouseholdVoteValue.MAYBE),v.optLong("votedAtEpochMs")))}}
                    add(HouseholdWatchSession(o.getString("id"),o.optString("name","Movie Night"),profileIds,candidates,votes,o.optLong("createdAtEpochMs")))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun write(items:List<HouseholdWatchSession>){
        val a=JSONArray();items.forEach{s->
            val profiles=JSONArray();s.profileIds.forEach(profiles::put)
            val candidates=JSONArray();s.candidates.forEach{c->candidates.put(JSONObject().put("mediaId",c.mediaId).put("mediaType",c.mediaType).put("title",c.title).put("posterUrl",c.posterUrl?:JSONObject.NULL))}
            val votes=JSONArray();s.votes.forEach{v->votes.put(JSONObject().put("profileId",v.profileId).put("mediaId",v.mediaId).put("value",v.value.name).put("votedAtEpochMs",v.votedAtEpochMs))}
            a.put(JSONObject().put("id",s.id).put("name",s.name).put("profiles",profiles).put("candidates",candidates).put("votes",votes).put("createdAtEpochMs",s.createdAtEpochMs))
        };prefs.edit().putString(KEY,a.toString()).apply()
    }

    companion object { private const val KEY="sessions" }
}
