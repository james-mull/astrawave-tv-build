'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import {
  Activity, CalendarDays, CheckCircle2, Clapperboard, Compass, Film, Gauge, Heart,
  Home, Library, ListVideo, Menu, Music2, Play, RadioTower, RefreshCw, Search,
  Settings2, ShieldCheck, SlidersHorizontal, Sparkles, Tv2, Trophy, UserCircle2,
  Volume2, X, Zap
} from 'lucide-react';
import { AstraWaveApi, CatalogItem, LiveChannel, SportsEvent } from '../../lib/astrawave-api';

type View = 'home'|'movies'|'tv'|'live'|'guide'|'sports'|'audio'|'discover'|'sources'|'diagnostics';
type SourceRecord = { id:string; name:string; type:'M3U'|'Xtream'|'Podcast/RSS'|'Personal Media'; status:'Ready'|'Needs setup'; note:string };

const nav: {id:View; label:string; icon:any}[] = [
  {id:'home',label:'Home',icon:Home},{id:'movies',label:'Movies',icon:Film},{id:'tv',label:'TV Shows',icon:Tv2},
  {id:'live',label:'Live TV',icon:RadioTower},{id:'guide',label:'Guide',icon:CalendarDays},{id:'sports',label:'Sports',icon:Trophy},
  {id:'audio',label:'Music & Podcasts',icon:Music2},{id:'discover',label:'Discover',icon:Compass},{id:'sources',label:'Source Manager',icon:SlidersHorizontal},
  {id:'diagnostics',label:'Diagnostics',icon:Gauge}
];

const starterSources: SourceRecord[] = [
  {id:'authorized-free',name:'AstraWave authorized/free media',type:'M3U',status:'Ready',note:'Only vetted open/public streams and metadata.'},
  {id:'customer-m3u',name:'My M3U / XMLTV',type:'M3U',status:'Needs setup',note:'Use only playlists you are authorized to access.'},
  {id:'customer-xtream',name:'My Xtream provider',type:'Xtream',status:'Needs setup',note:'Credentials stay user-supplied.'},
  {id:'personal-media',name:'Personal media',type:'Personal Media',status:'Needs setup',note:'Plex/Jellyfin/Emby roadmap connector.'}
];

function Card({item,onPlay}:{item:CatalogItem;onPlay:(item:CatalogItem)=>void}) {
  const inner = <article className="aw-card">
    <div className="aw-poster" style={item.posterUrl?{backgroundImage:`linear-gradient(to top,rgba(4,6,10,.92),rgba(4,6,10,.05)),url(${item.posterUrl})`}:undefined}>
      {!item.posterUrl && <span>{item.title.slice(0,1)}</span>}
      {item.kind==='live' && <b className="badge live">LIVE</b>}
      {item.kind==='sport' && <b className="badge sport">SPORT</b>}
      {item.kind==='podcast' && <b className="badge audio">AUDIO</b>}
      {item.streamUrl && <button className="poster-play" onClick={(e)=>{e.preventDefault();onPlay(item)}} aria-label={`Play ${item.title}`}><Play size={16}/></button>}
    </div>
    <strong>{item.title}</strong><small>{item.subtitle || 'AstraWave'}</small>
  </article>;
  if (item.kind==='movie'||item.kind==='series') return <Link className="card-link" href={`/app/title/${item.kind}/${item.id}`}>{inner}</Link>;
  return <div className="card-link">{inner}</div>;
}

function Row({title,items,onPlay}:{title:string;items:CatalogItem[];onPlay:(item:CatalogItem)=>void}) {
  if (!items.length) return null;
  return <section className="aw-row"><div className="row-head"><h3>{title}</h3><span>{items.length} picks</span></div><div className="card-strip">{items.map(x=><Card key={`${x.kind}-${x.id}`} item={x} onPlay={onPlay}/>)}</div></section>;
}

export default function WebAppHome(){
  const [view,setView] = useState<View>('home');
  const [rows,setRows] = useState<{title:string;items:CatalogItem[]}[]>([]);
  const [movies,setMovies] = useState<CatalogItem[]>([]);
  const [shows,setShows] = useState<CatalogItem[]>([]);
  const [live,setLive] = useState<LiveChannel[]>([]);
  const [sports,setSports] = useState<SportsEvent[]>([]);
  const [audio,setAudio] = useState<CatalogItem[]>([]);
  const [playing,setPlaying] = useState<CatalogItem|null>(null);
  const [sourceIndex,setSourceIndex] = useState(0);
  const [playerError,setPlayerError] = useState('');
  const [loading,setLoading] = useState(true);
  const [searchOpen,setSearchOpen] = useState(false);
  const [query,setQuery] = useState('');
  const [searchResults,setSearchResults] = useState<CatalogItem[]>([]);
  const [mobileNav,setMobileNav] = useState(false);
  const [sources,setSources] = useState<SourceRecord[]>(starterSources);
  const [diag,setDiag] = useState<Record<string,'checking'|'pass'|'fail'>>({});
  const [onboarded,setOnboarded] = useState(true);

  useEffect(()=>{
    try {
      setOnboarded(localStorage.getItem('astrawave:onboarded')==='1');
      const saved=localStorage.getItem('astrawave:sources');
      if(saved) setSources(JSON.parse(saved));
    } catch {}
    Promise.allSettled([
      AstraWaveApi.home(), AstraWaveApi.trendingMovies(), AstraWaveApi.trendingShows(),
      AstraWaveApi.liveChannels(), AstraWaveApi.sportsToday(), AstraWaveApi.audioTrending()
    ]).then(([h,m,t,l,s,a])=>{
      if(h.status==='fulfilled') setRows(h.value.rows);
      if(m.status==='fulfilled') setMovies(m.value);
      if(t.status==='fulfilled') setShows(t.value);
      if(l.status==='fulfilled') setLive(l.value);
      if(s.status==='fulfilled') setSports(s.value);
      if(a.status==='fulfilled') setAudio(a.value);
    }).finally(()=>setLoading(false));
  },[]);

  useEffect(()=>{
    const timer=setTimeout(()=>{
      if(!query.trim()){setSearchResults([]);return}
      AstraWaveApi.search(query).then(setSearchResults).catch(()=>setSearchResults([]));
    },250);
    return ()=>clearTimeout(timer);
  },[query]);

  const play=(item:CatalogItem)=>{
    setPlayerError(''); setSourceIndex(0);
    const first=item.sources?.[0]?.streamUrl || item.streamUrl;
    setPlaying({...item,streamUrl:first});
  };
  const nextSource=()=>{
    if(!playing?.sources?.length) return;
    const next=(sourceIndex+1)%playing.sources.length;
    setSourceIndex(next); setPlayerError('');
    setPlaying({...playing,streamUrl:playing.sources[next].streamUrl});
  };

  const liveItems=useMemo(()=>live.map(x=>({id:x.id,kind:'live' as const,title:x.name,subtitle:`${x.group||'Live'} • ${x.sourceCount} source${x.sourceCount===1?'':'s'}`,posterUrl:x.logoUrl,streamUrl:x.streamUrl,sources:x.sources})),[live]);
  const sportItems=useMemo(()=>sports.map(x=>({id:x.id,kind:'sport' as const,title:x.title,subtitle:`${x.league}${x.broadcaster?` • ${x.broadcaster}`:''}`})),[sports]);

  const runDiagnostics=async()=>{
    const checks:{key:string;fn:()=>Promise<any>}[]=[
      {key:'Catalog API',fn:AstraWaveApi.trendingMovies},{key:'Live/Guide API',fn:AstraWaveApi.liveChannels},
      {key:'Sports API',fn:AstraWaveApi.sportsToday},{key:'Audio API',fn:AstraWaveApi.audioTrending}
    ];
    setDiag(Object.fromEntries(checks.map(x=>[x.key,'checking'])));
    await Promise.all(checks.map(async c=>{try{await c.fn();setDiag(d=>({...d,[c.key]:'pass'}))}catch{setDiag(d=>({...d,[c.key]:'fail'}))}}));
  };

  const pageTitle=nav.find(n=>n.id===view)?.label||'Home';
  const heroBackdrop=rows.flatMap(r=>r.items).find(x=>x.backdropUrl)?.backdropUrl;

  return <main className="aw-shell">
    {!onboarded && <div className="onboard"><div className="onboard-card">
      <div className="logo-orb">AW</div><span className="eyebrow">WELCOME TO ASTRAWAVE</span><h1>Your entertainment control center.</h1>
      <p>Use AstraWave with authorized/free media and sources you are permitted to access. Connect your own playlists when you are ready.</p>
      <div className="onboard-grid"><div><ShieldCheck/><b>Rights-first</b><small>No bundled unauthorized premium feeds.</small></div><div><Zap/><b>Fast start</b><small>Movies, TV, live, sports and audio in one shell.</small></div><div><Settings2/><b>Your sources</b><small>M3U, Xtream and personal media stay under your control.</small></div></div>
      <button className="primary" onClick={()=>{localStorage.setItem('astrawave:onboarded','1');setOnboarded(true)}}>Enter AstraWave <Play size={17}/></button>
    </div></div>}

    <aside className={`aw-side ${mobileNav?'open':''}`}>
      <div className="side-top"><Link href="/" className="aw-brand"><span>AW</span><b>AstraWave</b></Link><button className="icon-btn mobile-only" onClick={()=>setMobileNav(false)}><X/></button></div>
      <nav>{nav.map(n=><button key={n.id} className={view===n.id?'nav-item active':'nav-item'} onClick={()=>{setView(n.id);setMobileNav(false)}}><n.icon size={19}/><span>{n.label}</span></button>)}</nav>
      <div className="side-footer"><div className="health-dot"/><div><b>Source-aware</b><small>Legal / authorized only</small></div></div>
    </aside>

    <section className="aw-main">
      <header className="aw-top">
        <button className="icon-btn mobile-only" onClick={()=>setMobileNav(true)}><Menu/></button>
        <div><small>ASTRAWAVE WEB</small><h1>{pageTitle}</h1></div>
        <div className="top-actions"><button className="search-pill" onClick={()=>setSearchOpen(true)}><Search size={17}/><span>Search</span></button><button className="icon-btn"><UserCircle2/></button></div>
      </header>

      {view==='home' && <>
        <section className="aw-hero" style={heroBackdrop?{backgroundImage:`linear-gradient(90deg,rgba(5,7,11,.97) 0%,rgba(5,7,11,.72) 42%,rgba(5,7,11,.25) 78%),url(${heroBackdrop})`}:undefined}>
          <div><span className="eyebrow"><Sparkles size={14}/> PREMIUM ENTERTAINMENT HUB</span><h2>Watch less UI.<br/>Watch more of what you love.</h2><p>Movies, shows, live TV, sports and audio across healthy, authorized sources—without repetitive clutter.</p><div className="hero-actions"><button className="primary" onClick={()=>setView('discover')}><Play size={17}/> Discover</button><button className="secondary" onClick={()=>setView('sources')}><Settings2 size={17}/> Manage sources</button></div></div>
        </section>
        <section className="quick-grid">
          <button onClick={()=>setView('live')}><RadioTower/><div><b>{live.length||'—'} Live channels</b><small>Source-aware playback</small></div></button>
          <button onClick={()=>setView('sports')}><Trophy/><div><b>{sports.length||'—'} Sports events</b><small>Today & upcoming</small></div></button>
          <button onClick={()=>setView('movies')}><Film/><div><b>Movies</b><small>Curated TMDB discovery</small></div></button>
          <button onClick={()=>setView('audio')}><Volume2/><div><b>Audio</b><small>Public radio & podcasts</small></div></button>
        </section>
        {loading?<div className="loading">Loading AstraWave…</div>:rows.map(r=><Row key={r.title} title={r.title} items={r.items} onPlay={play}/>)}
      </>}

      {view==='movies' && <><div className="section-intro"><span>FILMS</span><h2>Movies worth your time.</h2><p>Fresh discovery without dozens of duplicate rails.</p></div><Row title="Trending now" items={movies} onPlay={play}/><Row title="Popular with AstraWave viewers" items={[...movies].reverse().slice(0,18)} onPlay={play}/></>}
      {view==='tv' && <><div className="section-intro"><span>TELEVISION</span><h2>Your next series is here.</h2><p>Clean TV discovery with season and episode context inside each title.</p></div><Row title="Trending series" items={shows} onPlay={play}/><Row title="Worth a look" items={shows.slice(4).concat(shows.slice(0,4))} onPlay={play}/></>}

      {view==='live' && <><div className="section-intro"><span>LIVE</span><h2>Channels, not clutter.</h2><p>Duplicate feeds are merged, source counts are visible, and playback can fall through to another candidate.</p></div>
        <div className="channel-grid">{liveItems.slice(0,80).map(x=><button key={x.id} className="channel-card" onClick={()=>x.streamUrl&&play(x)}><div className="channel-logo">{x.posterUrl?<img src={x.posterUrl} alt=""/>:<RadioTower/>}</div><div><b>{x.title}</b><small>{x.subtitle}</small></div><Play size={15}/></button>)}</div></>}

      {view==='guide' && <><div className="section-intro"><span>GUIDE</span><h2>What’s on, at a glance.</h2><p>A compact guide built for scanning now/next instead of oversized channel tiles.</p></div>
        <div className="guide"><div className="guide-head"><span>Channel</span><span>Now</span><span>Next</span></div>{live.slice(0,60).map(c=><button className="guide-row" key={c.id} onClick={()=>c.streamUrl&&play({id:c.id,kind:'live',title:c.name,streamUrl:c.streamUrl,sources:c.sources})}><b>{c.name}</b><span>{c.now||'Live programming'}</span><span>{c.next||'Schedule data pending'}</span></button>)}</div></>}

      {view==='sports' && <><div className="section-intro"><span>SPORTS HUB</span><h2>Events first. Channels second.</h2><p>See today’s slate and the listed broadcaster; AstraWave only links playback when an enabled authorized source matches.</p></div>
        <div className="event-list">{sports.map(e=><article key={e.id}><div className="event-time">{new Date(e.startTime).toLocaleTimeString([],{hour:'numeric',minute:'2-digit'})}</div><div><small>{e.league}</small><b>{e.title}</b><span>{e.broadcaster||'Broadcaster not listed'}</span></div><Trophy/></article>)}</div><Row title="Live sports channels" items={liveItems.filter(x=>/sport|espn|nfl|nba|mlb|nhl|soccer|golf|tennis/i.test(x.subtitle||x.title)).slice(0,24)} onPlay={play}/></>}

      {view==='audio' && <><div className="section-intro"><span>LISTEN</span><h2>Radio and podcasts belong here too.</h2><p>Public station streams and podcast discovery live beside video without pretending they are the same thing.</p></div><Row title="Popular public radio" items={audio} onPlay={play}/><div className="audio-callout"><Music2/><div><b>Add your podcast feeds</b><p>Use Source Manager for RSS feeds you follow. No account lock-in.</p></div><button onClick={()=>setView('sources')}>Open Source Manager</button></div></>}

      {view==='discover' && <><div className="section-intro"><span>DISCOVER</span><h2>More ways in. Less repetition.</h2><p>Mix formats and intent-driven lists instead of cloning the same “trending” rail on every page.</p></div>
        <Row title="Big tonight" items={movies.slice(0,10).concat(shows.slice(0,10))} onPlay={play}/><Row title="Series to start this week" items={shows.slice(6).concat(shows.slice(0,6))} onPlay={play}/><Row title="Live right now" items={liveItems.slice(0,24)} onPlay={play}/><Row title="Listen next" items={audio.slice(0,20)} onPlay={play}/></>}

      {view==='sources' && <><div className="section-intro"><span>SOURCE MANAGER</span><h2>Your services. Your permissions.</h2><p>Keep provider setup separate from discovery so the rest of AstraWave stays clean.</p></div>
        <div className="source-grid">{sources.map((s,i)=><article key={s.id}><div className="source-icon">{s.type==='Xtream'?<ListVideo/>:s.type==='Podcast/RSS'?<Music2/>:<RadioTower/>}</div><div><small>{s.type}</small><b>{s.name}</b><p>{s.note}</p></div><span className={s.status==='Ready'?'status pass':'status'}>{s.status}</span>{i>0&&<button onClick={()=>{const next=sources.map((x,j)=>j===i?{...x,status:x.status==='Ready'?'Needs setup':'Ready' as any}:x);setSources(next);localStorage.setItem('astrawave:sources',JSON.stringify(next))}}>{s.status==='Ready'?'Disable':'Mark connected'}</button>}</article>)}</div>
        <div className="rights-note"><ShieldCheck/><div><b>Rights-first source policy</b><p>AstraWave does not add unauthorized premium streams. Customer-provided playlists and credentials should only be used when the customer has permission to access them.</p></div></div></>}

      {view==='diagnostics' && <><div className="section-intro"><span>DIAGNOSTICS</span><h2>Know what’s actually working.</h2><p>Provider health, browser playback capabilities and source-failover checks are visible instead of hidden behind generic errors.</p></div>
        <div className="diag-grid"><div className="diag-card"><Activity/><b>API health</b><p>Catalog, live/guide, sports and audio endpoints.</p><button className="primary small" onClick={runDiagnostics}><RefreshCw size={15}/> Run checks</button></div>
        <div className="diag-card"><Clapperboard/><b>Playback</b><p>HTML5 video: {typeof document!=='undefined'?'available':'checking'} • Inline playback enabled.</p><span className="status pass"><CheckCircle2 size={14}/> Browser ready</span></div>
        <div className="diag-card"><ShieldCheck/><b>Source policy</b><p>{sources.filter(s=>s.status==='Ready').length} enabled source profiles; customer authorization remains required.</p><span className="status pass">Policy active</span></div></div>
        {Object.keys(diag).length>0&&<div className="check-list">{Object.entries(diag).map(([k,v])=><div key={k}><span>{k}</span><b className={`check ${v}`}>{v}</b></div>)}</div>}</>}

      {searchOpen&&<div className="search-overlay"><div className="search-panel"><div className="search-input"><Search/><input autoFocus value={query} onChange={e=>setQuery(e.target.value)} placeholder="Movies, shows…"/><button onClick={()=>setSearchOpen(false)}><X/></button></div><div className="search-grid">{searchResults.map(x=><Card key={`${x.kind}-${x.id}`} item={x} onPlay={play}/>)}</div>{query&&searchResults.length===0&&<p className="empty">No matching titles yet.</p>}</div></div>}

      {playing?.streamUrl&&<div className="player-dock"><div className="player-top"><div><span>NOW PLAYING</span><b>{playing.title}</b><small>{playing.sources?.[sourceIndex]?.provider||playing.subtitle||'Authorized source'}</small></div><button onClick={()=>setPlaying(null)}><X/></button></div>
        {playing.kind==='podcast'?<audio key={playing.streamUrl} src={playing.streamUrl} controls autoPlay onError={()=>setPlayerError('This station stream is not browser-compatible right now.')}/>:<video key={playing.streamUrl} src={playing.streamUrl} controls playsInline autoPlay onError={()=>setPlayerError('Playback failed in this browser or source. Try the next authorized source.')}/>}
        {playerError&&<div className="player-error">{playerError}{playing.sources&&playing.sources.length>1&&<button onClick={nextSource}>Try next source</button>}</div>}
      </div>}
    </section>

    <style jsx global>{`
      *{box-sizing:border-box} body{margin:0;background:#07090d;color:#f7f8fb;font-family:Inter,ui-sans-serif,system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}
      button,input{font:inherit}.aw-shell{min-height:100vh;background:radial-gradient(circle at 70% 0%,#182034 0,transparent 35%),#07090d}
      .aw-side{position:fixed;inset:0 auto 0 0;width:232px;padding:20px 14px;background:rgba(8,10,15,.96);border-right:1px solid #202630;z-index:40;display:flex;flex-direction:column}
      .side-top{display:flex;align-items:center;justify-content:space-between}.aw-brand{display:flex;align-items:center;gap:10px;color:white;text-decoration:none}.aw-brand>span,.logo-orb{display:grid;place-items:center;width:38px;height:38px;border-radius:12px;background:linear-gradient(135deg,#7c5cff,#36d1ff);font-weight:900}.aw-brand b{font-size:18px}
      .aw-side nav{display:grid;gap:4px;margin-top:28px}.nav-item{display:flex;align-items:center;gap:12px;border:0;background:transparent;color:#9da6b5;padding:11px 12px;border-radius:11px;text-align:left;cursor:pointer}.nav-item:hover,.nav-item.active{color:white;background:#171c25}.nav-item.active{box-shadow:inset 3px 0 #8a6cff}.side-footer{margin-top:auto;border-top:1px solid #222834;padding:16px 8px 0;display:flex;gap:10px;align-items:center}.side-footer b,.side-footer small{display:block}.side-footer small{color:#7f8998;font-size:11px}.health-dot{width:9px;height:9px;border-radius:50%;background:#43d17b;box-shadow:0 0 0 5px rgba(67,209,123,.09)}
      .aw-main{margin-left:232px;padding:0 28px 80px;max-width:1700px}.aw-top{height:84px;display:flex;align-items:center;justify-content:space-between;position:sticky;top:0;z-index:25;background:linear-gradient(#07090df2,#07090dd5,transparent);backdrop-filter:blur(12px)}.aw-top small,.section-intro>span,.eyebrow{color:#8f99aa;font-size:11px;letter-spacing:.14em;font-weight:800}.aw-top h1{font-size:20px;margin:3px 0 0}.top-actions{display:flex;gap:8px}.icon-btn,.search-pill{border:1px solid #242b36;background:#10141b;color:#eef2f7;border-radius:12px;height:42px;display:flex;align-items:center;gap:8px;padding:0 13px;cursor:pointer}.icon-btn{width:42px;justify-content:center;padding:0}.mobile-only{display:none}
      .aw-hero{min-height:390px;border:1px solid #252b36;border-radius:24px;background:linear-gradient(120deg,#121827,#0b0e14);background-size:cover;background-position:center;display:flex;align-items:flex-end;overflow:hidden;padding:42px;margin-bottom:18px}.aw-hero>div{max-width:630px}.aw-hero h2,.section-intro h2{font-size:clamp(34px,5vw,64px);line-height:.98;letter-spacing:-.045em;margin:14px 0}.aw-hero p,.section-intro p{color:#a9b1bd;font-size:16px;line-height:1.6;max-width:650px}.eyebrow{display:inline-flex;align-items:center;gap:7px;color:#b9abff}.hero-actions{display:flex;gap:10px;margin-top:24px}.primary,.secondary{border:0;border-radius:12px;padding:12px 17px;font-weight:800;display:inline-flex;align-items:center;justify-content:center;gap:8px;cursor:pointer}.primary{background:#f4f6fa;color:#0a0c10}.secondary{background:#171c25;color:white;border:1px solid #2a323f}.primary.small{padding:9px 12px;font-size:13px}
      .quick-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:10px;margin:18px 0 30px}.quick-grid button{border:1px solid #202630;background:#0d1117;color:white;border-radius:16px;padding:16px;display:flex;gap:12px;align-items:center;text-align:left;cursor:pointer}.quick-grid b,.quick-grid small{display:block}.quick-grid small{color:#7f8998;margin-top:3px}.quick-grid svg{color:#9f8dff}
      .aw-row{margin:30px 0}.row-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:12px}.row-head h3{margin:0;font-size:19px}.row-head span{color:#707b8b;font-size:12px}.card-strip{display:grid;grid-auto-flow:column;grid-auto-columns:minmax(145px,12vw);gap:12px;overflow-x:auto;padding:2px 2px 12px;scrollbar-width:thin}.card-link{color:inherit;text-decoration:none}.aw-card{min-width:0}.aw-poster{aspect-ratio:2/3;background:#151a23;border:1px solid #252c38;border-radius:15px;background-size:cover;background-position:center;position:relative;display:grid;place-items:center;overflow:hidden;transition:.2s transform,.2s border-color}.aw-card:hover .aw-poster{transform:translateY(-3px);border-color:#6555a8}.aw-poster>span{font-size:38px;color:#465064;font-weight:900}.aw-card>strong,.aw-card>small{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.aw-card>strong{font-size:13px;margin:8px 2px 0}.aw-card>small{font-size:11px;color:#7d8795;margin:3px 2px}.badge{position:absolute;top:8px;left:8px;font-size:9px;padding:5px 7px;border-radius:7px}.badge.live{background:#e73955}.badge.sport{background:#2b77ff}.badge.audio{background:#875bff}.poster-play{position:absolute;right:8px;bottom:8px;width:32px;height:32px;border:0;border-radius:50%;background:#fff;color:#080a0d;display:grid;place-items:center;cursor:pointer}
      .section-intro{padding:30px 0 14px;border-bottom:1px solid #1d222c;margin-bottom:20px}.section-intro h2{font-size:clamp(34px,4vw,54px)}.section-intro p{margin-bottom:10px}
      .channel-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:8px}.channel-card{border:1px solid #202630;background:#0d1117;color:white;border-radius:13px;padding:11px;display:grid;grid-template-columns:44px 1fr auto;align-items:center;gap:10px;text-align:left;cursor:pointer}.channel-card:hover{background:#141922}.channel-logo{width:44px;height:44px;border-radius:10px;background:#171d27;display:grid;place-items:center;overflow:hidden}.channel-logo img{max-width:90%;max-height:90%}.channel-card b,.channel-card small{display:block}.channel-card small{color:#7f8997;margin-top:4px;font-size:11px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
      .guide{border:1px solid #232a35;border-radius:16px;overflow:hidden}.guide-head,.guide-row{display:grid;grid-template-columns:minmax(170px,1fr) 1.4fr 1.4fr;gap:12px;padding:13px 16px}.guide-head{background:#141922;color:#758092;font-size:11px;text-transform:uppercase;letter-spacing:.1em}.guide-row{width:100%;border:0;border-top:1px solid #1a2029;background:#0b0f14;color:#dfe4ec;text-align:left;cursor:pointer}.guide-row:hover{background:#111720}.guide-row span{color:#8d97a5;font-size:13px}
      .event-list{display:grid;gap:8px}.event-list article{border:1px solid #212833;background:#0c1016;border-radius:14px;padding:13px 15px;display:grid;grid-template-columns:80px 1fr auto;align-items:center;gap:12px}.event-time{font-weight:900}.event-list small,.event-list b,.event-list span{display:block}.event-list small{color:#7d8796}.event-list span{color:#9ba4b1;font-size:12px;margin-top:4px}
      .audio-callout,.rights-note{margin-top:28px;border:1px solid #293244;background:linear-gradient(135deg,#131827,#0c1017);border-radius:18px;padding:20px;display:flex;gap:14px;align-items:center}.audio-callout div,.rights-note div{flex:1}.audio-callout p,.rights-note p{color:#8e98a7;margin:4px 0}.audio-callout button{background:#f5f7fb;border:0;border-radius:10px;padding:10px 13px;font-weight:800;cursor:pointer}
      .source-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.source-grid article{border:1px solid #222a35;background:#0d1118;border-radius:16px;padding:16px;display:grid;grid-template-columns:46px 1fr auto;gap:12px;align-items:start}.source-icon{width:46px;height:46px;border-radius:12px;background:#171d27;display:grid;place-items:center;color:#a491ff}.source-grid small,.source-grid b{display:block}.source-grid p{color:#8792a1;font-size:12px;margin:5px 0 0}.source-grid article>button{grid-column:2/4;justify-self:start;background:transparent;color:#d6dce5;border:1px solid #2a3340;border-radius:9px;padding:7px 10px;cursor:pointer}.status{font-size:10px;border:1px solid #39414d;padding:5px 7px;border-radius:999px;color:#9da6b4}.status.pass{border-color:#24673e;color:#65dd8d;background:#0e2417}
      .diag-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:10px}.diag-card{border:1px solid #232a35;border-radius:16px;background:#0d1117;padding:18px}.diag-card>b{display:block;font-size:17px;margin:12px 0 4px}.diag-card p{color:#8994a3;min-height:44px}.check-list{margin-top:14px;border:1px solid #222a35;border-radius:14px;overflow:hidden}.check-list>div{display:flex;justify-content:space-between;padding:12px 14px;border-top:1px solid #1b212a}.check-list>div:first-child{border-top:0}.check{font-size:11px;text-transform:uppercase}.check.pass{color:#58d985}.check.fail{color:#ff6c7e}.check.checking{color:#e9be54}
      .loading,.empty{padding:30px;color:#7f8998}.search-overlay,.onboard{position:fixed;inset:0;background:rgba(3,5,8,.82);backdrop-filter:blur(18px);z-index:90;display:grid;place-items:center;padding:22px}.search-panel{width:min(1000px,94vw);max-height:86vh;background:#0a0d12;border:1px solid #29303c;border-radius:20px;overflow:auto;padding:16px}.search-input{display:flex;gap:10px;align-items:center;border:1px solid #28303b;border-radius:12px;padding:0 12px;background:#11161d}.search-input input{flex:1;background:transparent;border:0;outline:0;color:white;height:48px}.search-input button{background:transparent;border:0;color:white;cursor:pointer}.search-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(140px,1fr));gap:12px;margin-top:18px}
      .onboard-card{width:min(780px,96vw);background:linear-gradient(145deg,#111722,#080b10);border:1px solid #2b3442;border-radius:26px;padding:34px}.onboard-card h1{font-size:44px;letter-spacing:-.04em;margin:15px 0}.onboard-card>p{color:#98a3b2;line-height:1.6}.onboard-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:9px;margin:24px 0}.onboard-grid>div{background:#0d1219;border:1px solid #232b37;border-radius:14px;padding:14px}.onboard-grid svg{color:#9d8bff}.onboard-grid b,.onboard-grid small{display:block}.onboard-grid b{margin:8px 0 4px}.onboard-grid small{color:#7d8795}
      .player-dock{position:fixed;right:20px;bottom:20px;width:min(520px,calc(100vw - 40px));background:#0a0d12;border:1px solid #313a48;border-radius:18px;overflow:hidden;z-index:70;box-shadow:0 30px 80px #000a}.player-top{display:flex;justify-content:space-between;align-items:center;padding:12px 14px}.player-top span,.player-top b,.player-top small{display:block}.player-top span{font-size:9px;color:#8a95a4;letter-spacing:.12em}.player-top small{color:#7c8795;font-size:11px}.player-top button{background:transparent;border:0;color:white;cursor:pointer}.player-dock video{display:block;width:100%;aspect-ratio:16/9;background:#000}.player-dock audio{width:100%;display:block}.player-error{padding:10px 12px;background:#351219;color:#ff9ba8;font-size:12px}.player-error button{margin-left:8px;border:0;border-radius:8px;padding:6px 8px;font-weight:800;cursor:pointer}
      @media(max-width:980px){.mobile-only{display:flex}.aw-side{transform:translateX(-105%);transition:.2s}.aw-side.open{transform:translateX(0)}.aw-main{margin-left:0;padding:0 18px 70px}.quick-grid{grid-template-columns:repeat(2,1fr)}.diag-grid{grid-template-columns:1fr}.source-grid{grid-template-columns:1fr}.aw-top{height:70px}.aw-top>div:nth-child(2){margin-right:auto;margin-left:10px}.search-pill span{display:none}}
      @media(max-width:640px){.aw-main{padding:0 12px 80px}.aw-hero{min-height:360px;padding:24px;border-radius:18px}.aw-hero h2{font-size:40px}.quick-grid{grid-template-columns:1fr 1fr}.quick-grid button{padding:12px}.card-strip{grid-auto-columns:132px}.section-intro h2{font-size:36px}.guide{overflow-x:auto}.guide-head,.guide-row{min-width:700px}.event-list article{grid-template-columns:64px 1fr auto}.onboard-card{padding:24px}.onboard-card h1{font-size:36px}.onboard-grid{grid-template-columns:1fr}.source-grid article{grid-template-columns:42px 1fr}.source-grid .status{grid-column:2}.audio-callout,.rights-note{align-items:flex-start;flex-wrap:wrap}}
    `}</style>
  </main>
}
