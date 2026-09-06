'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { ArrowLeft, ExternalLink, Film, Play, ShieldCheck, Star, Tv2 } from 'lucide-react';
import { AstraWaveApi, CatalogItem, SourceCandidate, TitleDetails } from '../../../../../lib/astrawave-api';

export default function TitlePlaybackPage({ params }: { params: Promise<{ kind: string; id: string }> }) {
  const [route,setRoute]=useState<{kind:'movie'|'series';id:string}|null>(null);
  const [details,setDetails]=useState<TitleDetails|null>(null);
  const [sources,setSources]=useState<SourceCandidate[]>([]);
  const [selected,setSelected]=useState<SourceCandidate|null>(null);
  const [loading,setLoading]=useState(true);
  const [error,setError]=useState('');

  useEffect(()=>{params.then(value=>setRoute({kind:value.kind==='series'?'series':'movie',id:value.id}))},[params]);
  useEffect(()=>{
    if(!route)return;
    setLoading(true);setError('');
    Promise.allSettled([AstraWaveApi.titleDetails(route.kind,route.id),AstraWaveApi.sources(route.kind,route.id)]).then(([d,s])=>{
      if(d.status==='fulfilled')setDetails(d.value.details||null);
      if(s.status==='fulfilled'){setSources(s.value);setSelected(s.value.find(x=>x.url)||null)}
      if(d.status==='rejected'&&s.status==='rejected')setError('AstraWave could not load title metadata or sources.')
    }).finally(()=>setLoading(false));
  },[route]);

  const related=useMemo(()=>details?.related||[],[details]);
  const meta=details?[details.releaseDate?.slice(0,4),details.rating,details.runtimeMinutes?`${details.runtimeMinutes} min`:null,details.seasons?`${details.seasons} seasons`:null,details.score?`★ ${details.score.toFixed(1)}`:null].filter(Boolean):[];

  return <main className="title-shell">
    <Link href="/app" className="back"><ArrowLeft size={17}/> Back to AstraWave</Link>
    {loading&&<div className="state">Loading title…</div>}
    {error&&<div className="state error">{error}</div>}
    {!loading&&details&&<>
      <section className="title-hero" style={details.backdropUrl?{backgroundImage:`linear-gradient(90deg,rgba(5,7,12,.98),rgba(5,7,12,.76) 46%,rgba(5,7,12,.2)),url(${details.backdropUrl})`}:undefined}>
        {details.posterUrl&&<img className="hero-poster" src={details.posterUrl} alt=""/>}
        <div className="hero-copy"><span className="eyebrow">{details.kind==='movie'?<Film size={14}/>:<Tv2 size={14}/>} {details.kind==='movie'?'MOVIE':'SERIES'}</span><h1>{details.title}</h1>{details.tagline&&<p className="tagline">{details.tagline}</p>}<div className="meta">{meta.map(x=><span key={String(x)}>{x}</span>)}</div><p className="overview">{details.overview||'No synopsis available.'}</p><div className="genres">{details.genres.map(g=><span key={g}>{g}</span>)}</div><div className="hero-actions">{selected?.url&&<button className="primary" onClick={()=>document.getElementById('player')?.scrollIntoView({behavior:'smooth'})}><Play size={16}/> Play best source</button>}{details.trailers[0]&&<a className="secondary" href={details.trailers[0].url} target="_blank" rel="noreferrer"><ExternalLink size={16}/> Watch trailer</a>}</div></div>
      </section>

      <section className="detail-grid"><article><h2>Cast</h2><div className="people">{details.cast.map(person=><div key={person.id}>{person.profileUrl?<img src={person.profileUrl} alt=""/>:<div className="person-fallback">{person.name.charAt(0)}</div>}<b>{person.name}</b><small>{person.role}</small></div>)}</div></article><aside><h2>About</h2>{details.crew.map(person=><div className="credit" key={`${person.id}:${person.role}`}><span>{person.role}</span><b>{person.name}</b></div>)}{details.status&&<div className="credit"><span>Status</span><b>{details.status}</b></div>}{details.episodes&&<div className="credit"><span>Episodes</span><b>{details.episodes}</b></div>}</aside></section>

      {details.trailers.length>0&&<section className="section"><div className="section-head"><div><small>TITLE-SPECIFIC MEDIA</small><h2>Trailers & extras</h2></div><span>Only for {details.title}</span></div><div className="trailers">{details.trailers.map(video=><a href={video.url} target="_blank" rel="noreferrer" key={video.key}><div><Play/></div><b>{video.name}</b><small>{video.type}{video.official?' • Official':''}</small></a>)}</div></section>}

      <section className="section" id="player"><div className="section-head"><div><small>PLAYBACK</small><h2>Available sources</h2></div><span>{sources.length} approved candidate{sources.length===1?'':'s'}</span></div>{sources.length===0?<div className="state"><ShieldCheck/> No approved playable source is currently available. Add permitted providers in the Web Control Center.</div>:<><div className="source-list">{sources.map(source=><button key={source.id} className={selected?.id===source.id?'active':''} onClick={()=>setSelected(source)}><div><b>{source.provider}</b><small>{[source.quality,source.direct?'Direct':null,source.licenseLabel].filter(Boolean).join(' • ')}</small></div><span>{selected?.id===source.id?'Selected':'Choose'}</span></button>)}</div>{selected?.url&&<div className="player"><video key={selected.url} src={selected.url} controls playsInline/><div><ShieldCheck size={15}/>{selected.provider}{selected.quality?` • ${selected.quality}`:''}{selected.licenseLabel?` • ${selected.licenseLabel}`:''}</div></div>}</>}</section>

      {related.length>0&&<section className="section"><div className="section-head"><div><small>KEEP WATCHING</small><h2>More like this</h2></div></div><div className="related">{related.map(item=><RelatedCard item={item} key={`${item.kind}:${item.id}`}/>)}</div></section>}
    </>}
    {!loading&&!details&&<div className="state">TMDB metadata is not configured for this deployment. Playback sources can still appear when available.</div>}

    <style jsx global>{`
      *{box-sizing:border-box}body{margin:0;background:#07090d;color:#f7f8fb;font-family:Inter,system-ui,sans-serif}a{text-decoration:none;color:inherit}.title-shell{min-height:100vh;padding:22px max(18px,4vw) 80px}.back{display:inline-flex;align-items:center;gap:7px;color:#a996ff;margin-bottom:18px}.state{border:1px solid #252d38;background:#0e131a;border-radius:15px;padding:20px;color:#a3adba;display:flex;gap:8px;align-items:center}.state.error{border-color:#542632;background:#251016;color:#ff9eaa}.title-hero{min-height:520px;border:1px solid #252d38;border-radius:24px;background:#111723 center/cover;display:flex;gap:30px;align-items:flex-end;padding:42px;overflow:hidden}.hero-poster{width:min(230px,24vw);border-radius:16px;box-shadow:0 24px 60px #0009}.hero-copy{max-width:760px}.eyebrow{display:inline-flex;align-items:center;gap:7px;color:#aa98ff;font-size:11px;letter-spacing:.12em}.hero-copy h1{font-size:clamp(40px,6vw,76px);line-height:.95;letter-spacing:-.055em;margin:13px 0}.tagline{font-size:19px;color:#c4cad4}.overview{color:#b0b8c4;line-height:1.65;font-size:16px}.meta,.genres,.hero-actions{display:flex;gap:8px;flex-wrap:wrap}.meta span,.genres span{border:1px solid #354052;background:#10151ddd;border-radius:999px;padding:6px 9px;font-size:11px}.genres span{color:#aeb7c5}.hero-actions{margin-top:22px}.primary,.secondary{border:0;border-radius:11px;padding:11px 15px;font-weight:800;display:inline-flex;align-items:center;gap:7px}.primary{background:white;color:#080a0e}.secondary{background:#161c25;color:white;border:1px solid #303946}.detail-grid{display:grid;grid-template-columns:1fr 320px;gap:18px;margin:24px 0}.detail-grid>article,.detail-grid>aside{border:1px solid #222a35;background:#0c1016;border-radius:18px;padding:18px}.detail-grid h2{margin-top:0}.people{display:grid;grid-template-columns:repeat(auto-fill,minmax(110px,1fr));gap:12px}.people img,.person-fallback{width:74px;height:74px;border-radius:50%;object-fit:cover;background:#171d27;display:grid;place-items:center;font-size:22px}.people b,.people small{display:block}.people b{font-size:12px;margin-top:7px}.people small{font-size:10px;color:#7f8997}.credit{display:flex;justify-content:space-between;gap:12px;padding:9px 0;border-top:1px solid #202732}.credit:first-of-type{border-top:0}.credit span{color:#7e8998}.section{margin-top:32px}.section-head{display:flex;justify-content:space-between;align-items:end;margin-bottom:12px}.section-head small{color:#8c7ce8;letter-spacing:.12em}.section-head h2{margin:4px 0 0}.section-head>span{color:#788392;font-size:11px}.trailers{display:grid;grid-template-columns:repeat(auto-fill,minmax(210px,1fr));gap:10px}.trailers a{border:1px solid #242c38;background:#0d1117;border-radius:14px;padding:12px}.trailers a>div{aspect-ratio:16/9;border-radius:10px;background:linear-gradient(135deg,#261e47,#111720);display:grid;place-items:center}.trailers b,.trailers small{display:block}.trailers b{margin-top:8px}.trailers small{color:#7f8997}.source-list{display:grid;grid-template-columns:repeat(auto-fill,minmax(270px,1fr));gap:8px}.source-list button{display:flex;justify-content:space-between;gap:12px;text-align:left;border:1px solid #252d38;background:#0d1117;color:white;border-radius:12px;padding:13px}.source-list button.active{border-color:#765ee4;background:#211a41}.source-list b,.source-list small{display:block}.source-list small{color:#8893a2;margin-top:4px}.source-list span{color:#a998ff;font-size:11px}.player{margin-top:12px;border:1px solid #252d38;border-radius:16px;overflow:hidden;background:#05070a}.player video{width:100%;max-height:68vh;background:#000;display:block}.player>div{padding:11px 13px;color:#8f9aa8;display:flex;align-items:center;gap:6px}.related{display:grid;grid-auto-flow:column;grid-auto-columns:150px;gap:11px;overflow-x:auto;padding-bottom:10px}.related-card img,.related-fallback{width:100%;aspect-ratio:2/3;object-fit:cover;border-radius:13px;background:#151a23}.related-fallback{display:grid;place-items:center;font-size:32px}.related-card b,.related-card small{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.related-card b{font-size:12px;margin-top:7px}.related-card small{color:#7f8997;font-size:10px}@media(max-width:850px){.title-hero{padding:24px;min-height:440px}.hero-poster{display:none}.detail-grid{grid-template-columns:1fr}}@media(max-width:580px){.title-shell{padding:14px 12px 70px}.title-hero{border-radius:18px;min-height:400px;padding:20px}.hero-copy h1{font-size:42px}.related{grid-auto-columns:130px}.section-head{align-items:flex-start;flex-direction:column}}
    `}</style>
  </main>
}

function RelatedCard({item}:{item:CatalogItem}){return <Link href={`/app/title/${item.kind}/${item.id}`} className="related-card">{item.posterUrl?<img src={item.posterUrl} alt=""/>:<div className="related-fallback">{item.title[0]}</div>}<b>{item.title}</b><small>{item.subtitle}{item.score?` • ★ ${item.score.toFixed(1)}`:''}</small></Link>}
