package com.astrawave.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Profile-scoped sports personalization used by Sports Home and Astra concierge. */
class SportsPreferenceStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs=appContext.getSharedPreferences("astrawave_sports_preferences_v1",Context.MODE_PRIVATE)

    data class FavoriteTeam(val id:String,val name:String,val league:String?=null)
    data class Reminder(val id:String,val eventId:String,val title:String,val startTime:String,val enabled:Boolean=true)

    fun teams(profileId:String):List<FavoriteTeam>{val raw=prefs.getString("teams_$profileId",null)?:return emptyList();return runCatching{val a=JSONArray(raw);buildList{for(i in 0 until a.length()){val o=a.getJSONObject(i);add(FavoriteTeam(o.getString("id"),o.getString("name"),o.optString("league").takeIf{it.isNotBlank()}))}}}.getOrDefault(emptyList())}
    fun toggleTeam(profileId:String,team:FavoriteTeam):Boolean{val current=teams(profileId).toMutableList();val index=current.indexOfFirst{it.id==team.id};val enabled=index<0;if(enabled)current+=team else current.removeAt(index);val a=JSONArray();current.forEach{a.put(JSONObject().put("id",it.id).put("name",it.name).put("league",it.league?:JSONObject.NULL))};prefs.edit().putString("teams_$profileId",a.toString()).apply();return enabled}

    fun reminders(profileId:String):List<Reminder>{val raw=prefs.getString("reminders_$profileId",null)?:return emptyList();return runCatching{val a=JSONArray(raw);buildList{for(i in 0 until a.length()){val o=a.getJSONObject(i);add(Reminder(o.getString("id"),o.getString("eventId"),o.getString("title"),o.getString("startTime"),o.optBoolean("enabled",true)))}}}.getOrDefault(emptyList())}
    fun saveReminder(profileId:String,item:Reminder){
        val all=reminders(profileId).filterNot{it.id==item.id}+item
        val a=JSONArray();all.forEach{a.put(JSONObject().put("id",it.id).put("eventId",it.eventId).put("title",it.title).put("startTime",it.startTime).put("enabled",it.enabled))}
        prefs.edit().putString("reminders_$profileId",a.toString()).apply()
        SportsReminderScheduler.schedule(appContext,profileId,item)
    }
    fun removeReminder(profileId:String,id:String){
        val all=reminders(profileId).filterNot{it.id==id};val a=JSONArray();all.forEach{a.put(JSONObject().put("id",it.id).put("eventId",it.eventId).put("title",it.title).put("startTime",it.startTime).put("enabled",it.enabled))};prefs.edit().putString("reminders_$profileId",a.toString()).apply()
        SportsReminderScheduler.cancel(appContext,profileId,id)
    }
}
