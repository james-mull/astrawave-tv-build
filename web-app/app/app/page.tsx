'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import {
  Activity, CalendarDays, Compass, Film, Gauge, Home, Menu, Music2, Play,
  RadioTower, Search, SlidersHorizontal, Tv2, Trophy, UserCircle2, X
} from 'lucide-react';
import {
  AstraWaveApi, CatalogItem, CatalogRail, LiveData, SourceRegistry, SportsEvent
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
  const [liveLimit,setLiveLimit]=useState(160);
  const [guideLimit,setGuideLimit]=useState(120);
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
  useEffect(()=>setLiveLimit(160),[activeSource,liveQuery]);
  useEffect(()=>setGuideLimit(120),[activeSource]);

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

  const filteredLive=useMemo(()=>{const n=norm(liveQuery);return !n?liveData.channels:liveData.channels.filter(c=>norm(`${c.name} ${c.group||''} ${c.now||''}`).includes(n))},[liveData,liveQuery]);
  const guideChannels=useMemo(()=>liveData.channels.filter(c=>c.programs?.length),[liveData]);
  const dates=useMemo(()=>Array.from(new Set(sports.map(e=>e.date||e.startTime.slice(0,10)))),[sports]);
  const visibleSports=sportsDate==='all'?sports:sports.filter(e=>(e.date||e.startTime.slice(0,10))===sportsDate);

  async function runDiagnostics(){
    const checks:[string,()=>Promise<any>][]=[['TMDB catalogs',()=>AstraWaveApi.catalogRails('movie')],['Stremio addons',AstraWaveApi.addonRails],['Live sources + EPG',()=>AstraWaveApi.liveData(activeSource)],['Sports Channel Cloud',AstraWaveApi.sportsData],['Source registry',AstraWaveApi.sourceRegistry]];
    setDiag(Object.fromEntries(checks.map(([k])=>[k,'checking'])));
    await Promise.all(checks.map(async([k,fn])=>{try{await fn();setDiag(d=>({...d,[k]:'pass'}))}catch{setDiag(d=>({...d,[k]:'fail'}))}}));
  }

  const pageTitle=nav.find(n=>n[0]===view)?.[1]||'AstraWave';
  const hero=movieRails[0]?.items[0]||tvRails[0]?.items[0];

  return <main className="shell">
    <aside className={mobileNav?'side open':'side'}><div className="brand"><Link href="/"><span>AW</span><b>AstraWave</b></Link><button onClick={()=>setMobileNav(false)}><X/></button></div><nav>{nav.map(([id,label,Icon])=><button key={id} className={view===id?'active':''} onClick={()=>{if(id==='audio'){window.location.href='/app/audio';return}setView(id as View);setMobileNav(false)}}><Icon size={18}/><span>{label}</span></button>)}</nav><footer><i/><div><b>{liveData.stats.channels||'—'} live</b><small>{liveData.stats.epgLinked||0} with EPG</small></div></footer></aside>

    <section className="main"><header className="top"><button className="menu" onClick={()=>setMobileNav(true)}><Menu/></button><div><small>ASTRAWAVE</small><h1>{pageTitle}</h1></div><div className="search"><Search size={16}/><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Search movies, TV, live, sports & audio"/></div><Link className="user" href="/app/control"><UserCircle2/></Link></header>

    {query.trim()&&<section className="searchResults"><div className="row-head"><div><h3>Search Everything</h3><small>{searchResults.length} result{searchResults.length===1?'':'s'}</small></div></div><div className="card-strip">{searchResults.map(item=><Card key={`${item.kind}:${item.id}`} item={item} onPlay={play}/>)}</div></section>}

    {!query.trim()&&view==='home'&&<>{!tmdbConfigured&&<div className="setupNotice"><h3>Movie/TV metadata not configured</h3><p>Add TMDB in AstraWave settings to populate rich movie and TV discovery.</p></div>}{hero&&<section className="appHero" style={hero.backdropUrl?{backgroundImage:`linear-gradient(90deg,rgba(6,8,13,.98),rgba(6,8,13,.45)),url(${hero.backdropUrl})`,backgroundSize:'cover',backgroundPosition:'center'}:undefined}><div className="heroShade"><span className="pillTag">ASTRAWAVE • FEATURED</span><h2>{hero.title}</h2><p>{hero.overview||hero.subtitle||'Your entertainment, unified.'}</p><div><button className="primaryBtn" onClick={()=>play(hero)}><Play size={16}/>Play Best</button><Link className="ghostBtn" href={`/app/title/${hero.kind}/${hero.id}`}>Details</Link></div></div></section>}{aiRails.map(r=><Row key={r.title} rail={r} onPlay={play}/>)}{movieRails.slice(0,4).map(r=><Row key={`m-${r.title}`} rail={r} onPlay={play}/>)}{tvRails.slice(0,4).map(r=><Row key={`t-${r.title}`} rail={r} onPlay={play}/>)}</>}

    {!query.trim()&&view==='movies'&&movieRails.map(r=><Row key={r.title} rail={r} onPlay={play}/>)}
    {!query.trim()&&view==='tv'&&tvRails.map(r=><Row key={r.title} rail={r} onPlay={play}/>)}
    {!query.trim()&&view==='discover'&&<>{aiRails.map(r=><Row key={r.title} rail={r} onPlay={play}/>)}{addonRails.map(r=><Row key={`${r.addonId||'addon'}-${r.title}`} rail={r} onPlay={play}/>)}</>}
    {!query.trim()&&view==='audio'&&<><div className="setupNotice"><h3>Audio moved to a full catalog browser</h3><p>Open the dedicated Audio experience for large radio, podcast and music catalogs.</p><Link className="primaryBtn" href="/app/audio">Open Audio</Link></div>{audio.length>0&&<Row rail={{title:'Popular Radio',source:'Radio Browser',items:audio}} onPlay={play}/>}</>}
    {!query.trim()&&view==='live'&&<section><div className="row-head"><div><h3>Live TV</h3><small>{liveData.stats.channels} channels • {liveData.stats.epgLinked} EPG linked</small></div></div><div className="sourceChips">{liveData.sourceOptions.map(s=><button key={s.id} className={activeSource===s.id?'active':''} onClick={()=>switchSource(s.id)}>{s.name}</button>)}</div><input className="liveSearch" value={liveQuery} onChange={e=>setLiveQuery(e.target.value)} placeholder="Filter channels, groups or now playing"/><div className="liveGrid">{filteredLive.slice(0,liveLimit).map(c=><button className="liveTile" key={c.id} onClick={()=>play({id:c.id,kind:'live',title:c.name,subtitle:c.now||c.group,posterUrl:c.logoUrl,streamUrl:c.streamUrl,sources:c.sources})}><b>{c.name}</b><span>{c.now||c.group||'Live'}</span><small>{c.sourceCount} source{c.sourceCount===1?'':'s'}</small></button>)}</div>{filteredLive.length>liveLimit&&<button className="ghostBtn" onClick={()=>setLiveLimit(v=>v+160)}>Show more</button>}</section>}
    {!query.trim()&&view==='guide'&&<section><div className="row-head"><div><h3>Guide</h3><small>{guideChannels.length} channels with schedule data</small></div></div><div className="guideList">{guideChannels.slice(0,guideLimit).map(c=><article key={c.id}><div><b>{c.name}</b><small>{c.group||'Live TV'}</small></div><div>{(c.programs||[]).slice(0,4).map(p=><span key={`${c.id}-${p.start}-${p.title}`}><b>{p.title}</b><small>{new Date(p.start).toLocaleTimeString([],{hour:'numeric',minute:'2-digit'})}</small></span>)}</div></article>)}</div>{guideChannels.length>guideLimit&&<button className="ghostBtn" onClick={()=>setGuideLimit(v=>v+120)}>Show more</button>}</section>}
    {!query.trim()&&view==='sports'&&<section><div className="dateChips"><button className={sportsDate==='all'?'active':''} onClick={()=>setSportsDate('all')}>All</button>{dates.slice(0,14).map(d=><button key={d} className={sportsDate===d?'active':''} onClick={()=>setSportsDate(d)}>{new Date(`${d}T12:00:00`).toLocaleDateString([],{month:'short',day:'numeric'})}</button>)}</div><div className="sportsGrid">{visibleSports.map(e=>{const match=e.channelMatches?.[0];const sources=match?.candidates||[];const broadcasts=(e.broadcasts||[]).join(' • ');return <article key={e.id}><span>{e.league}</span><h3>{e.title}</h3><p>{new Date(e.startTime).toLocaleString()} • {e.status}</p><small>{broadcasts||'Broadcaster TBD'}</small>{match&&sources.length>0&&<button className="primaryBtn" onClick={()=>play({id:match.id,kind:'live',title:match.name,subtitle:e.title,posterUrl:match.logoUrl,streamUrl:sources[0]?.streamUrl,sources})}>Watch on {match.name}</button>}{match&&<small>{match.sourceCount} ranked source{match.sourceCount===1?'':'s'}{match.healthScore?` • health ${Math.round(match.healthScore)}`:''}</small>}</article>})}</div></section>}
    {!query.trim()&&view==='sources'&&registry&&<section>{(['stremio','cloudstream','live','providerCatalogs'] as const).map(group=><div className="sourceGroup" key={group}><h3>{group==='providerCatalogs'?'Provider Catalogs':group[0].toUpperCase()+group.slice(1)}</h3>{registry[group].map(s=>{const on=enabled[s.id]??s.enabledByDefault;return <article key={s.id}><div><b>{s.name}</b><small>{s.note}</small></div><button className={on?'active':''} onClick={()=>toggleRegistry(s.id,s.enabledByDefault)}>{on?'Enabled':'Disabled'}</button></article>})}</div>)}</section>}
    {!query.trim()&&view==='diagnostics'&&<section><button className="primaryBtn" onClick={runDiagnostics}><Activity size={16}/>Run diagnostics</button><div className="diagList">{Object.entries(diag).map(([k,v])=><article key={k}><b>{k}</b><span>{v}</span></article>)}</div></section>}

    {playing&&<div className="playerPanel"><div className="row-head"><div><h3>{playing.title}</h3><small>{playing.subtitle}</small></div><button className="ghostBtn" onClick={()=>setPlaying(null)}>Close</button></div>{playing.streamUrl?<video key={playing.streamUrl} controls autoPlay src={playing.streamUrl} onError={()=>setPlayerError('This source could not be played in the browser.')}/>:<p>No browser-playable source available.</p>}{playerError&&<p>{playerError}</p>}{playing.sources&&playing.sources.length>1&&<div className="sourceChips">{playing.sources.map((s,i)=><button className={i===sourceIndex?'active':''} key={s.id} onClick={()=>selectPlayerSource(i)}>{s.provider}</button>)}</div>}</div>}
    {loading&&<div className="setupNotice"><p>Loading AstraWave…</p></div>}
    </section>
  </main>
}