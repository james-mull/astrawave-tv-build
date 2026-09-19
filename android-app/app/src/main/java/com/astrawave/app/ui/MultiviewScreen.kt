package com.astrawave.app.ui

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.astrawave.app.core.MultiviewLayout
import com.astrawave.app.core.MultiviewPane
import com.astrawave.app.core.MultiviewSession

/** Premium 2/3/4/6/8-pane Media3 multiview with one audible pane and hardware-aware fallback. */
@Composable
fun MultiviewScreen(
    session: MultiviewSession,
    onActivateAudio: (String) -> Unit = {},
    onOpenPane: (MultiviewPane) -> Unit = {},
    onReplacePane: (MultiviewPane) -> Unit = {},
) {
    val context = LocalContext.current
    val paneLimit = remember(context) { safeMultiviewPaneLimit(context) }
    val effectivePanes = remember(session.panes, paneLimit) { session.panes.take(paneLimit) }
    val effectiveLayout = remember(session.layout, paneLimit, effectivePanes.size) {
        when {
            session.layout == MultiviewLayout.EIGHT_UP && paneLimit < 8 -> when {
                effectivePanes.size <= 2 -> MultiviewLayout.TWO_UP
                effectivePanes.size == 3 -> MultiviewLayout.THREE_UP
                effectivePanes.size <= 4 -> MultiviewLayout.FOUR_UP
                else -> MultiviewLayout.SIX_UP
            }
            paneLimit < 6 && session.layout in setOf(MultiviewLayout.SIX_UP, MultiviewLayout.SPORTS_MOSAIC) -> when (effectivePanes.size) {
                0, 1, 2 -> MultiviewLayout.TWO_UP
                3 -> MultiviewLayout.THREE_UP
                else -> MultiviewLayout.FOUR_UP
            }
            else -> session.layout
        }
    }
    val effectiveSession = remember(session, effectivePanes, effectiveLayout) {
        session.copy(layout = effectiveLayout, panes = effectivePanes)
    }

    var activeAudioPaneId by remember(session.id) { mutableStateOf(session.activeAudioPaneId ?: effectivePanes.firstOrNull()?.id) }
    LaunchedEffect(session.activeAudioPaneId, effectivePanes) {
        val requested = session.activeAudioPaneId
        activeAudioPaneId = when {
            requested != null && effectivePanes.any { it.id == requested } -> requested
            activeAudioPaneId != null && effectivePanes.any { it.id == activeAudioPaneId } -> activeAudioPaneId
            else -> effectivePanes.firstOrNull()?.id
        }
    }
    fun activate(paneId: String) { activeAudioPaneId = paneId; onActivateAudio(paneId) }

    Column(Modifier.fillMaxSize().background(AstraWaveColors.Background).padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    if (effectiveLayout == MultiviewLayout.SPORTS_MOSAIC) "SPORTS MOSAIC" else "MULTIVIEW",
                    color = AstraWaveColors.PrimaryText,
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    "${effectivePanes.size} STREAMS  •  SELECT A PANE FOR AUDIO",
                    color = AstraWaveColors.TertiaryText,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text("UP TO 8", color = AstraWaveColors.AccentStrong, style = MaterialTheme.typography.labelLarge)
        }
        if (session.panes.size > paneLimit) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Device safety: showing $paneLimit of ${session.panes.size} panes to avoid decoder/memory overload. Open any hidden event full-screen or replace an active pane.",
                color = AstraWaveColors.Warning,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Spacer(Modifier.height(10.dp))

        when (effectiveSession.layout) {
            MultiviewLayout.TWO_UP -> Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PaneOrEmpty(effectiveSession,0,Modifier.weight(1f),activeAudioPaneId,::activate,onOpenPane,onReplacePane)
                PaneOrEmpty(effectiveSession,1,Modifier.weight(1f),activeAudioPaneId,::activate,onOpenPane,onReplacePane)
            }
            MultiviewLayout.THREE_UP -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PaneOrEmpty(effectiveSession,0,Modifier.weight(1f).fillMaxWidth(),activeAudioPaneId,::activate,onOpenPane,onReplacePane)
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PaneOrEmpty(effectiveSession,1,Modifier.weight(1f),activeAudioPaneId,::activate,onOpenPane,onReplacePane)
                    PaneOrEmpty(effectiveSession,2,Modifier.weight(1f),activeAudioPaneId,::activate,onOpenPane,onReplacePane)
                }
            }
            MultiviewLayout.FOUR_UP -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(2) { row -> Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PaneOrEmpty(effectiveSession,row*2,Modifier.weight(1f),activeAudioPaneId,::activate,onOpenPane,onReplacePane)
                    PaneOrEmpty(effectiveSession,row*2+1,Modifier.weight(1f),activeAudioPaneId,::activate,onOpenPane,onReplacePane)
                } }
            }
            MultiviewLayout.SIX_UP, MultiviewLayout.SPORTS_MOSAIC -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(2) { row -> Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(3) { col -> PaneOrEmpty(effectiveSession,row*3+col,Modifier.weight(1f),activeAudioPaneId,::activate,onOpenPane,onReplacePane) }
                } }
            }
            MultiviewLayout.EIGHT_UP -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(2) { row -> Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(4) { col -> PaneOrEmpty(effectiveSession,row*4+col,Modifier.weight(1f),activeAudioPaneId,::activate,onOpenPane,onReplacePane) }
                } }
            }
        }
    }
}

private fun safeMultiviewPaneLimit(context: Context): Int {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 4
    return when {
        manager.isLowRamDevice || manager.memoryClass < 256 -> 4
        manager.memoryClass < 384 -> 6
        else -> 8
    }
}

@Composable
private fun PaneOrEmpty(session: MultiviewSession,index:Int,modifier:Modifier,activeAudioPaneId:String?,onActivateAudio:(String)->Unit,onOpenPane:(MultiviewPane)->Unit,onReplacePane:(MultiviewPane)->Unit){
    val pane=session.panes.getOrNull(index)
    if(pane==null){Box(modifier.background(AstraWaveColors.BackgroundRaised).padding(12.dp),contentAlignment=Alignment.Center){Text("+ ADD STREAM",color=AstraWaveColors.TertiaryText,style=MaterialTheme.typography.labelLarge)};return}
    val activeAudio=activeAudioPaneId==pane.id
    AstraWaveFocusableCard(modifier.clickable{onActivateAudio(pane.id)}){Column{
        MultiviewPlayerSurface(pane,activeAudio,Modifier.weight(1f).fillMaxWidth());Spacer(Modifier.height(8.dp))
        Text(pane.title,color=AstraWaveColors.PrimaryText,style=MaterialTheme.typography.titleMedium,maxLines=1)
        Text(pane.sourceName,color=AstraWaveColors.Accent,style=MaterialTheme.typography.labelMedium,maxLines=1)
        Spacer(Modifier.height(5.dp));Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){
            Text(if(activeAudio)"Audio active" else "Select audio",color=if(activeAudio)AstraWaveColors.Success else AstraWaveColors.SecondaryText,style=MaterialTheme.typography.labelMedium)
            Text("Open",color=AstraWaveColors.PrimaryText,modifier=Modifier.clickable{onOpenPane(pane)},style=MaterialTheme.typography.labelMedium)
            Text("Replace",color=AstraWaveColors.SecondaryText,modifier=Modifier.clickable{onReplacePane(pane)},style=MaterialTheme.typography.labelMedium)
        }
    }}
}

@Composable
private fun MultiviewPlayerSurface(pane:MultiviewPane,audible:Boolean,modifier:Modifier=Modifier){
    val context=LocalContext.current
    var playbackError by remember(pane.id,pane.streamUrl){ mutableStateOf<String?>(null) }
    val player=remember(pane.id,pane.streamUrl){ExoPlayer.Builder(context).build().apply{
        repeatMode=Player.REPEAT_MODE_OFF
        setMediaItem(MediaItem.fromUri(pane.streamUrl))
        volume=if(audible)1f else 0f
        addListener(object:Player.Listener{
            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.errorCodeName
            }
            override fun onPlaybackStateChanged(playbackState:Int){
                if(playbackState==Player.STATE_READY) playbackError=null
            }
        })
        prepare();playWhenReady=true
    }}
    LaunchedEffect(audible,player){player.volume=if(audible)1f else 0f}
    DisposableEffect(player){onDispose{player.release()}}
    Box(modifier.background(AstraWaveColors.Background)) {
        AndroidView(modifier=Modifier.fillMaxSize(),factory={PlayerView(it).apply{useController=false;this.player=player}},update={it.player=player})
        playbackError?.let {
            Box(Modifier.fillMaxSize().background(AstraWaveColors.Background.copy(alpha=.78f)).padding(12.dp), contentAlignment=Alignment.Center) {
                Text("Pane unavailable • $it\nOpen full-screen or replace this source.", color=AstraWaveColors.Warning, style=MaterialTheme.typography.labelMedium)
            }
        }
    }
}
