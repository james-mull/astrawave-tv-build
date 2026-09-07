'use client';

import Link from 'next/link';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ArrowLeft, Headphones, Music2, Play, Podcast, RadioTower, Search } from 'lucide-react';

type Kind='radio'|'podcast'|'music';
type AudioItem={id:string;kind:Kind;title:string;subtitle?:string;posterUrl?:string;streamUrl?:string;feedUrl?:string;externalUrl?:string;country?:string;genre?:string};
type DirectoryResponse={kind:Kind;count:number;items:AudioItem[];error?:string};

const radioGenres=['news','sports','talk','pop','rock','country','jazz','classical','hip hop','electronic','latin','reggae','oldies','christian'];
const countries=[['US','United States'],['CA','Canada'],['GB','United Kingdom'],['AU','Australia'],['MX','Mexico'],['DE','Germany'],['FR','France'],['ES','Spain'],['BR','Brazil'],['JP','Japan']];

function Tile({item,onPlay}:{item:AudioItem;onPlay:(item:AudioItem)=>void}){
  return <article className="audioTile">
    <div className="audioArt" style={item.posterUrl?{backgroundImage:`url(${item.posterUrl})`}:undefined}>{!item.posterUrl&&<span>{item.kind==='radio'?'R':item.kind==='podcast'?'P':'M'}</span>}</div>
    <div className="audioMeta"><b>{item.title}</b><small>{item.subtitle||item.genre||item.country||item.kind}</small></div>
    {item.streamUrl&&<button className="primaryBtn" onClick={()=>onPlay(item)}><Play size={14}/>Play</button>}
    {!item.streamUrl&&item.externalUrl&&<a className="ghostBtn" href={item.externalUrl} target="_blank" rel="noreferrer">Open</a>}
  </article>
}

export default function AudioDirectoryPage(){
  const [kind,setKind]=useState<Kind>('radio');
  const [query,setQuery]=useState('');
  const [country,setCountry]=useState('');
  const [genre,setGenre]=useState('');
  const [items,setItems]=useState<AudioItem[]>([]);
  const [offset,setOffset]=useState(0);
  const [loading,setLoading]=useState(false);
  const [error,setError]=useState('');
  const [nowPlaying,setNowPlaying]=useState<AudioItem|null>(null);
  const pageSize=kind==='radio'?180:120;

  const load=useCallback(async(reset=true)=>{
    const nextOffset=reset?0:offset;
    setLoading(true);setError('');
    try{
      const params=new URLSearchParams({kind,limit:String(pageSize),offset:String(nextOffset)});
      if(query.trim())params.set('q',query.trim());
      if(kind==='radio'&&country)params.set('country',country);
      if(kind==='radio'&&genre)params.set('tag',genre);
      const response=await fetch(`/api/astrawave/audio-directory?${params.toString()}`,{cache:'no-store'});
      const data=await response.json() as DirectoryResponse;
      if(!response.ok)throw new Error(data.error||'Audio directory unavailable');
      setItems(current=>reset?data.items:[...current,...data.items.filter(x=>!current.some(y=>y.id===x.id))]);
      setOffset(nextOffset+data.items.length);
    }catch(e){setError(e instanceof Error?e.message:'Audio directory unavailable')}
    finally{setLoading(false)}
  },[kind,query,country,genre,offset,pageSize]);

  useEffect(()=>{setItems([]);setOffset(0);void load(true)},[kind,country,genre]);
  const subtitle=useMemo(()=>kind==='radio'?'Worldwide internet radio directory':kind==='podcast'?'Search a broad publisher podcast directory':'Search songs and artists; previews play where available', [kind]);

  function play(item:AudioItem){if(item.streamUrl)setNowPlaying(item)}

  return <main className="audioPage">
    <header className="audioHeader"><Link href="/app" className="ghostBtn"><ArrowLeft size={16}/>AstraWave</Link><div><small>ASTRAWAVE AUDIO</small><h1>Music, Podcasts & Radio</h1><p>{subtitle}</p></div><Headphones size={28}/></header>

    <section className="audioTabs">
      <button className={kind==='radio'?'active':''} onClick={()=>setKind('radio')}><RadioTower size={17}/>Radio</button>
      <button className={kind==='podcast'?'active':''} onClick={()=>setKind('podcast')}><Podcast size={17}/>Podcasts</button>
      <button className={kind==='music'?'active':''} onClick={()=>setKind('music')}><Music2 size={17}/>Music</button>
    </section>

    <section className="audioSearch"><Search size={17}/><input value={query} onChange={e=>setQuery(e.target.value)} onKeyDown={e=>{if(e.key==='Enter')void load(true)}} placeholder={kind==='radio'?'Search stations':kind==='podcast'?'Search podcasts':'Search songs or artists'}/><button className="primaryBtn" onClick={()=>load(true)}>Search</button></section>

    {kind==='radio'&&<><section className="filterStrip"><button className={!genre?'active':''} onClick={()=>setGenre('')}>All genres</button>{radioGenres.map(g=><button key={g} className={genre===g?'active':''} onClick={()=>setGenre(g)}>{g}</button>)}</section><section className="filterStrip"><button className={!country?'active':''} onClick={()=>setCountry('')}>Worldwide</button>{countries.map(([code,name])=><button key={code} className={country===code?'active':''} onClick={()=>setCountry(code)}>{name}</button>)}</section></>}

    <div className="audioCount">{items.length.toLocaleString()} loaded{kind==='radio'?' • load more to keep browsing':''}</div>
    {error&&<div className="setupNotice"><h3>Audio directory unavailable</h3><p>{error}</p></div>}
    <section className="audioGrid">{items.map(item=><Tile key={item.id} item={item} onPlay={play}/>)}</section>
    {loading&&<div className="setupNotice"><p>Loading audio catalog…</p></div>}
    {!loading&&items.length>=pageSize&&kind==='radio'&&<button className="ghostBtn audioMore" onClick={()=>load(false)}>Load {pageSize} more stations</button>}

    {nowPlaying?.streamUrl&&<aside className="audioPlayer"><div><b>{nowPlaying.title}</b><small>{nowPlaying.subtitle||'Now playing'}</small></div><audio src={nowPlaying.streamUrl} controls autoPlay/><button onClick={()=>setNowPlaying(null)}>×</button></aside>}

    <style jsx>{`
      .audioPage{min-height:100vh;background:#07090d;color:#f5f7fb;padding:28px clamp(16px,4vw,56px) 110px}.audioHeader{display:grid;grid-template-columns:auto 1fr auto;gap:22px;align-items:start;max-width:1500px;margin:auto}.audioHeader h1{font-size:clamp(32px,5vw,62px);margin:4px 0 8px;letter-spacing:-.045em}.audioHeader small{letter-spacing:.16em;color:#8d96a6;font-weight:800}.audioHeader p{margin:0;color:#9ba3b1}.audioTabs,.filterStrip{display:flex;gap:9px;overflow-x:auto;max-width:1500px;margin:28px auto 0;padding-bottom:3px}.audioTabs button,.filterStrip button{white-space:nowrap;border:1px solid #202631;background:#0d1118;color:#9da5b3;border-radius:999px;padding:10px 14px;display:flex;align-items:center;gap:7px}.audioTabs button.active,.filterStrip button.active{background:#eef2ff;color:#0a0d12;border-color:#eef2ff}.audioSearch{max-width:1500px;margin:18px auto 0;display:flex;gap:10px;align-items:center;background:#0d1118;border:1px solid #202631;border-radius:16px;padding:8px 10px 8px 14px}.audioSearch input{flex:1;background:transparent;border:0;outline:0;color:#fff;font-size:16px;min-width:0}.audioCount{max-width:1500px;margin:20px auto 10px;color:#798292;font-size:13px}.audioGrid{max-width:1500px;margin:auto;display:grid;grid-template-columns:repeat(auto-fill,minmax(230px,1fr));gap:12px}.audioTile{background:#0d1118;border:1px solid #1c222d;border-radius:18px;padding:13px;display:grid;grid-template-columns:58px 1fr auto;gap:12px;align-items:center}.audioArt{width:58px;height:58px;border-radius:13px;background:#151b25 center/cover;display:grid;place-items:center;font-size:22px;font-weight:900;color:#778195}.audioMeta{min-width:0}.audioMeta b,.audioMeta small{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.audioMeta small{margin-top:5px;color:#818b9b}.audioTile .primaryBtn,.audioTile .ghostBtn{padding:8px 10px;min-height:36px}.audioMore{display:flex;margin:24px auto}.audioPlayer{position:fixed;left:50%;bottom:20px;transform:translateX(-50%);width:min(760px,calc(100% - 24px));background:#10151e;border:1px solid #293241;border-radius:18px;padding:12px 14px;display:grid;grid-template-columns:minmax(120px,1fr) minmax(240px,2fr) auto;gap:12px;align-items:center;box-shadow:0 20px 60px #000a;z-index:50}.audioPlayer b,.audioPlayer small{display:block}.audioPlayer small{color:#8892a2;margin-top:3px}.audioPlayer audio{width:100%}.audioPlayer button{border:0;background:transparent;color:#fff;font-size:24px}@media(max-width:640px){.audioPage{padding:18px 12px 120px}.audioHeader{grid-template-columns:1fr auto}.audioHeader>a{grid-column:1/-1;width:max-content}.audioHeader h1{font-size:36px}.audioGrid{grid-template-columns:1fr}.audioTile{grid-template-columns:52px 1fr auto}.audioArt{width:52px;height:52px}.audioPlayer{grid-template-columns:1fr auto}.audioPlayer audio{grid-column:1/-1;grid-row:2}.audioSearch{position:sticky;top:8px;z-index:20;backdrop-filter:blur(20px)}}
    `}</style>
  </main>
}
