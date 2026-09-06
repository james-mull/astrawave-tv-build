package com.astrawave.app.data

import android.content.Context
import android.content.SharedPreferences
import com.astrawave.app.core.ChannelCustomization
import org.json.JSONArray
import org.json.JSONObject

/** Profile-scoped channel numbers, aliases, groups, hide/order, EPG mapping and logo repair overrides. */
class ChannelCustomizationStore(context: Context) {
    private val prefs=context.getSharedPreferences("astrawave_channel_customization_v1",Context.MODE_PRIVATE)

    fun load(profileId:String):List<ChannelCustomization>{
        val raw=prefs.getString("channels_$profileId",null)?:return emptyList()
        return runCatching{val a=JSONArray(raw);buildList{for(i in 0 until a.length()){val o=a.getJSONObject(i);add(ChannelCustomization(
            channelId=o.getString("channelId"),customName=o.optString("customName").takeIf{it.isNotBlank()},customNumber=o.optInt("customNumber").takeIf{!o.isNull("customNumber")},customGroup=o.optString("customGroup").takeIf{it.isNotBlank()},hidden=o.optBoolean("hidden"),sortOrder=o.optInt("sortOrder"),epgIdOverride=o.optString("epgIdOverride").takeIf{it.isNotBlank()},logoUrlOverride=o.optString("logoUrlOverride").takeIf{it.isNotBlank()}
        ))}}}.getOrDefault(emptyList())
    }

    fun save(profileId:String,item:ChannelCustomization){saveAll(profileId,load(profileId).filterNot{it.channelId==item.channelId}+item)}
    fun remove(profileId:String,channelId:String){saveAll(profileId,load(profileId).filterNot{it.channelId==channelId})}

    fun apply(profileId:String,row:GuideChannelRow):GuideChannelRow{
        val c=load(profileId).firstOrNull{it.channelId==row.id}?:return row
        return row.copy(name=c.customName?:row.name,group=c.customGroup?:row.group,logo=c.logoUrlOverride?:row.logo)
    }
    fun visible(profileId:String,rows:List<GuideChannelRow>):List<GuideChannelRow>{
        val map=load(profileId).associateBy{it.channelId}
        return rows.filterNot{map[it.id]?.hidden==true}.map{row->apply(profileId,row)}.sortedWith(compareBy<GuideChannelRow>{map[it.id]?.sortOrder?:Int.MAX_VALUE}.thenBy{map[it.id]?.customNumber?:Int.MAX_VALUE}.thenBy{it.name})
    }

    /**
     * Observe profile-scoped channel customization writes. This is intentionally backed by
     * SharedPreferences so local edits and DeviceConfigCloudSync imports use the same signal.
     * Call the returned function when the observing screen leaves composition.
     */
    fun observe(profileId:String,onChanged:()->Unit):()->Unit{
        val key="channels_$profileId"
        val listener=SharedPreferences.OnSharedPreferenceChangeListener{_,changedKey->
            if(changedKey==key)onChanged()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private fun saveAll(profileId:String,items:List<ChannelCustomization>){val a=JSONArray();items.forEach{c->a.put(JSONObject().put("channelId",c.channelId).put("customName",c.customName?:JSONObject.NULL).put("customNumber",c.customNumber?:JSONObject.NULL).put("customGroup",c.customGroup?:JSONObject.NULL).put("hidden",c.hidden).put("sortOrder",c.sortOrder).put("epgIdOverride",c.epgIdOverride?:JSONObject.NULL).put("logoUrlOverride",c.logoUrlOverride?:JSONObject.NULL))};prefs.edit().putString("channels_$profileId",a.toString()).apply()}
}
