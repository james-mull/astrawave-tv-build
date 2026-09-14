'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';

type Kind='movie'|'series';
type Def={id:string;title:string;kind:Kind;category:string;featured?:boolean;service?:boolean};
type Item={id:string;kind:Kind;title:string;subtitle?:string;posterUrl?:string;overview?:string};
type Prefs={hidden:string[];pinned:string[];order:string[]};
const emptyPrefs:Prefs={hidden:[],pinned:[],order:[]};

function prefKey(kind:Kind){return `astrawave:builtin-catalogs:${kind}`}
function loadPrefs(kind:Kind):Prefs{try{return{...emptyPrefs,...JSON.parse(localStorage.getItem(prefKey(kind))||'{}')}}catch{return emptyPrefs}}
function savePrefs(kind:Kind,prefs:Prefs){try{localStorage.setItem(prefKey(kind),JSON.stringify(prefs))}catch{}}
function sectionOrder(kind:Kind){return kind==='movie'?['Streaming Services','Trending & Box Office','New Releases & Awards','Movie Genres','Seasonal & Occasion','Premium Cinema & World']:['Streaming Services','Trending & Popular','New Episodes & Prestige','TV Genres','Reality, Docs & Lifestyle','Kids, Anime & International']}
function sectionFor(def:Def,kind:Kind){
  if(def.service)return 'Streaming Services';
  if(kind==='movie'){
    if(def.category==='Discovery')return 'Trending & Box Office';
    if(def.category==='Editorial')return 'New Releases & Awards';
    if(def.category==='Genres')return 'Movie Genres';
    if(def.category==='Moods')return 'Seasonal & Occasion';
    return 'Premium Cinema & World';
  }
  if(def.category==='Discovery')return 'Trending & Popular';
  if(def.category==='Editorial')return 'New Episodes & Prestige';
  if(def.category==='Genres')return 'TV Genres';
  if(def.category==='Moods')return 'Reality, Docs & Lifestyle';
  return 'Kids, Anime & International';
}
function sectionSubtitle(section:string,kind:Kind){
  if(section==='Streaming Services')return `Browse ${kind==='movie'?'movies':'shows'} by Netflix, Prime Video, Disney+, Max, Apple TV+, Hulu, Peacock and Paramount+.`;
  if(section==='Trending & Box Office')return 'Popular, most watched, anticipated and theatrical movie discovery.';
  if(section==='New Releases & Awards')return "Fresh movies, critics' picks, festivals, awards and standout cinema.";
  if(section==='Movie Genres')return 'Action, comedy, drama, horror, sci-fi, documentary and more.';
  if(section==='Seasonal & Occasion')return 'Holiday, theme, runtime and occasion-based movie discovery.';
  if(section==='Premium Cinema & World')return '4K/HDR, Dolby, anime and international cinema.';
  if(section==='Trending & Popular')return 'The shows audiences are watching, rating and anticipating now.';
  if(section==='New Episodes & Prestige')return 'New series, fresh episodes, airing shows, limited series and prestige TV.';
  if(section==='TV Genres')return 'Crime, comedy, drama, mystery, sci-fi, fantasy, horror and more.';
  if(section==='Reality, Docs & Lifestyle')return 'Reality, true crime, sports, food, travel, nature and documentary TV.';
  return 'Kids TV, anime, K-dramas, British TV and series from around the world.';
}

export default function BuiltInCatalogBrowser({kind}:{kind:Kind}){
  const [definitions,setDefinitions]=useState<Def[]>([]);
  const [mappedIds,setMappedIds]=useState<Set<string>>(new Set());
  const [mdblistConfigured,setMdblistConfigured]=useState(false);
  const [prefs,setPrefs]=useState<Prefs>(emptyPrefs);
  const [category,setCategory]=useState('All');
  const [selected,setSelected]=useState<Def|null>(null);
  const [items,setItems]=useState<Item[]>([]);
  const [source,setSource]=useState('');
  const [previews,setPreviews]=useState<Record<string,{items:Item[];title:string;source:string}>>({});
  const [loading,setLoading]=useState(true);
  const [listLoading,setListLoading]=useState(false);
  const [manage,setManage]=useState(false);

  useEffect(()=>{
    setPrefs(loadPrefs(kind));setLoading(true);setPreviews({});
    fetch(`/api/astrawave/builtin-catalogs?kind=${kind}`,{cache:'no-store'})
      .then(r=>r.json())
      .then(data=>{setDefinitions(data.definitions||[]);setMappedIds(new Set<string>(data.mdblistMappedIds||[]));setMdblistConfigured(Boolean(data.mdblistConfigured));})
      .finally(()=>setLoading(false));
  },[kind]);

  useEffect(()=>{
    if(!definitions.length||manage)return;
    let cancelled=false;
    const leads=sectionOrder(kind).flatMap(section=>{
      const defs=definitions.filter(d=>sectionFor(d,kind)===section);
      const lead=section==='Streaming Services'
        ? defs.find(d=>d.service)
        : defs.find(d=>mappedIds.has(d.id))||defs.find(d=>d.featured)||defs[0];
      return lead?[[section,lead] as const]:[];
    });
    Promise.all(leads.map(async([section,lead])=>{
      try{
        const data=await fetch(`/api/astrawave/builtin-catalogs?id=${encodeURIComponent(lead.id)}`,{cache:'no-store'}).then(r=>r.json());
        return[section,{items:(data.items||[]).slice(0,14),title:lead.title,source:data.source||''}] as const;
      }catch{return[section,{items:[],title:lead.title,source:''}] as const}
    })).then(rows=>{if(!cancelled)setPreviews(Object.fromEntries(rows))});
    return()=>{cancelled=true};
  },[definitions,mappedIds,manage,kind]);

  const ordered=useMemo(()=>{const index=new Map(prefs.order.map((id,i)=>[id,i]));return definitions.filter(d=>!prefs.hidden.includes(d.id)).sort((a,b)=>(index.get(a.id)??9999)-(index.get(b.id)??9999)||Number(Boolean(b.featured))-Number(Boolean(a.featured))||a.title.localeCompare(b.title));},[definitions,prefs]);
  const categories=useMemo(()=>['All',...Array.from(new Set(definitions.map(d=>d.category)))],[definitions]);
  const visible=category==='All'?ordered:ordered.filter(d=>d.category===category);
  const sections=useMemo(()=>sectionOrder(kind).map(name=>({name,items:visible.filter(d=>sectionFor(d,kind)===name)})).filter(s=>s.items.length),[visible,kind]);

  function update(next:Prefs){setPrefs(next);savePrefs(kind,next)}
  function togglePinned(id:string){update({...prefs,pinned:prefs.pinned.includes(id)?prefs.pinned.filter(x=>x!==id):[...prefs.pinned,id]})}
  function hide(id:string){update({...prefs,hidden:[...new Set([...prefs.hidden,id])]})}
  function move(id:string,delta:number){const base=ordered.map(x=>x.id);const from=base.indexOf(id);if(from<0)return;const to=Math.max(0,Math.min(base.length-1,from+delta));base.splice(to,0,base.splice(from,1)[0]);update({...prefs,order:base})}
  function reset(){update(emptyPrefs)}
  async function open(def:Def){setSelected(def);setItems([]);setSource('');setListLoading(true);try{const data=await fetch(`/api/astrawave/builtin-catalogs?id=${encodeURIComponent(def.id)}`,{cache:'no-store'}).then(r=>r.json());setItems(data.items||[]);setSource(data.source||'AstraWave catalog')}finally{setListLoading(false)}}
  function card(def:Def){return <article key={def.id} className={selected?.id===def.id?'active':''}><button className="catalogOpen" onClick={()=>open(def)}><b>{def.title}</b><small>{def.category}{def.featured?' • Featured':''}{def.service?' • Service':''}{mappedIds.has(def.id)?' • MDBList':''}</small></button>{manage&&<div className="catalogManage"><button onClick={()=>togglePinned(def.id)}>{prefs.pinned.includes(def.id)?'Unpin':'Pin'}</button><button onClick={()=>move(def.id,-1)}>↑</button><button onClick={()=>move(def.id,1)}>↓</button><button onClick={()=>hide(def.id)}>Hide</button></div>}</article>}
  function preview(section:string){const data=previews[section];const shelf=data?.items||[];if(!shelf.length||manage)return null;return <div className="catalogPreviewRail"><div className="catalogPreviewMeta"><small>{data.title}{data.source?` • ${data.source}`:''}</small></div><div className="card-strip">{shelf.map(item=><Link className="card-link" key={`${section}:${item.kind}:${item.id}`} href={`/app/title/${item.kind}/${item.id}`}><article className="aw-card"><div className="aw-poster" style={item.posterUrl?{backgroundImage:`linear-gradient(to top,rgba(5,7,12,.92),rgba(5,7,12,.05)),url(${item.posterUrl})`}:undefined}>{!item.posterUrl&&<span>{item.title.slice(0,1)}</span>}</div><strong>{item.title}</strong><small>{item.subtitle||section}</small></article></Link>)}</div></div>}

  if(loading)return <div className="setupNotice"><p>Loading {kind==='movie'?'75 movie':'75 TV'} catalogs…</p></div>;
  const mappedCount=definitions.filter(d=>mappedIds.has(d.id)).length;
  const serviceCount=definitions.filter(d=>d.service).length;
  return <section className="builtinCatalogBrowser">
    <div className="row-head"><div><h3>{kind==='movie'?'Movies':'TV Shows'}</h3><small>{ordered.length} enabled • {definitions.length} total • {serviceCount} services • {mappedCount} MDBList mapped</small></div><button className="ghostBtn" onClick={()=>setManage(v=>!v)}>{manage?'Done':'Manage'}</button></div>
    {!mdblistConfigured&&mappedCount>0&&<div className="setupNotice"><small>{mappedCount} catalogs have verified MDBList mappings. They automatically use AstraWave metadata fallback until the server MDBList connection is configured.</small></div>}
    <div className="sourceChips">{categories.map(c=><button key={c} className={category===c?'active':''} onClick={()=>setCategory(c)}>{c}</button>)}</div>
    {category==='All'?<div className="builtinCatalogSections">{sections.map(section=><section key={section.name} className="builtinCatalogSection"><div className="row-head"><div><h3>{section.name}</h3><small>{sectionSubtitle(section.name,kind)} • {section.items.length} catalogs</small></div></div>{preview(section.name)}<div className="builtinCatalogDirectory">{section.items.map(card)}</div></section>)}</div>:<><div className="catalogPreviewFiltered">{visible.length?preview(sectionFor(visible[0],kind)):null}</div><div className="builtinCatalogDirectory">{visible.map(card)}</div></>}
    {manage&&prefs.hidden.length>0&&<div className="setupNotice"><b>{prefs.hidden.length} hidden catalogs</b><button className="ghostBtn" onClick={reset}>Reset catalog layout</button></div>}
    {selected&&<div className="builtinCatalogResults"><div className="row-head"><div><h3>{selected.title}</h3><small>{source}{items.length?` • ${items.length} titles`:''}</small></div></div>{listLoading?<div className="setupNotice"><p>Refreshing catalog…</p></div>:items.length?<div className="card-strip">{items.map(item=><Link className="card-link" key={`${item.kind}:${item.id}`} href={`/app/title/${item.kind}/${item.id}`}><article className="aw-card"><div className="aw-poster" style={item.posterUrl?{backgroundImage:`linear-gradient(to top,rgba(5,7,12,.92),rgba(5,7,12,.05)),url(${item.posterUrl})`}:undefined}>{!item.posterUrl&&<span>{item.title.slice(0,1)}</span>}</div><strong>{item.title}</strong><small>{item.subtitle||selected.title}</small></article></Link>)}</div>:<div className="setupNotice"><p>No items are available in this catalog right now.</p></div>}</div>}
  </section>;
}
