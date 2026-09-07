package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.core.CatchUpItem
import com.astrawave.app.core.CatchUpProgram
import com.astrawave.app.core.DvrEligibility
import com.astrawave.app.core.DvrGateway
import com.astrawave.app.core.LiveSourceCapabilities
import com.astrawave.app.core.Recording
import com.astrawave.app.core.RecordingRequest
import com.astrawave.app.core.RecordingState
import com.astrawave.app.core.TimeshiftSession
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Local DVR/catch-up orchestration for explicitly capable authorized providers. */
class LocalDvrGateway(private val context: Context) : DvrGateway {
    private val prefs = context.getSharedPreferences("astrawave_dvr_v1", Context.MODE_PRIVATE)

    fun registerCapabilities(capabilities: LiveSourceCapabilities) {
        val all = loadCapabilities().toMutableMap(); all[capabilities.sourceId] = capabilities
        val root = JSONObject()
        all.values.forEach { cap -> root.put(cap.sourceId, JSONObject()
            .put("supportsDvr",cap.supportsDvr).put("supportsCatchUp",cap.supportsCatchUp).put("supportsTimeshift",cap.supportsTimeshift)
            .put("catchUpWindowHours",cap.catchUpWindowHours?:JSONObject.NULL).put("maxRecordingHours",cap.maxRecordingHours?:JSONObject.NULL)
            .put("catchUpUrlTemplate",cap.catchUpUrlTemplate?:JSONObject.NULL).put("supportsSeriesRecording",cap.supportsSeriesRecording)
            .put("recordingStorageLabel",cap.recordingStorageLabel?:JSONObject.NULL)) }
        prefs.edit().putString(KEY_CAPABILITIES,root.toString()).apply()
    }

    fun registerCatchUpPrograms(programs: List<CatchUpProgram>) {
        val all = loadCatchUpPrograms().filterNot { existing -> programs.any { it.sourceId==existing.sourceId && it.programId==existing.programId } } + programs
        val a=JSONArray();all.takeLast(MAX_CATCHUP_PROGRAMS).forEach { p -> a.put(JSONObject().put("sourceId",p.sourceId).put("channelId",p.channelId).put("programId",p.programId).put("title",p.title).put("start",p.startEpochMs).put("end",p.endEpochMs)) }
        prefs.edit().putString(KEY_CATCHUP,a.toString()).apply()
    }

    override fun capabilities(sourceId:String):LiveSourceCapabilities=loadCapabilities()[sourceId]?:LiveSourceCapabilities(sourceId=sourceId)

    override fun schedule(request:RecordingRequest):Recording {
        val cap=capabilities(request.sourceId)
        val errors=DvrEligibility.validateRequest(request,cap)
        require(errors.isEmpty()){errors.joinToString(" • ")}
        val conflict=loadRecordings().firstOrNull { existing ->
            existing.request.id != request.id &&
                existing.request.sourceId == request.sourceId &&
                existing.state !in setOf(RecordingState.COMPLETE,RecordingState.FAILED,RecordingState.CANCELED) &&
                DvrEligibility.overlaps(existing.request,request)
        }
        require(conflict==null){"Recording conflicts with ${conflict?.request?.title ?: "another recording"} on this source"}
        val recording=Recording(request,RecordingState.SCHEDULED)
        upsert(recording)
        DvrRecordingScheduler.schedule(context,recording)
        return recording
    }

    override fun cancel(recordingId:String):Boolean {
        val all=loadRecordings().toMutableList();val i=all.indexOfFirst{it.request.id==recordingId};if(i<0)return false
        all[i]=all[i].copy(state=RecordingState.CANCELED,error="Canceled")
        writeRecordings(all)
        DvrRecordingScheduler.cancel(context,recordingId)
        return true
    }

    override fun recordings(profileId:String):List<Recording> = loadRecordings().filter{it.request.profileId==profileId}.sortedByDescending{it.request.startEpochMs}

    fun find(recordingId:String):Recording? = loadRecordings().firstOrNull{it.request.id==recordingId}

    fun updateRecording(recordingId:String,state:RecordingState,playbackUrl:String?=null,error:String?=null):Recording? {
        val all=loadRecordings().toMutableList();val i=all.indexOfFirst{it.request.id==recordingId};if(i<0)return null
        val current=all[i]
        if(current.state==RecordingState.CANCELED && state!=RecordingState.CANCELED)return current
        val updated=current.copy(state=state,playbackUrl=playbackUrl?:current.playbackUrl,error=error)
        all[i]=updated;writeRecordings(all);return updated
    }

    override fun catchUp(channelId:String,fromEpochMs:Long,toEpochMs:Long):List<CatchUpItem> = loadCatchUpPrograms().filter { it.channelId==channelId && it.endEpochMs>=fromEpochMs && it.startEpochMs<=toEpochMs }.mapNotNull { program ->
        val cap=capabilities(program.sourceId);val template=cap.catchUpUrlTemplate?.takeIf{DvrEligibility.canCatchUp(cap)}?:return@mapNotNull null
        val now=System.currentTimeMillis();val window=cap.catchUpWindowHours?.times(3_600_000L)
        if(window!=null && program.endEpochMs < now-window)return@mapNotNull null
        val duration=((program.endEpochMs-program.startEpochMs)/1000L).coerceAtLeast(1)
        val url=template.replace("{channelId}",enc(program.channelId)).replace("{start}",(program.startEpochMs/1000L).toString()).replace("{end}",(program.endEpochMs/1000L).toString()).replace("{duration}",duration.toString())
        if(!url.startsWith("http://")&&!url.startsWith("https://"))return@mapNotNull null
        CatchUpItem(program.sourceId,program.channelId,program.programId,program.title,program.startEpochMs,program.endEpochMs,url)
    }.sortedByDescending{it.startEpochMs}

    override fun startTimeshift(sourceId:String,channelId:String):TimeshiftSession? { val cap=capabilities(sourceId);if(!DvrEligibility.canTimeshift(cap)||channelId.isBlank())return null;val now=System.currentTimeMillis();val windowMs=(cap.catchUpWindowHours?:2).coerceAtLeast(1)*3_600_000L;return TimeshiftSession("timeshift:$sourceId:$channelId:$now",sourceId,channelId,now,now-windowMs,now) }

    private fun upsert(recording:Recording){val all=loadRecordings().toMutableList();val i=all.indexOfFirst{it.request.id==recording.request.id};if(i>=0)all[i]=recording else all+=recording;writeRecordings(all)}

    private fun loadCapabilities():Map<String,LiveSourceCapabilities>{val raw=prefs.getString(KEY_CAPABILITIES,null)?:return emptyMap();return runCatching{val root=JSONObject(raw);buildMap{root.keys().forEach{sourceId->val o=root.getJSONObject(sourceId);put(sourceId,LiveSourceCapabilities(sourceId,o.optBoolean("supportsDvr"),o.optBoolean("supportsCatchUp"),o.optBoolean("supportsTimeshift"),o.optInt("catchUpWindowHours").takeIf{!o.isNull("catchUpWindowHours")},o.optInt("maxRecordingHours").takeIf{!o.isNull("maxRecordingHours")},o.optString("catchUpUrlTemplate").takeIf{it.isNotBlank()&&it!="null"},o.optBoolean("supportsSeriesRecording"),o.optString("recordingStorageLabel").takeIf{it.isNotBlank()&&it!="null"}))}}}.getOrDefault(emptyMap())}
    private fun loadCatchUpPrograms():List<CatchUpProgram>{val raw=prefs.getString(KEY_CATCHUP,null)?:return emptyList();return runCatching{val a=JSONArray(raw);buildList{for(i in 0 until a.length()){val o=a.getJSONObject(i);add(CatchUpProgram(o.getString("sourceId"),o.getString("channelId"),o.getString("programId"),o.getString("title"),o.getLong("start"),o.getLong("end")))}}}.getOrDefault(emptyList())}

    private fun loadRecordings():List<Recording>{val raw=prefs.getString(KEY_RECORDINGS,null)?:return emptyList();return runCatching{val a=JSONArray(raw);buildList{for(i in 0 until a.length()){val o=a.getJSONObject(i);val r=o.getJSONObject("request");val urls=r.optJSONArray("authorizedStreamUrls")?.let{array->buildList{for(j in 0 until array.length())add(array.optString(j))}}?:emptyList();add(Recording(RecordingRequest(r.getString("id"),r.getString("profileId"),r.getString("sourceId"),r.getString("channelId"),r.getString("title"),r.getLong("startEpochMs"),r.getLong("endEpochMs"),r.optString("seriesId").takeIf{it.isNotBlank()&&it!="null"},r.optString("eventId").takeIf{it.isNotBlank()&&it!="null"},urls),runCatching{RecordingState.valueOf(o.getString("state"))}.getOrDefault(RecordingState.FAILED),o.optString("playbackUrl").takeIf{it.isNotBlank()&&it!="null"},o.optString("error").takeIf{it.isNotBlank()&&it!="null"}))}}}.getOrDefault(emptyList())}
    private fun writeRecordings(items:List<Recording>){val a=JSONArray();items.forEach{rec->val r=rec.request;val urls=JSONArray();r.authorizedStreamUrls.forEach(urls::put);a.put(JSONObject().put("state",rec.state.name).put("playbackUrl",rec.playbackUrl?:JSONObject.NULL).put("error",rec.error?:JSONObject.NULL).put("request",JSONObject().put("id",r.id).put("profileId",r.profileId).put("sourceId",r.sourceId).put("channelId",r.channelId).put("title",r.title).put("startEpochMs",r.startEpochMs).put("endEpochMs",r.endEpochMs).put("seriesId",r.seriesId?:JSONObject.NULL).put("eventId",r.eventId?:JSONObject.NULL).put("authorizedStreamUrls",urls)))};prefs.edit().putString(KEY_RECORDINGS,a.toString()).apply()}
    private fun enc(v:String)=URLEncoder.encode(v,StandardCharsets.UTF_8.name())
    private companion object { const val KEY_CAPABILITIES="capabilities";const val KEY_RECORDINGS="recordings";const val KEY_CATCHUP="catchup_programs";const val MAX_CATCHUP_PROGRAMS=5000 }
}
