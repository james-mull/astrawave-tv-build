'use client';

import Link from 'next/link';
import { useEffect, useMemo, useRef, useState } from 'react';
import {
  Activity, CalendarDays, Clock3, Compass, Film, Gauge, Home, Menu, Music2, Play,
  RadioTower, Search, SlidersHorizontal, Star, Tv2, Trophy, UserCircle2, X
} from 'lucide-react';
import {
  AstraWaveApi, CatalogItem, CatalogRail, LiveData, SourceRegistry, SportsEvent
} from '../../lib/astrawave-api';

type View='home'|'movies'|'tv'|'live'|'guide'|'sports'|'audio'|'discover'|'sources'|'diagnostics';
type LiveMode='managed'|'provider';
type LiveQuickFilter='all'|'favorites'|'recent';
const nav=[
  ['home','Home',Home],['movies','Movies',Film],['tv','TV Shows',Tv2],['live','Live TV',RadioTower],
  ['guide','Guide',CalendarDays],['sports','Sports',Trophy],['audio','Music & Podcasts',Music2],
  ['discover','Discover',Compass],['sources','Source Manager',SlidersHorizontal],['diagnostics','Diagnostics',Gauge]
] as const;

function unique(items:CatalogItem[]){const seen=new Set<string>();return items.filter(x=>{const k=`${x.kind}:${x.id}`;if(seen.has(k))return false;seen.add(k);return true})}
function norm(v:string){return v.toLowerCase().replace(/[^a-z0-9]+/g,' ').trim()}

function Card({item,onPlay}:{item:CatalogItem;onPlay:(item:CatalogItem)=>void}){
  const body=<article className="aw-card"><div className="aw-poster" style={item.posterUrl?{backgroundImage:`linear-gradient(to top,rgba(5,7,12,.94),rgba(5,7,12,.08)),url(${item.posterUrl})`}:undefined}>
    {!item.posterUrl&&<span>{item.title.slice(0,1)}</span>}
    {item.streamUrl&&<button className="poster-play" onClick={e=>{e.preventDefault();onPlay(item)}}><Play size={15}/></button>}
    {item.score&&item.score>0?<b className="score">{item.score.toFixed(1)}</b>:null}
  </div><strong>{item.title}</strong><small>{item.subtitle||'AstraWave'}</small></article>;
  return item.kind==='movie'||item.kind==='series'?<Link className="card-link" href={`/app/title/${item.kind}/${item.id}`}>{body}</Link>:<div className="card-link">{body}</div>
}

function Row({rail,onPlay}:{rail:CatalogRail;onPlay:(item:CatalogItem)=>void}){if(!rail.items?.length)return null;return <section className="aw-row"><div className="row-head"><div><h3>{rail.title}</h3>{rail.source&&<small>{rail.source}</small>}</div><span>{rail.items.length}</span></div><div className="card-strip">{rail.items.map(x=><Card key={`${x.kind}:${x.id}`} item={x} onPlay={onPlay}/>)}</div></section>}

function BrowserPlayerVideo({url,onFatal}:{url:string;onFatal:()=>void}){
  const videoRef=useRef<HTMLVideoElement|null>(null);
  const fatalRef=useRef(onFatal);
  useEffect(()=>{fatalRef.current=onFatal},[onFatal]);
  useEffect(()=>{
    const video=videoRef.current;
    if(!video)return;
    let disposed=false;
    let failed=false;
    let hls:{destroy:()=>void}|null=null;
    const fail=()=>{if(disposed||failed)return;failed=true;fatalRef.current()};
    const nativeHls=video.canPlayType('application/vnd.apple.mpegurl')||video.canPlayType('application/x-mpegURL');
    const isHls=/\.m3u8(?:$|\?)/i.test(url);
    video.addEventListener('error',fail);
    if(isHls&&!nativeHls){
      import('hls.js').then(({default:Hls})=>{
        if(disposed)return;
        if(!Hls.isSupported()){fail();return;}
        const instance=new Hls({enableWorker:true,lowLatencyMode:true,backBufferLength:30});
        hls=instance;
        instance.on(Hls.Events.ERROR,(_event,data)=>{if(data.fatal)fail()});
        instance.loadSource(url);
        instance.attachMedia(video);
        instance.on(Hls.Events.MANIFEST_PARSED,()=>{video.play().catch(()=>{})});
      }).catch(fail);
    }else{
      video.src=url;
      video.play().catch(()=>{});
    }
    return()=>{
      disposed=true;
      video.removeEventListener('error',fail);
      hls?.destroy();
      video.pause();
      video.removeAttribute('src');
      video.load();
    };
  },[url]);
  return <video ref={videoRef} controls autoPlay playsInline/>;
}

export default function WebAppHome(){
  const [view,setView]=useState<View>('home');
  const [movieRails,setMovieRails]=useState<CatalogRail[]>([]);
  const [tvRails,setTvRails]=useState<CatalogRail[]>([]);
  const [addonRails,setAddonRails]=useState<CatalogRail[]>([]);
  const [tmdbConfigured,setTmdbConfigured]=useState(true);
  const [liveData,setLiveData]=useState<LiveData>({activeSource:'all-free',sourceOptions:[],channels:[],stats:{channels:0,epgLinked:0}});
  const [activeSource,setActiveSource]=useState('all-free');
  const [liveMode,setLiveMode]=useState<LiveMode>('managed');
  const [liveGroup,setLiveGroup]=useState('all');
  const [liveQuickFilter,setLiveQuickFilter]=useState<LiveQuickFilter>('all');
  const [favoriteLiveIds,setFavoriteLiveIds]=useState<Set<string>>(new Set());
  const [recentLiveIds,setRecentLiveIds]=useState<string[]>([]);
  const [sports,setSports]=useState<SportsEvent[]>([]);
  const [sportsDate,setSportsDate]=useState('all');
  const [sportsLeague,setSportsLeague]=useState('all');
  const [sportsQuery,setSportsQuery]=useState('');
  const [audio,setAudio]=useState<CatalogItem[]>([]);
  const [registry,setRegistry]=useState<SourceRegistry|null>(null);
  const [playing,setPlaying]=useState<CatalogItem|null>(null);
  const [sourceIndex,setSourceIndex]=useState(0);
  const [playerError,setPlayerError]=useState('');
  const [mobileNav,setMobileNav]=useState(false);
  const [query,setQuery]=useState('');
  const [searchResults,setSearchResults]=useState<CatalogItem[]>([]);
  const [liveQuery,setLiveQuery]=useState('');
  const [liveLimit,setLiveLimit]=useState(160);
  const [guideLimit,setGuideLimit]=useState(120);
  const [loading,setLoading]=useState(true);
  const [liveLoading,setLiveLoading]=useState(false);
  const [enabled,setEnabled]=useState<Record<string,boolean>>({});
  const [diag,setDiag]=useState<Record<string,string>>({});

  async function loadLive(source:string,mode:LiveMode){
    const response=await fetch(`/api/astrawave/live-data?source=${encodeURIComponent(source)}&mode=${mode}`,{cache:'no-store'});
    if(!response.ok)throw new Error(`Live TV ${response.status}`);
    return response.json() as Promise<LiveData>;
  }

  useEffect(()=>{
    try{
      const raw=localStorage.getItem('astrawave:registry-enabled');if(raw)setEnabled(JSON.parse(raw));
      const fav=localStorage.getItem('astrawave:live-favorites');if(fav)setFavoriteLiveIds(new Set(JSON.parse(fav)));
      const recent=localStorage.getItem('astrawave:live-recents');if(recent)setRecentLiveIds(JSON.parse(recent));
    }catch{}
    Promise.allSettled([
      AstraWaveApi.catalogRails('movie'),AstraWaveApi.catalogRails('series'),AstraWaveApi.addonRails(),
      AstraWaveApi.liveData('all-free'),AstraWaveApi.sportsChannelCloud(),AstraWaveApi.audioTrending(),AstraWaveApi.sourceRegistry()
    ]).then(([m,t,a,l,s,au,r])=>{
      if(m.status==='fulfilled'){setMovieRails(m.value.rails);setTmdbConfigured(m.value.configured)}
      if(t.status==='fulfilled')setTvRails(t.value.rails);
      if(a.status==='fulfilled')setAddonRails(a.value.rails);
      if(l.status==='fulfilled')setLiveData(l.value);
      if(s.status==='fulfilled')setSports(s.value.events);
      if(au.status==='fulfilled')setAudio(au.value);
      if(r.status==='fulfilled')setRegistry(r.value);
    }).finally(()=>setLoading(false));
  },[]);

  useEffect(()=>{const id=setTimeout(()=>{if(!query.trim()){setSearchResults([]);return}AstraWaveApi.search(query).then(setSearchResults).catch(()=>setSearchResults([]))},220);return()=>clearTimeout(id)},[query]);
  useEffect(()=>setLiveLimit(160),[activeSource,liveQuery,liveMode,liveGroup,liveQuickFilter]);
  useEffect(()=>setGuideLimit(120),[activeSource,liveMode]);

  async function refreshLive(source:string,mode:LiveMode){
    setLiveLoading(true);setActiveSource(source);setLiveMode(mode);setLiveGroup('all');setLiveQuickFilter('all');
    try{setLiveData(await loadLive(source,mode))}catch{setLiveData(d=>({...d,channels:[]}))}finally{setLiveLoading(false)}
  }
  async function switchSource(id:string){await refreshLive(id,liveMode)}
  async function switchLiveMode(mode:LiveMode){if(mode===liveMode)return;await refreshLive(activeSource,mode)}
  function rememberRecent(item:CatalogItem){
    if(item.kind!=='live'&&item.kind!=='sport')return;
    setRecentLiveIds(current=>{
      const next=[item.id,...current.filter(id=>id!==item.id)].slice(0,30);
      try{localStorage.setItem('astrawave:live-recents',JSON.stringify(next))}catch{}
      return next;
    });
  }
  function play(item:CatalogItem){setSourceIndex(0);setPlayerError('');rememberRecent(item);const url=item.sources?.[0]?.streamUrl||item.streamUrl;setPlaying({...item,streamUrl:url})}
  function selectPlayerSource(index:number){if(!playing?.sources?.[index])return;setSourceIndex(index);setPlayerError('');setPlaying({...playing,streamUrl:playing.sources[index].streamUrl})}
  function handlePlayerError(){
    if(!playing)return;
    const sources=playing.sources||[];
    const nextIndex=sourceIndex+1;
    if(nextIndex<sources.length){
      setSourceIndex(nextIndex);
      setPlayerError(`Source ${sourceIndex+1} could not play in this browser. Trying backup ${nextIndex+1} of ${sources.length}…`);
      setPlaying({...playing,streamUrl:sources[nextIndex].streamUrl});
      return;
    }
    setPlayerError(sources.length>1?'All ranked browser sources failed. Choose a source manually or switch Live TV to Provider Order.':'This source could not be played in the browser. Try Provider Order or the Android/TV app.');
  }
  function toggleRegistry(id:string,defaultValue:boolean){const next={...enabled,[id]:!(enabled[id]??defaultValue)};setEnabled(next);localStorage.setItem('astrawave:registry-enabled',JSON.stringify(next))}
  function toggleLiveFavorite(id:string){
    setFavoriteLiveIds(current=>{
      const next=new Set(current);if(next.has(id))next.delete(id);else next.add(id);
      try{localStorage.setItem('astrawave:live-favorites',JSON.stringify([...next]))}catch{}
      return next;
    });
  }

  const aiRails=useMemo(()=>{
    const all=unique([...movieRails,...tvRails].flatMap(r=>r.items));
    const best=[...all].sort((a,b)=>(b.score||0)-(a.score||0)).slice(0,24);
    const hidden=[...all].filter(x=>(x.score||0)>=7&&(x.popularity||0)<100).sort((a,b)=>(b.score||0)-(a.score||0)).slice(0,24);
    const fresh=unique([...movieRails.slice(0,3).flatMap(r=>r.items.slice(0,8)),...tvRails.slice(0,3).flatMap(r=>r.items.slice(0,8))]).slice(0,28);
    return [
      {title:'AstraWave AI • Best Bets Tonight',source:'AI-assisted ranking',items:best},
      {title:'AstraWave AI • Hidden Gems',source:'AI-assisted ranking',items:hidden},
      {title:'AstraWave AI • Fresh Mix',source:'AI-assisted ranking',items:fresh},
    ].filter(r=>r.items.length) as CatalogRail[];
  },[movieRails,tvRails]);

  const liveGroups=useMemo(()=>Array.from(new Set(liveData.channels.map(c=>c.group).filter((x):x is string=>Boolean(x)))).sort().slice(0,36),[liveData]);
  const filteredLive=useMemo(()=>{
    const n=norm(liveQuery);
    const recentIndex=new Map(recentLiveIds.map((id,index)=>[id,index]));
    return liveData.channels
      .filter(c=>(liveGroup==='all'||c.group===liveGroup)&&(!n||norm(`${c.name} ${c.group||''} ${c.now||''}`).includes(n)))
      .filter(c=>liveQuickFilter==='all'||(liveQuickFilter==='favorites'&&favoriteLiveIds.has(c.id))||(liveQuickFilter==='recent'&&recentIndex.has(c.id)))
      .sort((a,b)=>liveQuickFilter==='recent'?(recentIndex.get(a.id)??999)-(recentIndex.get(b.id)??999):0);
  },[liveData,liveQuery,liveGroup,liveQuickFilter,favoriteLiveIds,recentLiveIds]);
  const guideChannels=useMemo(()=>filteredLive.filter(c=>c.programs?.length),[filteredLive]);
  const recentLiveItems=useMemo(()=>recentLiveIds.map(id=>liveData.channels.find(c=>c.id===id)).filter(Boolean).slice(0,20).map(c=>({id:c!.id,kind:'live' as const,title:c!.name,subtitle:c!.now||c!.group,posterUrl:c!.logoUrl,streamUrl:c!.streamUrl,sources:c!.sources})),[recentLiveIds,liveData]);
  const dates=useMemo(()=>Array.from(new Set(sports.map(e=>e.date||e.startTime.slice(0,10)))),[sports]);
  const sportLeagues=useMemo(()=>Array.from(new Set(sports.map(e=>e.league).filter(Boolean))).sort(),[sports]);
  const visibleSports=useMemo(()=>{
    const q=norm(sportsQuery);
    return sports.filter(e=>(sportsDate==='all'||(e.date||e.startTime.slice(0,10))===sportsDate)&&(sportsLeague==='all'||e.league===sportsLeague)&&(!q||norm(`${e.title} ${e.league} ${e.homeTeam||''} ${e.awayTeam||''} ${(e.broadcasts||[]).join(' ')}`).includes(q)));
  },[sports,sportsDate,sportsLeague,sportsQuery]);
  const activePlayerSource=playing?.sources?.[sourceIndex];

  async function runDiagnostics(){
    const checks:[string,()=>Promise<any>][]=[
      ['TMDB catalogs',()=>AstraWaveApi.catalogRails('movie')],
      ['Stremio addons',AstraWaveApi.addonRails],
      ['Live • managed',()=>loadLive(activeSource,'managed')],
      ['Live • provider-order fallback',()=>loadLive(activeSource,'provider')],
      ['Sports Channel Cloud',()=>AstraWaveApi.sportsChannelCloud()],
      ['Source registry',AstraWaveApi.sourceRegistry]
    ];
    setDiag(Object.fromEntries(checks.map(([k])=>[k,'checking'])));
    await Promise.all(checks.map(async([k,fn])=>{try{const value=await fn();let detail='pass';if(k.startsWith('Live')&&value?.stats)detail=`pass • ${value.stats.channels} channels • ${value.stats.epgLinked} EPG`;if(k==='Sports Channel Cloud'&&value?.stats)detail=`pass • ${value.stats.events} events • ${value.stats.eventsWithChannelMatches} matched`;setDiag(d=>({...d,[k]:detail}))}catch{setDiag(d=>({...d,[k]:'fail'}))}}));
  }

  const pageTitle=nav.find(n=>n[0]===view)?.[1]||'AstraWave';
  const hero=movieRails[0]?.items[0]||tvRails[0]?.items[0];

  return <main className="shell">
    <aside className={mobileNav?'side open':'side'}><div className="brand"><Link href="/"><span>AW</span><b>AstraWave</b></Link><button onClick={()=>setMobileNav(false)}><X/></button></div><nav>{nav.map(([id,label,Icon])=><button key={id} className={view===id?'active':''} onClick={()=>{if(id==='audio'){window.location.href='/app/audio';return}setView(id as View);setMobileNav(false)}}><Icon size={18}/><span>{label}</span></button>)}</nav><footer><i/><div><b>{liveData.stats.channels||'—'} live</b><small>{liveData.stats.epgLinked||0} with EPG</small></div></footer></aside>

    <section className="main"><header className="top"><button className="menu" onClick={()=>setMobileNav(true)}><Menu/></button><div><small>ASTRAWAVE</small><h1>{pageTitle}</h1></div><div className="search"><Search size={16}/><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Search movies, TV, live, sports & audio"/></div><Link className="user" href="/app/control"><UserCircle2/></Link></header>

    {query.trim()&&<section className="searchResults"><div className="row-head"><div><h3>Search Everything</h3><small>{searchResults.length} result{searchResults.length===1?'':'s'}</small></div></div><div className="card-strip">{searchResults.map(item=><Card key={`${item.kind}:${item.id}`} item={item} onPlay={play}/>)}</div></section>}

    {!query.trim()&&view==='home'&&<>{!tmdbConfigured&&<div className="setupNotice"><h3>Movie/TV metadata not configured</h3><p>Add TMDB in AstraWave settings to populate rich movie and TV discovery.</p></div>}{hero&&<section className="appHero" style={hero.backdropUrl?{backgroundImage:`linear-gradient(90deg,rgba(6,8,13,.98),rgba(6,8,13,.45)),url(${hero.backdropUrl})`,backgroundSize:'cover',backgroundPosition:'center'}:undefined}><div className="heroShade"><span className="pillTag">ASTRAWAVE • FEATURED</span><h2>{hero.title}</h2><p>{hero.overview||hero.subtitle||'Your entertainment, unified.'}</p><div><button className="primaryBtn" onClick={()=>play(hero)}><Play size={16}/>Play Best</button><Link className="ghostBtn" href={`/app/title/${hero.kind}/${hero.id}`}>Details</Link></div></div></section>}{recentLiveItems.length>0&&<Row rail={{title:'Jump Back In • Live TV',source:'Your recent channels',items:recentLiveItems}} onPlay={play}/>} {aiRails.map(r=><Row key={r.title} rail={r} onPlay={play}/>)}{movieRails.slice(0,4).map(r=><Row key={`m-${r.title}`} rail={r} onPlay={play}/>)}{tvRails.slice(0,4).map(r=><Row key={`t-${r.title}`} rail={r} onPlay={play}/>)}</>}

    {!query.trim()&&view==='movies'&&movieRails.map(r=><Row key={r.title} rail={r} onPlay={play}/>)}
    {!query.trim()&&view==='tv'&&tvRails.map(r=><Row key={r.title} rail={r} onPlay={play}/>)}
    {!query.trim()&&view==='discover'&&<>{aiRails.map(r=><Row key={r.title} rail={r} onPlay={play}/>)}{addonRails.map(r=><Row key={`${r.addonId||'addon'}-${r.title}`} rail={r} onPlay={play}/>)}</>}
    {!query.trim()&&view==='audio'&&<><div className="setupNotice"><h3>Audio moved to a full catalog browser</h3><p>Open the dedicated Audio experience for large radio, podcast and music catalogs.</p><Link className="primaryBtn" href="/app/audio">Open Audio</Link></div>{audio.length>0&&<Row rail={{title:'Popular Radio',source:'Radio Browser',items:audio}} onPlay={play}/>}</>}
    {!query.trim()&&view==='live'&&<section>
      <div className="row-head"><div><h3>Live TV</h3><small>{liveData.stats.channels} channels • {liveData.stats.epgLinked} EPG linked • {liveMode==='managed'?'Unified channels':'Original provider order'}</small></div></div>
      <div className="sourceChips"><button className={liveMode==='managed'?'active':''} onClick={()=>switchLiveMode('managed')}>Unified</button><button className={liveMode==='provider'?'active':''} onClick={()=>switchLiveMode('provider')}>Provider Order</button></div>
      <div className="sourceChips"><button className={liveQuickFilter==='all'?'active':''} onClick={()=>setLiveQuickFilter('all')}>All</button><button className={liveQuickFilter==='favorites'?'active':''} onClick={()=>setLiveQuickFilter('favorites')}><Star size={14}/> Favorites {favoriteLiveIds.size||''}</button><button className={liveQuickFilter==='recent'?'active':''} onClick={()=>setLiveQuickFilter('recent')}><Clock3 size={14}/> Recent</button></div>
      <div className="sourceChips">{liveData.sourceOptions.map(s=><button key={s.id} className={activeSource===s.id?'active':''} onClick={()=>switchSource(s.id)}>{s.name}</button>)}</div>
      {liveGroups.length>0&&<div className="sourceChips"><button className={liveGroup==='all'?'active':''} onClick={()=>setLiveGroup('all')}>All categories</button>{liveGroups.map(group=><button key={group} className={liveGroup===group?'active':''} onClick={()=>setLiveGroup(group)}>{group}</button>)}</div>}
      <input className="liveSearch" value={liveQuery} onChange={e=>setLiveQuery(e.target.value)} placeholder="Filter channels, categories or now playing"/>
      {liveLoading&&<div className="setupNotice"><p>Refreshing {liveMode==='provider'?'provider order':'health-ranked unified channels'}…</p></div>}
      {!liveLoading&&filteredLive.length===0&&<div className="setupNotice"><h3>No channels in this view</h3><p>Try All channels, another category, or Provider Order. Favorites and Recent are stored locally on this device.</p></div>}
      <div className="liveGrid">{filteredLive.slice(0,liveLimit).map(c=><div className="liveTile" key={c.id}><button className="cardButton" onClick={()=>play({id:c.id,kind:'live',title:c.name,subtitle:c.now||c.group,posterUrl:c.logoUrl,streamUrl:c.streamUrl,sources:c.sources})}><b>{c.name}</b><span>{c.now||c.group||'Live'}</span><small>{liveMode==='provider'?'Provider order':`${c.sourceCount} source${c.sourceCount===1?'':'s'}${c.sources?.[0]?.healthScore?` • health ${Math.round(c.sources[0].healthScore)}`:''}`}</small></button><button className={favoriteLiveIds.has(c.id)?'active':''} aria-label={favoriteLiveIds.has(c.id)?'Remove favorite':'Add favorite'} onClick={()=>toggleLiveFavorite(c.id)}><Star size={15}/></button></div>)}</div>
      {filteredLive.length>liveLimit&&<button className="ghostBtn" onClick={()=>setLiveLimit(v=>v+160)}>Show more</button>}
    </section>}
    {!query.trim()&&view==='guide'&&<section><div className="row-head"><div><h3>Guide</h3><small>{guideChannels.length} channels with schedule data • {liveMode==='provider'?'Provider Order':'Unified'}</small></div></div><div className="sourceChips"><button className={liveMode==='managed'?'active':''} onClick={()=>switchLiveMode('managed')}>Unified</button><button className={liveMode==='provider'?'active':''} onClick={()=>switchLiveMode('provider')}>Provider Order</button></div><div className="guideList">{guideChannels.slice(0,guideLimit).map(c=><article key={c.id} onClick={()=>play({id:c.id,kind:'live',title:c.name,subtitle:c.now||c.group,posterUrl:c.logoUrl,streamUrl:c.streamUrl,sources:c.sources})}><div><b>{c.name}</b><small>{c.group||'Live TV'}</small></div><div>{(c.programs||[]).slice(0,4).map(p=><span key={`${c.id}-${p.start}-${p.title}`}><b>{p.title}</b><small>{new Date(p.start).toLocaleTimeString([],{hour:'numeric',minute:'2-digit'})}</small></span>)}</div></article>)}</div>{guideChannels.length>guideLimit&&<button className="ghostBtn" onClick={()=>setGuideLimit(v=>v+120)}>Show more</button>}</section>}
    {!query.trim()&&view==='sports'&&<section><div className="dateChips"><button className={sportsDate==='all'?'active':''} onClick={()=>setSportsDate('all')}>All dates</button>{dates.slice(0,14).map(d=><button key={d} className={sportsDate===d?'active':''} onClick={()=>setSportsDate(d)}>{new Date(`${d}T12:00:00`).toLocaleDateString([],{month:'short',day:'numeric'})}</button>)}</div><div className="sourceChips"><button className={sportsLeague==='all'?'active':''} onClick={()=>setSportsLeague('all')}>All leagues</button>{sportLeagues.slice(0,24).map(league=><button key={league} className={sportsLeague===league?'active':''} onClick={()=>setSportsLeague(league)}>{league}</button>)}</div><input className="liveSearch" value={sportsQuery} onChange={e=>setSportsQuery(e.target.value)} placeholder="Filter teams, leagues or broadcasters"/><div className="row-head"><div><h3>Sports</h3><small>{visibleSports.length} events • health-ranked channel matching</small></div></div><div className="sportsGrid">{visibleSports.map(e=>{const match=e.channelMatches?.[0];const sources=match?.candidates||[];const best=sources[0];const broadcasts=(e.broadcasts||[]).join(' • ');return <article key={e.id}><span>{e.league}</span><h3>{e.title}</h3><p>{new Date(e.startTime).toLocaleString()} • {e.status}</p><small>{broadcasts||'Broadcaster TBD'}</small>{match&&best&&<button className="primaryBtn" onClick={()=>play({id:`sport:${e.id}`,kind:'sport',title:e.title,subtitle:`${e.league} • ${match.name}`,posterUrl:e.badge||match.logoUrl,streamUrl:best.streamUrl,sources})}>Watch best • {match.name}</button>}{match&&<small>{match.sourceCount} ranked source{match.sourceCount===1?'':'s'}{match.healthScore?` • channel health ${Math.round(match.healthScore)}`:''}{best?.provider?` • best ${best.provider}`:''}{best?.healthScore?` ${Math.round(best.healthScore)}`:''}</small>}</article>})}</div></section>}
    {!query.trim()&&view==='sources'&&registry&&<section>{(['stremio','cloudstream','live','providerCatalogs'] as const).map(group=><div className="sourceGroup" key={group}><h3>{group==='providerCatalogs'?'Provider Catalogs':group[0].toUpperCase()+group.slice(1)}</h3>{registry[group].map(s=>{const on=enabled[s.id]??s.enabledByDefault;return <article key={s.id}><div><b>{s.name}</b><small>{s.note}</small></div><button className={on?'active':''} onClick={()=>toggleRegistry(s.id,s.enabledByDefault)}>{on?'Enabled':'Disabled'}</button></article>})}</div>)}</section>}
    {!query.trim()&&view==='diagnostics'&&<section><div className="setupNotice"><h3>Playback & source diagnostics</h3><p>Tests both AstraWave Unified Live TV and the non-destructive Provider Order fallback, plus catalogs, sports matching and source registry health.</p></div><button className="primaryBtn" onClick={runDiagnostics}><Activity size={16}/>Run diagnostics</button><div className="diagList">{Object.entries(diag).map(([k,v])=><article key={k}><b>{k}</b><span>{v}</span></article>)}</div></section>}

    {playing&&<div className="playerPanel"><div className="row-head"><div><h3>{playing.title}</h3><small>{playing.subtitle}</small></div><button className="ghostBtn" onClick={()=>setPlaying(null)}>Close</button></div>{playing.streamUrl?<BrowserPlayerVideo key={playing.streamUrl} url={playing.streamUrl} onFatal={handlePlayerError}/>:<p>No browser-playable source available.</p>}{activePlayerSource&&<div className="setupNotice"><h3>Why AstraWave chose this source</h3><p><b>{activePlayerSource.provider}</b>{activePlayerSource.quality?` • ${activePlayerSource.quality}`:''}{activePlayerSource.healthScore!=null?` • health ${Math.round(activePlayerSource.healthScore)}`:' • health not reported'}{activePlayerSource.uptimePercent!=null?` • uptime ${activePlayerSource.uptimePercent.toFixed(1)}%`:''}{activePlayerSource.latencyMs!=null?` • ${Math.round(activePlayerSource.latencyMs)} ms`:''}. {sourceIndex===0?'This is the highest-ranked eligible candidate AstraWave received.':'A higher-ranked source failed, so AstraWave advanced to this backup.'}</p></div>}{playerError&&<p>{playerError}</p>}{playing.sources&&playing.sources.length>1&&<div className="sourceChips">{playing.sources.map((s,i)=><button className={i===sourceIndex?'active':''} key={s.id} onClick={()=>selectPlayerSource(i)}><b>{i+1}. {s.provider}</b><span>{[s.quality,s.healthScore!=null?`health ${Math.round(s.healthScore)}`:null,s.uptimePercent!=null?`${s.uptimePercent.toFixed(1)}% uptime`:null,s.latencyMs!=null?`${Math.round(s.latencyMs)} ms`:null].filter(Boolean).join(' • ')||'Eligible source'}</span></button>)}</div>}</div>}
    {loading&&<div className="setupNotice"><p>Loading AstraWave…</p></div>}
    </section>
  </main>
}