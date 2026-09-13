'use client';

import Link from 'next/link';
import { useEffect, useMemo, useRef, useState } from 'react';
import { ArrowLeft, Bookmark, Check, ChevronRight, ExternalLink, Film, Loader2, Play, ShieldCheck, Tv2 } from 'lucide-react';
import { AstraWaveApi, CatalogItem, SourceCandidate, TitleDetails } from '../../../../../lib/astrawave-api';

type EpisodeItem={id:string;season:number;episode:number;title:string;overview?:string;released?:string;thumbnail?:string;runtimeMinutes?:number};
type EpisodePayload={seriesId:string;title?:string|null;episodes:EpisodeItem[]};
type ManifestInput={name?:string;url:string};

function sourceScore(source:SourceCandidate){
  const quality=String(source.quality||'').toLowerCase();
  const resolution=/2160|4k/.test(quality)?50:/1080/.test(quality)?38:/720/.test(quality)?24:/480/.test(quality)?12:0;
  const direct=source.direct?22:0;
  const bitrate=Math.min(Math.max(Number(source.bitrateKbps||0)/1000,0),18);
  const uptime=source.uptimePercent!=null?Math.min(source.uptimePercent/8,12):0;
  const latency=source.latencyMs!=null?Math.max(0,10-Math.min(source.latencyMs/250,10)):0;
  const format=/hevc|h265/i.test(`${source.codec||''} ${source.quality||''}`)?5:0;
  return resolution+direct+bitrate+uptime+latency+format;
}

function rankSources(sources:SourceCandidate[]){return [...sources].sort((a,b)=>sourceScore(b)-sourceScore(a)||String(b.quality||'').localeCompare(String(a.quality||'')))}
function playbackKey(kind:string,id:string){return `astrawave:vod-progress:${kind}:${id}`}
function watchlistKey(){return 'astrawave:vod-watchlist'}
function episodePlaybackId(seriesId:string,episode:EpisodeItem){return `${seriesId}:s${episode.season}:e${episode.episode}`}
function enabledStremioManifests():ManifestInput[]{
  try{
    const raw=JSON.parse(localStorage.getItem('astrawave:vod-stremio-manifests')||'[]') as Array<{name?:string;url?:string;enabled?:boolean}>;
    const seen=new Set<string>();
    return raw.flatMap(item=>{const url=String(item?.url||'').trim();if(!url||item.enabled===false||seen.has(url))return[];seen.add(url);return[{name:item.name,url}]}).slice(0,12);
  }catch{return[]}
}

function SmartVodPlayer({kind,id,sources,selectedIndex,onSelectedIndex,onEnded}:{kind:string;id:string;sources:SourceCandidate[];selectedIndex:number;onSelectedIndex:(index:number)=>void;onEnded?:()=>void}){
  const videoRef=useRef<HTMLVideoElement|null>(null);
  const [message,setMessage]=useState('');
  const selected=sources[selectedIndex];

  useEffect(()=>{
    const video=videoRef.current;if(!video||!selected?.url)return;
    setMessage('');
    try{const saved=Number(localStorage.getItem(playbackKey(kind,id))||0);if(saved>5)video.currentTime=saved}catch{}
    const save=()=>{try{if(video.currentTime>1)localStorage.setItem(playbackKey(kind,id),String(video.currentTime))}catch{}};
    const fail=()=>{
      const next=selectedIndex+1;
      if(next<sources.length){setMessage(`Source ${selectedIndex+1} failed. AstraWave is trying backup ${next+1} of ${sources.length}.`);onSelectedIndex(next)}
      else setMessage('All ranked browser-playable candidates failed. Try another authorized source or the Android/TV app.');
    };
    const ended=()=>{try{localStorage.removeItem(playbackKey(kind,id))}catch{};onEnded?.()};
    video.addEventListener('timeupdate',save);video.addEventListener('pause',save);video.addEventListener('ended',ended);video.addEventListener('error',fail);
    return()=>{save();video.removeEventListener('timeupdate',save);video.removeEventListener('pause',save);video.removeEventListener('ended',ended);video.removeEventListener('error',fail)};
  },[kind,id,selected?.url,selectedIndex,sources.length,onSelectedIndex,onEnded]);

  if(!selected?.url)return null;
  return <div className="player"><video ref={videoRef} key={selected.url} src={selected.url} controls playsInline autoPlay/><div className="player-meta"><ShieldCheck size={15}/><span><b>{selected.provider}</b>{selected.quality?` • ${selected.quality}`:''}{selected.codec?` • ${selected.codec}`:''}{selected.hdr?` • ${selected.hdr}`:''}{selected.licenseLabel?` • ${selected.licenseLabel}`:''}</span></div>{message&&<p className="player-message">{message}</p>}</div>
}

export default function TitlePlaybackPage({ params }: { params: Promise<{ kind: string; id: string }> }) {
  const [route,setRoute]=useState<{kind:'movie'|'series';id:string}|null>(null);
  const [details,setDetails]=useState<TitleDetails|null>(null);
  const [sources,setSources]=useState<SourceCandidate[]>([]);
  const [episodes,setEpisodes]=useState<EpisodeItem[]>([]);
  const [season,setSeason]=useState<number|null>(null);
  const [selectedEpisode,setSelectedEpisode]=useState<EpisodeItem|null>(null);
  const [episodeLoading,setEpisodeLoading]=useState(false);
  const [episodeMessage,setEpisodeMessage]=useState('');
  const [selectedIndex,setSelectedIndex]=useState(0);
  const [watchlisted,setWatchlisted]=useState(false);
  const [resumeSeconds,setResumeSeconds]=useState(0);
  const [loading,setLoading]=useState(true);
  const [error,setError]=useState('');

  useEffect(()=>{params.then(value=>setRoute({kind:value.kind==='series'?'series':'movie',id:value.id}))},[params]);
  useEffect(()=>{
    if(!route)return;
    try{
      const list=JSON.parse(localStorage.getItem(watchlistKey())||'[]') as string[];
      setWatchlisted(list.includes(`${route.kind}:${route.id}`));
      setResumeSeconds(Number(localStorage.getItem(playbackKey(route.kind,route.id))||0));
    }catch{}
    setLoading(true);setError('');setSelectedEpisode(null);setEpisodeMessage('');
    const episodeRequest=route.kind==='series'?fetch(`/api/astrawave/v1/tv/${encodeURIComponent(route.id)}/episodes`,{cache:'no-store'}).then(async r=>r.ok?r.json() as Promise<EpisodePayload>:({seriesId:route.id,episodes:[]} as EpisodePayload)):Promise.resolve({seriesId:route.id,episodes:[]} as EpisodePayload);
    Promise.allSettled([AstraWaveApi.titleDetails(route.kind,route.id),AstraWaveApi.sources(route.kind,route.id),episodeRequest]).then(([d,s,e])=>{
      if(d.status==='fulfilled')setDetails(d.value.details||null);
      if(s.status==='fulfilled'){const ranked=rankSources(s.value);setSources(ranked);setSelectedIndex(0)}
      if(e.status==='fulfilled'){setEpisodes(e.value.episodes||[]);setSeason((e.value.episodes||[])[0]?.season||null)}
      if(d.status==='rejected'&&s.status==='rejected')setError('AstraWave could not load title metadata or playback sources.')
    }).finally(()=>setLoading(false));
  },[route]);

  const related=useMemo(()=>details?.related||[],[details]);
  const meta=details?[details.releaseDate?.slice(0,4),details.rating,details.runtimeMinutes?`${details.runtimeMinutes} min`:null,details.seasons?`${details.seasons} seasons`:null,details.score?`★ ${details.score.toFixed(1)}`:null].filter(Boolean):[];
  const seasons=useMemo(()=>Array.from(new Set(episodes.map(e=>e.season))).sort((a,b)=>a-b),[episodes]);
  const visibleEpisodes=useMemo(()=>episodes.filter(e=>season==null||e.season===season),[episodes,season]);
  const selected=sources[selectedIndex]||null;
  const topQuality=sources.map(s=>s.quality).filter(Boolean)[0];
  const playbackKind=selectedEpisode?'episode':route?.kind||'movie';
  const playbackId=selectedEpisode&&route?episodePlaybackId(route.id,selectedEpisode):route?.id||'';

  function toggleWatchlist(){
    if(!route)return;
    try{
      const key=`${route.kind}:${route.id}`;const list=new Set<string>(JSON.parse(localStorage.getItem(watchlistKey())||'[]'));
      if(list.has(key))list.delete(key);else list.add(key);
      localStorage.setItem(watchlistKey(),JSON.stringify([...list]));setWatchlisted(list.has(key));
    }catch{}
  }

  async function playEpisode(ep:EpisodeItem){
    if(!route||route.kind!=='series'||!details?.imdbId)return;
    setSelectedEpisode(ep);setSeason(ep.season);setEpisodeLoading(true);setEpisodeMessage('Finding the best authorized episode source…');setSources([]);setSelectedIndex(0);
    const manifests=enabledStremioManifests();
    if(!manifests.length){setEpisodeLoading(false);setEpisodeMessage('Add an enabled Stremio addon in Easy Setup to resolve episode playback on web.');return}
    try{
      const response=await fetch('/api/astrawave/vod-resolve',{method:'POST',headers:{'content-type':'application/json'},cache:'no-store',body:JSON.stringify({kind:'episode',tmdbId:route.id,imdbId:details.imdbId,season:ep.season,episode:ep.episode,manifests})});
      const payload=await response.json() as {sources?:SourceCandidate[];error?:string};
      if(!response.ok)throw new Error(payload.error||`Resolver ${response.status}`);
      const ranked=rankSources(payload.sources||[]);setSources(ranked);setSelectedIndex(0);
      setEpisodeMessage(ranked.length?`Ready • S${ep.season} E${ep.episode} • ${ranked.length} source${ranked.length===1?'':'s'} ranked`:'No direct authorized episode source was returned by your enabled addons.');
      requestAnimationFrame(()=>document.getElementById('player')?.scrollIntoView({behavior:'smooth',block:'start'}));
    }catch(err){setEpisodeMessage(err instanceof Error?err.message:'Episode source resolution failed')}finally{setEpisodeLoading(false)}
  }

  function playNextEpisode(){
    if(!selectedEpisode)return;
    const currentIndex=episodes.findIndex(ep=>ep.id===selectedEpisode.id);
    const next=currentIndex>=0?episodes[currentIndex+1]:undefined;
    if(next)void playEpisode(next);
    else setEpisodeMessage('You reached the end of the available episode list.');
  }

  return <main className="title-shell">
    <Link href="/app" className="back"><ArrowLeft size={17}/> Back to AstraWave</Link>
    {loading&&<div className="state">Building your VOD experience…</div>}
    {error&&<div className="state error">{error}</div>}
    {!loading&&details&&<>
      <section className="title-hero" style={details.backdropUrl?{backgroundImage:`linear-gradient(90deg,rgba(5,7,12,.98),rgba(5,7,12,.76) 46%,rgba(5,7,12,.2)),url(${details.backdropUrl})`}:undefined}>
        {details.posterUrl&&<img className="hero-poster" src={details.posterUrl} alt=""/>}
        <div className="hero-copy"><span className="eyebrow">{details.kind==='movie'?<Film size={14}/>:<Tv2 size={14}/>} {details.kind==='movie'?'MOVIE':'SERIES'}</span><h1>{details.title}</h1>{details.tagline&&<p className="tagline">{details.tagline}</p>}<div className="meta">{meta.map(x=><span key={String(x)}>{x}</span>)}{sources.length>0&&<span>{sources.length} source{sources.length===1?'':'s'}</span>}{topQuality&&<span>Best {topQuality}</span>}</div><p className="overview">{details.overview||'No synopsis available.'}</p><div className="genres">{details.genres.map(g=><span key={g}>{g}</span>)}</div><div className="hero-actions">{selected?.url&&<button className="primary" onClick={()=>document.getElementById('player')?.scrollIntoView({behavior:'smooth'})}><Play size={16}/> {resumeSeconds>5?'Resume':'Play Best'}</button>}<button className={watchlisted?'secondary active':'secondary'} onClick={toggleWatchlist}>{watchlisted?<Check size={16}/>:<Bookmark size={16}/>} {watchlisted?'In Watchlist':'Add to Watchlist'}</button>{details.trailers[0]&&<a className="secondary" href={details.trailers[0].url} target="_blank" rel="noreferrer"><ExternalLink size={16}/> Trailer</a>}</div></div>
      </section>

      {route?.kind==='series'&&episodes.length>0&&<section className="section binge"><div className="section-head"><div><small>BINGE MODE</small><h2>Episodes</h2></div><span>{episodes.length} episodes across {seasons.length} season{seasons.length===1?'':'s'}</span></div><div className="season-tabs">{seasons.map(s=><button key={s} className={season===s?'active':''} onClick={()=>setSeason(s)}>Season {s}</button>)}</div><div className="episode-list">{visibleEpisodes.map(ep=><article key={ep.id} role="button" tabIndex={0} className={selectedEpisode?.id===ep.id?'active':''} onClick={()=>void playEpisode(ep)} onKeyDown={event=>{if(event.key==='Enter'||event.key===' '){event.preventDefault();void playEpisode(ep)}}}>{ep.thumbnail?<img src={ep.thumbnail} alt=""/>:<div className="episode-fallback">S{ep.season}E{ep.episode}</div>}<div><small>S{ep.season} E{ep.episode}{ep.runtimeMinutes?` • ${ep.runtimeMinutes} min`:''}{ep.released?` • ${ep.released}`:''}</small><h3>{ep.title}</h3><p>{ep.overview||'Episode details will appear when available.'}</p></div>{selectedEpisode?.id===ep.id&&episodeLoading?<Loader2 className="spin" size={18}/>:<ChevronRight size={18}/>}</article>)}</div>{episodeMessage&&<div className="episode-status">{episodeLoading&&<Loader2 className="spin" size={15}/>} {episodeMessage}</div>}</section>}

      <section className="section" id="player"><div className="section-head"><div><small>SMART PLAYBACK</small><h2>{selectedEpisode?`Play Best • S${selectedEpisode.season} E${selectedEpisode.episode}`:'Play Best'}</h2></div><span>{sources.length} approved candidate{sources.length===1?'':'s'}</span></div>{sources.length===0?<div className="state"><ShieldCheck/> {selectedEpisode?episodeMessage||'No approved episode source is currently available.':'No approved playable source is currently available. Add permitted Stremio, debrid or provider sources in the Control Center.'}</div>:<><div className="smart-summary"><div><b>{selectedEpisode?selectedEpisode.title:selected?.provider}</b><span>{selectedEpisode?`S${selectedEpisode.season} E${selectedEpisode.episode} • ${selected?.provider||'Best source'}`:`${selected?.quality||'Quality not reported'}${selected?.direct?' • Direct':''}`}</span></div><p>AstraWave ranks eligible sources by resolution, direct-play capability, bitrate, uptime and latency when those signals are available, then automatically advances to backups on playback failure.{selectedEpisode?' AutoNext advances to the next available episode when playback ends.':''}</p></div><div className="source-list">{sources.map((source,index)=><button key={source.id} className={selectedIndex===index?'active':''} onClick={()=>setSelectedIndex(index)}><div><b>{index+1}. {source.provider}</b><small>{[source.quality,source.codec,source.hdr,source.bitrateKbps?`${Math.round(source.bitrateKbps/1000)} Mbps`:null,source.uptimePercent!=null?`${source.uptimePercent.toFixed(1)}% uptime`:null,source.latencyMs!=null?`${Math.round(source.latencyMs)} ms`:null,source.direct?'Direct':null,source.licenseLabel].filter(Boolean).join(' • ')||'Eligible source'}</small></div><span>{selectedIndex===index?'Playing':`Score ${Math.round(sourceScore(source))}`}</span></button>)}</div>{route&&<SmartVodPlayer kind={playbackKind} id={playbackId} sources={sources} selectedIndex={selectedIndex} onSelectedIndex={setSelectedIndex} onEnded={selectedEpisode?playNextEpisode:undefined}/>}</>}</section>

      <section className="detail-grid"><article><h2>Cast</h2><div className="people">{details.cast.map(person=><div key={person.id}>{person.profileUrl?<img src={person.profileUrl} alt=""/>:<div className="person-fallback">{person.name.charAt(0)}</div>}<b>{person.name}</b><small>{person.role}</small></div>)}</div></article><aside><h2>About</h2>{details.crew.map(person=><div className="credit" key={`${person.id}:${person.role}`}><span>{person.role}</span><b>{person.name}</b></div>)}{details.status&&<div className="credit"><span>Status</span><b>{details.status}</b></div>}{details.episodes&&<div className="credit"><span>Episodes</span><b>{details.episodes}</b></div>}<div className="credit"><span>Source mode</span><b>Authorized only</b></div></aside></section>

      {details.trailers.length>0&&<section className="section"><div className="section-head"><div><small>TITLE-SPECIFIC MEDIA</small><h2>Trailers & extras</h2></div><span>Only for {details.title}</span></div><div className="trailers">{details.trailers.map(video=><a href={video.url} target="_blank" rel="noreferrer" key={video.key}><div><Play/></div><b>{video.name}</b><small>{video.type}{video.official?' • Official':''}</small></a>)}</div></section>}

      {related.length>0&&<section className="section"><div className="section-head"><div><small>DISCOVER NEXT</small><h2>More like this</h2></div></div><div className="related">{related.map(item=><RelatedCard item={item} key={`${item.kind}:${item.id}`}/>)}</div></section>}
    </>}
    {!loading&&!details&&<div className="state">TMDB metadata is not configured for this deployment. Playback sources can still appear when available.</div>}

    <style jsx global>{`
      *{box-sizing:border-box}body{margin:0;background:radial-gradient(circle at 76% -10%,#21174b55,transparent 34%),#07090d;color:#f7f8fb;font-family:Inter,system-ui,sans-serif}a{text-decoration:none;color:inherit}.title-shell{min-height:100vh;padding:22px max(18px,4vw) 80px}.back{display:inline-flex;align-items:center;gap:7px;color:#a996ff;margin-bottom:18px}.state{border:1px solid #252d38;background:#0e131a;border-radius:15px;padding:20px;color:#a3adba;display:flex;gap:8px;align-items:center}.state.error{border-color:#542632;background:#251016;color:#ff9eaa}.title-hero{min-height:540px;border:1px solid #252d38;border-radius:26px;background:#111723 center/cover;display:flex;gap:30px;align-items:flex-end;padding:44px;overflow:hidden;box-shadow:0 30px 90px #0007}.hero-poster{width:min(230px,24vw);border-radius:17px;box-shadow:0 24px 60px #0009}.hero-copy{max-width:820px}.eyebrow{display:inline-flex;align-items:center;gap:7px;color:#aa98ff;font-size:11px;letter-spacing:.12em}.hero-copy h1{font-size:clamp(40px,6vw,78px);line-height:.95;letter-spacing:-.055em;margin:13px 0}.tagline{font-size:19px;color:#c4cad4}.overview{color:#b0b8c4;line-height:1.65;font-size:16px}.meta,.genres,.hero-actions,.season-tabs{display:flex;gap:8px;flex-wrap:wrap}.meta span,.genres span{border:1px solid #354052;background:#10151ddd;border-radius:999px;padding:6px 9px;font-size:11px}.genres span{color:#aeb7c5}.hero-actions{margin-top:22px}.primary,.secondary{border:0;border-radius:11px;padding:11px 15px;font-weight:800;display:inline-flex;align-items:center;gap:7px;cursor:pointer}.primary{background:white;color:#080a0e}.secondary{background:#161c25;color:white;border:1px solid #303946}.secondary.active{border-color:#7664e5;background:#251e48}.section{margin-top:34px}.section-head{display:flex;justify-content:space-between;align-items:end;margin-bottom:12px}.section-head small{color:#8c7ce8;letter-spacing:.12em}.section-head h2{margin:4px 0 0}.section-head>span{color:#788392;font-size:11px}.season-tabs{margin-bottom:12px;overflow:auto;flex-wrap:nowrap}.season-tabs button{white-space:nowrap;border:1px solid #293240;background:#10151d;color:#aeb7c5;border-radius:11px;padding:9px 12px;cursor:pointer}.season-tabs button.active{background:#282050;border-color:#7968e8;color:white}.episode-list{display:grid;gap:9px}.episode-list article{display:grid;grid-template-columns:180px minmax(0,1fr) 24px;gap:14px;align-items:center;border:1px solid #232b37;background:#0d1117;border-radius:15px;padding:10px;cursor:pointer}.episode-list article.active{border-color:#7867e8;background:linear-gradient(145deg,#1b1730,#10151e)}.episode-list img,.episode-fallback{width:180px;aspect-ratio:16/9;object-fit:cover;border-radius:10px;background:#171e28;display:grid;place-items:center;color:#7d8795;font-weight:800}.episode-list small{color:#8175d0}.episode-list h3{margin:5px 0;font-size:15px}.episode-list p{margin:0;color:#87919f;line-height:1.45;font-size:12px;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}.episode-status{margin-top:12px;border:1px solid #2c3543;background:#0d1219;border-radius:12px;padding:11px 13px;color:#9ba6b6;font-size:12px;display:flex;align-items:center;gap:7px}.spin{animation:spin .9s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}.smart-summary{border:1px solid #322b52;background:linear-gradient(135deg,#18132d,#0d1118);border-radius:15px;padding:14px 16px;margin-bottom:12px;display:grid;grid-template-columns:minmax(180px,260px) 1fr;gap:18px}.smart-summary b,.smart-summary span{display:block}.smart-summary span,.smart-summary p{color:#929cac;font-size:12px}.smart-summary p{margin:0;line-height:1.5}.source-list{display:grid;grid-template-columns:repeat(auto-fill,minmax(290px,1fr));gap:8px}.source-list button{display:flex;justify-content:space-between;gap:12px;text-align:left;border:1px solid #252d38;background:#0d1117;color:white;border-radius:12px;padding:13px;cursor:pointer}.source-list button.active{border-color:#765ee4;background:#211a41}.source-list b,.source-list small{display:block}.source-list small{color:#8893a2;margin-top:4px;line-height:1.45}.source-list span{color:#a998ff;font-size:11px;white-space:nowrap}.player{margin-top:12px;border:1px solid #252d38;border-radius:16px;overflow:hidden;background:#05070a}.player video{width:100%;max-height:68vh;background:#000;display:block}.player-meta{padding:11px 13px;color:#8f9aa8;display:flex;align-items:center;gap:7px}.player-meta b{color:#c8ced8}.player-message{margin:0;padding:0 13px 13px;color:#efb45c;font-size:12px}.detail-grid{display:grid;grid-template-columns:1fr 320px;gap:18px;margin:34px 0 0}.detail-grid>article,.detail-grid>aside{border:1px solid #222a35;background:#0c1016;border-radius:18px;padding:18px}.detail-grid h2{margin-top:0}.people{display:grid;grid-template-columns:repeat(auto-fill,minmax(110px,1fr));gap:12px}.people img,.person-fallback{width:74px;height:74px;border-radius:50%;object-fit:cover;background:#171d27;display:grid;place-items:center;font-size:22px}.people b,.people small{display:block}.people b{font-size:12px;margin-top:7px}.people small{font-size:10px;color:#7f8997}.credit{display:flex;justify-content:space-between;gap:12px;padding:9px 0;border-top:1px solid #202732}.credit:first-of-type{border-top:0}.credit span{color:#7e8998}.trailers{display:grid;grid-template-columns:repeat(auto-fill,minmax(210px,1fr));gap:10px}.trailers a{border:1px solid #242c38;background:#0d1117;border-radius:14px;padding:12px}.trailers a>div{aspect-ratio:16/9;border-radius:10px;background:linear-gradient(135deg,#261e47,#111720);display:grid;place-items:center}.trailers b,.trailers small{display:block}.trailers b{margin-top:8px}.trailers small{color:#7f8997}.related{display:grid;grid-auto-flow:column;grid-auto-columns:150px;gap:11px;overflow-x:auto;padding-bottom:10px}.related-card img,.related-fallback{width:100%;aspect-ratio:2/3;object-fit:cover;border-radius:13px;background:#151a23}.related-fallback{display:grid;place-items:center;font-size:32px}.related-card b,.related-card small{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.related-card b{font-size:12px;margin-top:7px}.related-card small{color:#7f8997;font-size:10px}@media(max-width:850px){.title-hero{padding:24px;min-height:440px}.hero-poster{display:none}.detail-grid{grid-template-columns:1fr}.episode-list article{grid-template-columns:130px minmax(0,1fr) 20px}.episode-list img,.episode-fallback{width:130px}.smart-summary{grid-template-columns:1fr}}@media(max-width:580px){.title-shell{padding:14px 12px 70px}.title-hero{border-radius:18px;min-height:400px;padding:20px}.hero-copy h1{font-size:42px}.related{grid-auto-columns:130px}.section-head{align-items:flex-start;flex-direction:column}.episode-list article{grid-template-columns:110px minmax(0,1fr)}.episode-list article>svg{display:none}.episode-list img,.episode-fallback{width:110px}.episode-list p{display:none}}
    `}</style>
  </main>
}

function RelatedCard({item}:{item:CatalogItem}){return <Link href={`/app/title/${item.kind}/${item.id}`} className="related-card">{item.posterUrl?<img src={item.posterUrl} alt=""/>:<div className="related-fallback">{item.title[0]}</div>}<b>{item.title}</b><small>{item.subtitle}{item.score?` • ★ ${item.score.toFixed(1)}`:''}</small></Link>}
