package com.astrawave.app.data

import com.astrawave.app.core.LiveProviderType
import com.astrawave.app.core.ProviderCapabilities

/** Product-level provider matrix. Secret-bearing credentials live in provider-specific secure stores. */
object PremiumProviderRegistry {
    val supported = listOf(
        ProviderCapabilities(LiveProviderType.M3U, supportsLive=true, supportsVod=false, supportsEpg=true, supportsHeaders=true),
        ProviderCapabilities(LiveProviderType.XTREAM, supportsLive=true, supportsVod=true, supportsEpg=true, supportsCatchUp=true, supportsDvr=true, supportsTimeshift=true, supportsHeaders=true),
        ProviderCapabilities(LiveProviderType.STALKER, supportsLive=true, supportsVod=true, supportsEpg=true, supportsCatchUp=true, supportsDvr=true, supportsTimeshift=true),
        ProviderCapabilities(LiveProviderType.JELLYFIN_LIVE_TV, supportsLive=true, supportsVod=true, supportsEpg=true, supportsDvr=true, supportsTimeshift=true, supportsRemoteRecording=true),
        ProviderCapabilities(LiveProviderType.PLEX_LIVE_TV, supportsLive=true, supportsVod=true, supportsEpg=true, supportsDvr=true, supportsTimeshift=true, supportsRemoteRecording=true),
        ProviderCapabilities(LiveProviderType.HDHOMERUN, supportsLive=true, supportsEpg=true, supportsDvr=true, supportsTimeshift=true),
        ProviderCapabilities(LiveProviderType.TVHEADEND, supportsLive=true, supportsEpg=true, supportsDvr=true, supportsCatchUp=true, supportsTimeshift=true, supportsRemoteRecording=true),
        ProviderCapabilities(LiveProviderType.ENIGMA2, supportsLive=true, supportsEpg=true, supportsDvr=true, supportsCatchUp=true, supportsTimeshift=true, supportsRemoteRecording=true),
        ProviderCapabilities(LiveProviderType.XMLTV, supportsLive=false, supportsEpg=true),
        ProviderCapabilities(LiveProviderType.WEBDAV, supportsLive=false, supportsVod=true),
        ProviderCapabilities(LiveProviderType.SMB, supportsLive=false, supportsVod=true),
        ProviderCapabilities(LiveProviderType.LOCAL, supportsLive=false, supportsVod=true),
    )

    fun capabilities(type:LiveProviderType)=supported.first{it.type==type}
}
