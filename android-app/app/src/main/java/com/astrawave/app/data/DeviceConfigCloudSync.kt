package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.core.IptvSource
import com.astrawave.app.core.IptvSourceType
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot

/** Synchronizes the signed-in user's AstraWave web Control Center configuration into the TV app. */
class DeviceConfigCloudSync(private val context: Context) {
    private val appContext = context.applicationContext
    private val ready = AstraWaveFirebase.initialize(appContext)
    private val auth: FirebaseAuth? get() = if (ready) FirebaseAuth.getInstance() else null
    private val db: FirebaseFirestore? get() = if (ready) FirebaseFirestore.getInstance() else null
    private val syncPrefs = appContext.getSharedPreferences("astrawave_device_cloud_sync", Context.MODE_PRIVATE)

    data class RestoreReport(val configVersion:Long,val iptvSourcesImported:Int,val stremioAddonsApplied:Int,val cloudStreamReposApplied:Int)

    fun startLiveSync(profileId:String="default") {
        val uid=auth?.currentUser?.uid?:return
        val database=db?:return
        val key="$uid:$profileId"
        synchronized(activeListeners){
            if(activeListeners.containsKey(key))return
            val settings=database.collection("users").document(uid).collection("settings").document("app")
                .addSnapshotListener{_,error->if(error==null)restore(profileId){}}
            val sources=database.collection("users").document(uid).collection("sources")
                .addSnapshotListener{_,error->if(error==null)restore(profileId){}}
            activeListeners[key]=listOf(settings,sources)
        }
    }

    fun restore(profileId:String="default",onComplete:(Result<RestoreReport>)->Unit){
        val uid=auth?.currentUser?.uid;val database=db
        if(uid==null||database==null){onComplete(Result.success(RestoreReport(0,0,0,0)));return}
        val configTask=database.collection("users").document(uid).collection("settings").document("app").get()
        val sourcesTask=database.collection("users").document(uid).collection("sources").get()
        Tasks.whenAllSuccess<Any>(configTask,sourcesTask).addOnSuccessListener{values->
            val result=runCatching{apply(profileId,values[0] as DocumentSnapshot,values[1] as QuerySnapshot)}
            if(result.isSuccess)startLiveSync(profileId)
            onComplete(result)
        }.addOnFailureListener{onComplete(Result.failure(it))}
    }

    private fun apply(profileId:String,config:DocumentSnapshot,sources:QuerySnapshot):RestoreReport{
        val importedSources=sources.documents.mapNotNull{doc->
            val enabled=doc.getBoolean("enabled")?:true
            val type=doc.getString("type").orEmpty().uppercase()
            val rawConfig=doc.get("config") as? Map<*,*>?:emptyMap<Any,Any>()
            when(type){
                "M3U","PUBLIC"->{
                    val url=rawConfig["m3uUrl"]?.toString()?.takeIf{it.isNotBlank()&&it!="null"}?:return@mapNotNull null
                    IptvSource(id=doc.id,profileId=profileId,name=doc.getString("name").orEmpty().ifBlank{"Cloud IPTV"},type=IptvSourceType.M3U,enabled=enabled,priority=(doc.getLong("priority")?:20L).toInt(),m3uUrl=url,xmlTvUrl=rawConfig["xmlTvUrl"]?.toString()?.takeIf{it.isNotBlank()&&it!="null"})
                }
                "XTREAM"->{
                    val server=rawConfig["server"]?.toString()?.takeIf{it.isNotBlank()}?:return@mapNotNull null
                    val username=rawConfig["username"]?.toString()?.takeIf{it.isNotBlank()}?:return@mapNotNull null
                    val password=rawConfig["password"]?.toString()?.takeIf{it.isNotBlank()}?:return@mapNotNull null
                    IptvSource(id=doc.id,profileId=profileId,name=doc.getString("name").orEmpty().ifBlank{"Cloud Xtream"},type=IptvSourceType.XTREAM,enabled=enabled,priority=(doc.getLong("priority")?:20L).toInt(),xtreamServer=server,xtreamUsername=username,xtreamPassword=password,xmlTvUrl=rawConfig["xmlTvUrl"]?.toString()?.takeIf{it.isNotBlank()&&it!="null"})
                }
                else->null
            }
        }
        val sourceStore=IptvSourceStore(appContext)
        val previousCloudIds=syncPrefs.getStringSet("$profileId:cloudSourceIds",emptySet()).orEmpty()
        val newCloudIds=importedSources.map{it.id}.toSet()
        val localOnly=sourceStore.load(profileId).filterNot{it.id in previousCloudIds||it.id in newCloudIds}
        sourceStore.save(profileId,localOnly+importedSources)
        syncPrefs.edit().putStringSet("$profileId:cloudSourceIds",newCloudIds).apply()

        val addons=config.get("addons") as? List<*>?:emptyList<Any>()
        val stremioStore=StremioAddonStore(appContext)
        var stremioApplied=0
        val cloudRepos=mutableListOf<CloudStreamRepositoryPreference>()
        addons.forEach{raw->
            val map=raw as? Map<*,*>?:return@forEach
            val id=map["id"]?.toString().orEmpty();val name=map["name"]?.toString().orEmpty();val kind=map["kind"]?.toString().orEmpty()
            val url=map["url"]?.toString()?.takeIf{it.isNotBlank()&&it!="null"};val enabled=map["enabled"] as? Boolean?:false;val custom=map["custom"] as? Boolean?:false
            when(kind){
                "stremio"->if(url!=null)runCatching{val addon=stremioStore.install(url);stremioStore.setEnabled(addon.manifest.id,enabled);stremioApplied++}
                "cloudstream"->if(url!=null)cloudRepos+=CloudStreamRepositoryPreference(id=id.ifBlank{"cloud-${url.hashCode()}"},name=name.ifBlank{"CloudStream Repo"},url=url,enabled=enabled,custom=custom)
            }
        }
        CloudStreamRepositoryPreferenceStore(appContext).save(profileId,cloudRepos)

        val editor=appContext.getSharedPreferences("astrawave_experience",Context.MODE_PRIVATE).edit()
        config.getString("theme")?.let{editor.putString("$profileId:theme",if(it=="dark")"AstraWave" else it)}
        config.getString("homeDensity")?.let{editor.putString("$profileId:density",if(it.equals("compact",true))"Compact" else "Standard")}
        config.getBoolean("autoplayTrailers")?.let{editor.putBoolean("$profileId:autoplayTrailers",it)}
        config.getBoolean("aiDiscovery")?.let{editor.putBoolean("$profileId:aiDiscovery",it)}
        config.getString("preferredLanguage")?.let{editor.putString("$profileId:preferredLanguage",it)}
        config.getString("preferredRegion")?.let{editor.putString("$profileId:preferredRegion",it)}
        config.getString("activeLiveSource")?.let{editor.putString("$profileId:activeLiveSource",it)}
        editor.apply()
        return RestoreReport(configVersion=config.getLong("version")?:0L,iptvSourcesImported=importedSources.size,stremioAddonsApplied=stremioApplied,cloudStreamReposApplied=cloudRepos.size)
    }

    companion object{private val activeListeners=mutableMapOf<String,List<ListenerRegistration>>()}
}