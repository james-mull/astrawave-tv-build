'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import {
  Activity, Bot, CalendarDays, Compass, Film, Gauge, Home, Menu, Music2, Play,
  RadioTower, Search, Settings2, ShieldCheck, SlidersHorizontal, Sparkles, Tv2,
  Trophy, UserCircle2, Volume2, X
} from 'lucide-react';
import {
  AstraWaveApi, CatalogItem, CatalogRail, LiveChannel, LiveData, SourceRegistry, SportsEvent
} from '../../lib/astrawave-api';

type View='home'|'movies'|'tv'|'live'|'guide'|'sports'|'audio'|'discover'|'sources'|'diagnostics';
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

export default function WebAppHome(){
  const [view,setView]=useState<View>('home');
  const [movieRails,setMovieRails]=useState<CatalogRail[]>([]);
  const [tvRails,setTvRails]=useState<CatalogRail[]>([]);
  const [addonRails,setAddonRails]=useState<CatalogRail[]>([]);
  const [tmdbConfigured,setTmdbConfigured]=useState(true);
  const [liveData,setLiveData]=useState<LiveData>({activeSource:'all-free',sourceOptions:[],channels:[],stats:{channels:0,epgLinked:0}});
  const [activeSource,setActiveSource]=useState('all-free');
  const [sports,setSports]=useState<SportsEvent[]>([]);
  const [sportsDate,setSportsDate]=useState('all');
  const [audio,setAudio]=useState<CatalogItem[]>([]);
  const [registry,setRegistry]=useState<SourceRegistry|null>(null);
  const [playing,setPlaying]=useState<CatalogItem|null>(null);
  const [sourceIndex,setSourceIndex]=useState(0);
  const [playerError,setPlayerError]=useState('');
  const [mobileNav,setMobileNav]=useState(false);
  const [query,setQuery]=useState('');
  const [searchResults,setSearchResults]=useState<CatalogItem[]>([]);
  const [liveQuery,setLiveQuery]=useState('');
  const [loading,setLoading]=useState(true);
  const [enabled,setEnabled]=useState<Record<string,boolean>>({});
  const [diag,setDiag]=useState<Record<string,string>>({});

  useEffect(()=>{
    try{const raw=localStorage.getItem('astrawave:registry-enabled');if(raw)setEnabled(JSON.parse(raw))}catch{}
    Promise.allSettled([
      AstraWaveApi.catalogRails('movie'),AstraWaveApi.catalogRails('series'),AstraWaveApi.addonRails(),
      AstraWaveApi.liveData('all-free'),AstraWaveApi.sportsData(),AstraWaveApi.audioTrending(),AstraWaveApi.sourceRegistry()
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

  async function switchSource(id:string){setActiveSource(id);setLiveData(d=>({...d,activeSource:id,channels:[]}));try{setLiveData(await AstraWaveApi.liveData(id))}catch{setLiveData(d=>({...d,channels:[]}))}}
  function play(item:CatalogItem){setSourceIndex(0);setPlayerError('');const url=item.sources?.[0]?.streamUrl||item.streamUrl;setPlaying({...item,streamUrl:url})}
  function selectPlayerSource(index:number){if(!playing?.sources?.[index])return;setSourceIndex(index);setPlayerError('');setPlaying({...playing,streamUrl:playing.sources[index].streamUrl})}
  function toggleRegistry(id:string,defaultValue:boolean){const next={...enabled,[id]:!(enabled[id]??defaultValue)};setEnabled(next);localStorage.setItem('astrawave:registry-enabled',JSON.stringify(next))}

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

  const liveItems=useMemo(()=>liveData.channels.map(c=>({id:c.id,kind:'live' as const,title:c.name,subtitle:`${c.now||c.group||'Live'} • ${c.sourceCount} source${c.sourceCount===1?'':'s'}`,posterUrl:c.logoUrl,streamUrl:c.streamUrl,sources:c.sources})),[liveData]);
  const filteredLive=useMemo(()=>{const n=norm(liveQuery);return !n?liveData.channels:liveData.channels.filter(c=>norm(`${c.name} ${c.group||''} ${c.now||''}`).includes(n))},[liveData,liveQuery]);
  const dates=useMemo(()=>Array.from(new Set(sports.map(e=>e.date||e.startTime.slice(0,10)))),[sports]);
  const visibleSports=sportsDate==='all'?sports:sports.filter(e=>(e.date||e.startTime.slice(0,10))===sportsDate);

  function matchedChannel(event:SportsEvent){
    const b=norm(event.broadcaster||'');if(!b)return undefined;
    return liveData.channels.find(c=>{const n=norm(c.name);return n.includes(b)||b.includes(n)||b.split(' ').some(token=>token.length>3&&n.includes(token))});
  }

  async function runDiagnostics(){
    const checks:[string,()=>Promise<any>][]=[['TMDB catalogs',()=>AstraWaveApi.catalogRails('movie')],['Stremio addons',AstraWaveApi.addonRails],['Live sources + EPG',()=>AstraWaveApi.liveData(activeSource)],['Sports',AstraWaveApi.sportsData],['Source registry',AstraWaveApi.sourceRegistry]];
    setDiag(Object.fromEntries(checks.map(([k])=>[k,'checking'])));
    await Promise.all(checks.map(async([k,fn])=>{try{await fn();setDiag(d=>({...d,[k]:'pass'}))}catch{setDiag(d=>({...d,[k]:'fail'}))}}));
  }

  const pageTitle=nav.find(n=>n[0]===view)?.[1]||'AstraWave';
  const hero=movieRails[0]?.items[0]||tvRails[0]?.items[0];

  return <main className="shell">
    <aside className={mobileNav?'side open':'side'}><div className="brand"><Link href="/"><span>AW</span><b>AstraWave</b></Link><button onClick={()=>setMobileNav(false)}><X/></button></div><nav>{nav.map(([id,label,Icon])=><button key={id} className={view===id?'active':''} onClick={()=>{setView(id as View);setMobileNav(false)}}><Icon size={18}/><span>{label}</span></button>)}</nav><footer><i/><div><b>{liveData.stats.channels||'—'} live</b><small>{liveData.stats.epgLinked||0} with EPG</small></div></footer></aside>

    <section className="main"><header className="top"><button className="menu" onClick={()=>setMobileNav(true)}><Menu/></button><div><small>ASTRAWAVE</small><h1>{pageTitle}</h1></div><div className="search"><Search size={16}/><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Search movies & TV"/></div><button className="user"><UserCircle2/></button></header>

      {query&&<section><Intro k="SEARCH" h={`Results for “${query}”`} p="TMDB-backed universal title search."/><Row rail={{title:'Matches',items:searchResults}} onPlay={play}/></section>}

      {!query&&view==='home'&&<>{hero&&<section className="hero" style={hero.backdropUrl?{backgroundImage:`linear-gradient(90deg,#06090ff5 0%,#06090faa 45%,#06090f22 80%),url(${hero.backdropUrl})`}:undefined}><div><span><Sparkles size={14}/> $20/MONTH PRODUCT EXPERIENCE</span><h2>{hero.title}</h2><p>{hero.overview||'TMDB catalogs, real guide data, live source switching, sports, addons and smart discovery in one place.'}</p><div className="hero-actions"><button className="primary" onClick={()=>setView('discover')}><Compass size={16}/> Discover</button><button className="secondary" onClick={()=>setView('live')}><RadioTower size={16}/> Live TV</button></div></div></section>}
        <div className="metrics"><button onClick={()=>setView('movies')}><Film/><b>{movieRails.length||'—'} movie lists</b><small>TMDB + addons</small></button><button onClick={()=>setView('guide')}><CalendarDays/><b>{liveData.stats.epgLinked||'—'} guide channels</b><small>EPG linked</small></button><button onClick={()=>setView('sports')}><Trophy/><b>{sports.length||'—'} sports events</b><small>3-day slate</small></button><button onClick={()=>setView('sources')}><Settings2/><b>{registry?(registry.stremio.length+registry.cloudstream.length):'—'} addons/repos</b><small>Hardcoded registry</small></button></div>
        {!tmdbConfigured&&<Notice title="TMDB is not configured on this deployment">The web app is still loading reviewed Stremio catalogs, but TMDB requires TMDB_BEARER_TOKEN in the Vercel project environment.</Notice>}
        {aiRails.slice(0,1).map(r=><Row key={r.title} rail={r} onPlay={play}/>)}
        {movieRails.slice(0,3).map(r=><Row key={r.title} rail={r} onPlay={play}/>)}
        {tvRails.slice(0,3).map(r=><Row key={r.title} rail={r} onPlay={play}/>)}
        {addonRails.slice(0,3).map(r=><Row key={r.title} rail={r} onPlay={play}/>)}
      </>}

      {!query&&view==='movies'&&<><Intro k="MOVIES" h="A real catalog, not two repeated rows." p="Built-in TMDB lists plus genre rails and reviewed addon catalogs."/>{!tmdbConfigured&&<Notice title="TMDB environment missing">Add TMDB_BEARER_TOKEN to restore every built-in TMDB rail.</Notice>}{movieRails.map(r=><Row key={r.title} rail={r} onPlay={play}/>)}{addonRails.filter(r=>r.items.some(x=>x.kind==='movie')).map(r=><Row key={`a:${r.title}`} rail={r} onPlay={play}/>)}</>}
      {!query&&view==='tv'&&<><Intro k="TV" h="More shows. More lists. Better discovery." p="Trending, popular, airing today, new episodes, top-rated and genre-specific TV."/>{tvRails.map(r=><Row key={r.title} rail={r} onPlay={play}/>)}{addonRails.filter(r=>r.items.some(x=>x.kind==='series')).map(r=><Row key={`a:${r.title}`} rail={r} onPlay={play}/>)}</>}

      {!query&&view==='live'&&<><Intro k="LIVE TV" h="Change sources instantly." p="Use one merged lineup or isolate AstraWave Free TV, IPTV Nexus US, public TV, IPTV.org Sports and other reviewed lineups."/><SourceTabs options={liveData.sourceOptions} active={activeSource} onSelect={switchSource}/><div className="live-tools"><div><b>{liveData.stats.channels}</b><span>channels</span></div><div><b>{liveData.stats.epgLinked}</b><span>EPG linked</span></div><input value={liveQuery} onChange={e=>setLiveQuery(e.target.value)} placeholder="Search channels, groups, programs"/></div><div className="channel-grid">{filteredLive.slice(0,160).map(c=><button key={c.id} onClick={()=>c.streamUrl&&play({id:c.id,kind:'live',title:c.name,subtitle:c.now||c.group,posterUrl:c.logoUrl,streamUrl:c.streamUrl,sources:c.sources})}><div className="logo">{c.logoUrl?<img src={c.logoUrl} alt=""/>:<RadioTower/>}</div><div><b>{c.name}</b><small>{c.now||c.group||'Live programming'}</small><span>{c.sourceCount} source{c.sourceCount===1?'':'s'}{c.next?` • Next: ${c.next}`:''}</span></div><Play size={14}/></button>)}</div></>}

      {!query&&view==='guide'&&<><Intro k="EPG" h="The guide is back." p={`${liveData.stats.epgLinked} live channels are currently matched to AstraWave’s cached EPG; the EPG cache reports ${liveData.stats.epgScheduledChannels||0} scheduled channels.`}/><SourceTabs options={liveData.sourceOptions} active={activeSource} onSelect={switchSource}/><div className="guide"><header><span>Channel</span><span>Now</span><span>Next</span><span>Following</span></header>{liveData.channels.filter(c=>c.programs?.length).slice(0,120).map(c=><button key={c.id} onClick={()=>c.streamUrl&&play({id:c.id,kind:'live',title:c.name,streamUrl:c.streamUrl,sources:c.sources})}><b>{c.name}</b><span>{c.now||'—'}</span><span>{c.next||'—'}</span><span>{c.programs?.[2]?.title||'—'}</span></button>)}</div></>}

      {!query&&view==='sports'&&<><Intro k="SPORTS" h="A proper sports hub." p="Three-day event slate, broadcaster matching, sports channels and guide data in one screen."/><div className="date-tabs"><button className={sportsDate==='all'?'active':''} onClick={()=>setSportsDate('all')}>All</button>{dates.map(d=><button key={d} className={sportsDate===d?'active':''} onClick={()=>setSportsDate(d)}>{new Date(`${d}T12:00:00`).toLocaleDateString([],{weekday:'short',month:'short',day:'numeric'})}</button>)}</div><div className="events">{visibleSports.slice(0,120).map(e=>{const match=matchedChannel(e);return <article key={e.id}><div className="time">{new Date(e.startTime).toLocaleTimeString([],{hour:'numeric',minute:'2-digit'})}</div><div><small>{e.league}</small><b>{e.title}</b><span>{e.broadcaster||'Broadcaster not listed'}{match?` • Match: ${match.name}`:''}</span></div>{match?.streamUrl?<button onClick={()=>play({id:match.id,kind:'live',title:match.name,streamUrl:match.streamUrl,sources:match.sources})}><Play size={14}/> Watch</button>:<Trophy/>}</article>})}</div><Row rail={{title:'Live Sports Channels',source:'Current live sources',items:liveItems.filter(x=>/sport|espn|fox sports|fs1|fs2|nfl|nba|mlb|nhl|golf|tennis|bein/i.test(`${x.title} ${x.subtitle}`)).slice(0,36)}} onPlay={play}/></>}

      {!query&&view==='audio'&&<><Intro k="AUDIO" h="Music, radio and podcasts." p="Public radio discovery now lives beside video without losing its own identity."/><Row rail={{title:'Popular Public Radio',source:'Radio Browser',items:audio}} onPlay={play}/></>}

      {!query&&view==='discover'&&<><Intro k="DISCOVER" h="AI ranking + addon catalogs + TMDB." p="Discovery combines native metadata, reviewed Stremio catalog rows and an AstraWave ranking layer rather than repeating the same list everywhere."/>{aiRails.map(r=><Row key={r.title} rail={r} onPlay={play}/>)}{addonRails.map(r=><Row key={`addon:${r.title}`} rail={r} onPlay={play}/>)}{movieRails.slice(5,10).map(r=><Row key={`m:${r.title}`} rail={r} onPlay={play}/>)}{tvRails.slice(5,10).map(r=><Row key={`t:${r.title}`} rail={r} onPlay={play}/>)}</>}

      {!query&&view==='sources'&&<><Intro k="SOURCE MANAGER" h="The actual source stack." p="Switch live lineups, see every reviewed Stremio default and manage the hardcoded CloudStream repository registry."/>
        <h2 className="section-title">Live TV sources</h2><div className="registry-grid">{registry?.live.map(s=><article key={s.id}><RadioTower/><div><b>{s.name}</b><small>{s.note}</small>{s.url&&<code>{s.url}</code>}</div><button className={activeSource===s.id?'using':''} onClick={()=>switchSource(s.id)}>{activeSource===s.id?'Using':'Use'}</button></article>)}</div>
        <h2 className="section-title">Reviewed Stremio defaults</h2><div className="registry-grid">{registry?.stremio.map(s=>{const isOn=enabled[s.id]??s.enabledByDefault;return <article key={s.id}><Compass/><div><b>{s.name}</b><small>{s.note}</small><code>{s.url}</code></div><button className={isOn?'using':''} onClick={()=>toggleRegistry(s.id,s.enabledByDefault)}>{isOn?'Enabled':'Disabled'}</button></article>})}</div>
        <h2 className="section-title">CloudStream repositories</h2><div className="registry-grid">{registry?.cloudstream.map(s=>{const isOn=enabled[s.id]??s.enabledByDefault;return <article key={s.id}><SlidersHorizontal/><div><b>{s.name}</b><small>{s.note}</small><code>{s.url}</code></div><button className={isOn?'using':''} onClick={()=>toggleRegistry(s.id,s.enabledByDefault)}>{isOn?'Enabled':'Opt in'}</button></article>})}</div>
        <h2 className="section-title">Provider catalogs</h2><div className="provider-pills">{registry?.providerCatalogs.map(s=><span key={s.id}>{s.name}</span>)}</div><Notice title="Playback policy">Catalogs and metadata can be hardcoded. Stream-capable community extensions still require source eligibility, authorization and health checks before AstraWave can expose a playable link.</Notice>
      </>}

      {!query&&view==='diagnostics'&&<><Intro k="DIAGNOSTICS" h="See what is connected." p="Run a live check against catalogs, addons, EPG, sports and registry services."/><button className="primary" onClick={runDiagnostics}><Activity size={15}/> Run diagnostics</button><div className="checks">{Object.entries(diag).map(([k,v])=><div key={k}><span>{k}</span><b className={v}>{v}</b></div>)}</div><div className="diag-stats"><article><CalendarDays/><b>{liveData.stats.epgLinked}</b><span>EPG-linked live channels</span></article><article><Film/><b>{movieRails.length+tvRails.length}</b><span>TMDB rails</span></article><article><Compass/><b>{addonRails.length}</b><span>Addon catalog rails</span></article><article><Trophy/><b>{sports.length}</b><span>Sports events loaded</span></article></div></>}

      {loading&&<div className="loading">Loading AstraWave data…</div>}
      {playing?.streamUrl&&<div className="player"><header><div><small>NOW PLAYING</small><b>{playing.title}</b></div><button onClick={()=>setPlaying(null)}><X/></button></header>{playing.kind==='podcast'?<audio key={playing.streamUrl} src={playing.streamUrl} controls autoPlay onError={()=>setPlayerError('Audio stream is unavailable in this browser.')}/>:<video key={playing.streamUrl} src={playing.streamUrl} controls playsInline autoPlay onError={()=>setPlayerError('This source failed in the browser. Select another source below.')}/>} {playing.sources&&playing.sources.length>1&&<div className="player-sources">{playing.sources.map((s,i)=><button key={s.id} className={i===sourceIndex?'active':''} onClick={()=>selectPlayerSource(i)}>{s.provider}</button>)}</div>}{playerError&&<div className="player-error">{playerError}</div>}</div>}
    </section>

    <style jsx global>{`
      *{box-sizing:border-box}body{margin:0;background:#07090d;color:#f7f8fb;font-family:Inter,ui-sans-serif,system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}button,input{font:inherit}a{color:inherit;text-decoration:none}.shell{min-height:100vh;background:radial-gradient(circle at 72% 0,#19223a 0,transparent 34%),#07090d}.side{position:fixed;inset:0 auto 0 0;width:238px;background:#090c11f7;border-right:1px solid #202631;padding:18px 12px;z-index:40;display:flex;flex-direction:column}.brand{display:flex;align-items:center;padding:2px 5px 24px}.brand a{display:flex;align-items:center;gap:10px}.brand a span{width:38px;height:38px;border-radius:12px;background:linear-gradient(135deg,#7c5cff,#36d1ff);display:grid;place-items:center;font-weight:900}.brand>button{display:none;margin-left:auto;background:none;border:0;color:white}.side nav{display:grid;gap:3px}.side nav button{border:0;background:transparent;color:#939dab;border-radius:10px;padding:10px 12px;display:flex;gap:10px;align-items:center;text-align:left;cursor:pointer}.side nav button:hover,.side nav button.active{background:#171c25;color:white}.side footer{margin-top:auto;border-top:1px solid #202631;padding:16px 8px;display:flex;gap:10px;align-items:center}.side footer i{width:9px;height:9px;border-radius:50%;background:#52da82}.side footer b,.side footer small{display:block}.side footer small{color:#7f8997}.main{margin-left:238px;padding:0 28px 80px;max-width:1720px}.top{height:82px;display:flex;align-items:center;gap:12px;position:sticky;top:0;z-index:30;background:#07090de8;backdrop-filter:blur(14px)}.top>div:nth-child(2){margin-right:auto}.top h1{font-size:20px;margin:2px 0}.top small,.intro>span{font-size:10px;color:#7f8997;letter-spacing:.13em}.menu{display:none}.search{display:flex;align-items:center;gap:8px;border:1px solid #262e3a;background:#10141b;border-radius:11px;padding:0 11px}.search input{width:220px;height:40px;border:0;outline:0;background:none;color:white}.user,.menu{width:42px;height:42px;border:1px solid #262e3a;background:#10141b;color:white;border-radius:11px;place-items:center}.hero{min-height:390px;border:1px solid #252c38;border-radius:22px;background:linear-gradient(120deg,#121a29,#0b0f15);background-size:cover;background-position:center;display:flex;align-items:end;padding:40px}.hero>div{max-width:680px}.hero>div>span{display:inline-flex;gap:7px;align-items:center;font-size:10px;letter-spacing:.12em;color:#b4a2ff;font-weight:800}.hero h2,.intro h2{font-size:clamp(38px,5vw,64px);line-height:.98;letter-spacing:-.045em;margin:12px 0}.hero p,.intro p{color:#a0a9b7;line-height:1.6;max-width:700px}.hero-actions{display:flex;gap:9px}.primary,.secondary{border:0;border-radius:11px;padding:11px 15px;font-weight:800;display:inline-flex;align-items:center;gap:7px;cursor:pointer}.primary{background:white;color:#080a0e}.secondary{background:#171c25;color:white;border:1px solid #29313d}.metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:9px;margin:16px 0 28px}.metrics button{background:#0e1218;border:1px solid #222a36;border-radius:14px;color:white;padding:14px;text-align:left;display:grid;grid-template-columns:30px 1fr;gap:3px 9px;cursor:pointer}.metrics svg{grid-row:1/3;color:#9b88ff}.metrics b,.metrics small{display:block}.metrics small{color:#7f8997}.aw-row{margin:28px 0}.row-head{display:flex;align-items:end;justify-content:space-between;margin-bottom:11px}.row-head h3{margin:0;font-size:18px}.row-head small{display:block;color:#7d8795;margin-top:3px}.row-head>span{color:#6f7a89;font-size:11px}.card-strip{display:grid;grid-auto-flow:column;grid-auto-columns:minmax(140px,11vw);gap:11px;overflow-x:auto;padding:2px 2px 10px}.aw-card{min-width:0}.aw-poster{aspect-ratio:2/3;border-radius:14px;background:#151a23 center/cover;border:1px solid #252d39;position:relative;display:grid;place-items:center;font-size:38px;font-weight:900;color:#465166;overflow:hidden}.aw-card strong,.aw-card small{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.aw-card strong{font-size:12px;margin:7px 2px 0}.aw-card small{font-size:10px;color:#7d8795;margin:3px 2px}.poster-play{position:absolute;right:7px;bottom:7px;width:31px;height:31px;border:0;border-radius:50%;background:white;color:#07090d;display:grid;place-items:center;cursor:pointer}.score{position:absolute;top:7px;left:7px;background:#0b1018dd;border:1px solid #354154;border-radius:7px;padding:4px 6px;font-size:10px;color:#85e5a4}.intro{padding:30px 0 14px;border-bottom:1px solid #1d232d;margin-bottom:18px}.intro h2{font-size:clamp(34px,4vw,52px)}.notice{display:flex;gap:11px;align-items:flex-start;background:#101620;border:1px solid #2a3443;border-radius:14px;padding:16px;margin:14px 0}.notice b{display:block}.notice p{margin:3px 0;color:#98a3b2}.source-tabs,.date-tabs{display:flex;gap:7px;overflow-x:auto;padding-bottom:10px}.source-tabs button,.date-tabs button{white-space:nowrap;border:1px solid #29323e;background:#10151c;color:#909aa8;border-radius:999px;padding:8px 11px;cursor:pointer}.source-tabs button.active,.date-tabs button.active{background:#6f54e8;color:white;border-color:#8067f0}.live-tools{display:grid;grid-template-columns:110px 110px 1fr;gap:9px;margin:5px 0 14px}.live-tools>div,.live-tools input{border:1px solid #232b37;background:#0d1117;border-radius:12px;padding:11px;color:white}.live-tools b,.live-tools span{display:block}.live-tools span{font-size:10px;color:#7f8997}.live-tools input{outline:0}.channel-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(270px,1fr));gap:8px}.channel-grid>button{display:grid;grid-template-columns:46px 1fr auto;gap:10px;align-items:center;border:1px solid #222a35;background:#0d1117;color:white;border-radius:12px;padding:10px;text-align:left;cursor:pointer}.channel-grid .logo{width:46px;height:46px;border-radius:10px;background:#171d27;display:grid;place-items:center;overflow:hidden}.channel-grid img{max-width:90%;max-height:90%}.channel-grid b,.channel-grid small,.channel-grid span{display:block}.channel-grid small{color:#a0a9b7}.channel-grid span{font-size:10px;color:#727d8c;margin-top:3px}.guide{border:1px solid #222a35;border-radius:14px;overflow:auto}.guide header,.guide button{display:grid;grid-template-columns:minmax(180px,1fr) 1.4fr 1.4fr 1.4fr;gap:12px;padding:12px 14px;min-width:840px}.guide header{background:#151a22;color:#7e8999;font-size:11px}.guide button{width:100%;border:0;border-top:1px solid #1c232d;background:#0c1016;color:white;text-align:left;cursor:pointer}.guide button span{color:#909aa8}.events{display:grid;gap:8px}.events article{display:grid;grid-template-columns:80px 1fr auto;gap:10px;align-items:center;border:1px solid #222a35;background:#0d1117;border-radius:13px;padding:13px}.events small,.events b,.events span{display:block}.events small{color:#7e8898}.events span{color:#929caa;font-size:11px;margin-top:3px}.events article>button{border:0;border-radius:9px;background:#f4f6fa;color:#080a0e;padding:8px 10px;font-weight:800;display:flex;gap:5px;align-items:center}.time{font-weight:900}.section-title{font-size:18px;margin:30px 0 10px}.registry-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:9px}.registry-grid article{display:grid;grid-template-columns:38px 1fr auto;gap:10px;align-items:start;border:1px solid #232b37;background:#0d1117;border-radius:14px;padding:14px}.registry-grid small,.registry-grid code{display:block}.registry-grid small{color:#8994a3;margin-top:3px}.registry-grid code{color:#697585;font-size:9px;margin-top:7px;overflow:hidden;text-overflow:ellipsis}.registry-grid button{border:1px solid #303946;background:#141a22;color:#a3adbb;border-radius:9px;padding:7px 9px}.registry-grid button.using{border-color:#5b47c9;background:#241b4d;color:#d9d1ff}.provider-pills{display:flex;flex-wrap:wrap;gap:7px}.provider-pills span{border:1px solid #2a3340;background:#10151d;border-radius:999px;padding:8px 10px;color:#aab3c0}.checks{margin-top:16px;border:1px solid #222a35;border-radius:13px}.checks>div{display:flex;justify-content:space-between;padding:11px;border-top:1px solid #1c232d}.checks>div:first-child{border:0}.checks b.pass{color:#5bda87}.checks b.fail{color:#ff7385}.checks b.checking{color:#e6bc56}.diag-stats{display:grid;grid-template-columns:repeat(4,1fr);gap:9px;margin-top:14px}.diag-stats article{border:1px solid #222a35;background:#0d1117;border-radius:13px;padding:15px}.diag-stats b,.diag-stats span{display:block}.diag-stats b{font-size:24px;margin-top:9px}.diag-stats span{font-size:11px;color:#8792a0}.loading{padding:28px;color:#7f8997}.player{position:fixed;right:18px;bottom:18px;width:min(520px,calc(100vw - 36px));background:#090c11;border:1px solid #303947;border-radius:16px;overflow:hidden;z-index:70;box-shadow:0 28px 80px #000b}.player header{display:flex;justify-content:space-between;padding:11px 13px}.player header small,.player header b{display:block}.player header button{background:none;border:0;color:white}.player video{width:100%;display:block;aspect-ratio:16/9;background:#000}.player audio{width:100%;display:block}.player-sources{display:flex;gap:6px;overflow-x:auto;padding:9px}.player-sources button{border:1px solid #2c3542;background:#11161d;color:#9ca6b4;border-radius:8px;padding:7px 9px;white-space:nowrap}.player-sources button.active{background:#2a1f59;color:white;border-color:#6f54e8}.player-error{padding:9px 12px;background:#34141a;color:#ff9cab;font-size:11px}
      @media(max-width:980px){.menu,.brand>button{display:grid}.side{transform:translateX(-105%);transition:.2s}.side.open{transform:translateX(0)}.main{margin-left:0;padding:0 15px 70px}.metrics,.diag-stats{grid-template-columns:1fr 1fr}.registry-grid{grid-template-columns:1fr}.top .menu{display:grid}.search input{width:150px}}
      @media(max-width:620px){.top{height:70px}.top .user{display:none}.search input{width:105px}.hero{min-height:350px;padding:22px}.hero h2{font-size:40px}.metrics{grid-template-columns:1fr 1fr}.card-strip{grid-auto-columns:130px}.live-tools{grid-template-columns:1fr 1fr}.live-tools input{grid-column:1/3}.events article{grid-template-columns:64px 1fr auto}.registry-grid article{grid-template-columns:34px 1fr}.registry-grid article>button{grid-column:2;justify-self:start}.diag-stats{grid-template-columns:1fr 1fr}}
    `}</style>
  </main>
}

function Intro({k,h,p}:{k:string;h:string;p:string}){return <div className="intro"><span>{k}</span><h2>{h}</h2><p>{p}</p></div>}
function Notice({title,children}:{title:string;children:React.ReactNode}){return <div className="notice"><ShieldCheck/><div><b>{title}</b><p>{children}</p></div></div>}
function SourceTabs({options,active,onSelect}:{options:{id:string;name:string}[];active:string;onSelect:(id:string)=>void}){return <div className="source-tabs">{options.map(o=><button key={o.id} className={active===o.id?'active':''} onClick={()=>onSelect(o.id)}>{o.name}</button>)}</div>}
