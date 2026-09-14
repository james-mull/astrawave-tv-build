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

export default function BuiltInCatalogBrowser({kind}:{kind:Kind}){
  const [definitions,setDefinitions]=useState<Def[]>([]);
  const [mappedIds,setMappedIds]=useState<Set<string>>(new Set());
  const [mdblistConfigured,setMdblistConfigured]=useState(false);
  const [prefs,setPrefs]=useState<Prefs>(emptyPrefs);
  const [category,setCategory]=useState('All');
  const [selected,setSelected]=useState<Def|null>(null);
  const [items,setItems]=useState<Item[]>([]);
  const [source,setSource]=useState('');
  const [loading,setLoading]=useState(true);
  const [listLoading,setListLoading]=useState(false);
  const [manage,setManage]=useState(false);

  useEffect(()=>{
    setPrefs(loadPrefs(kind));setLoading(true);
    fetch(`/api/astrawave/builtin-catalogs?kind=${kind}`,{cache:'no-store'})
      .then(r=>r.json())
      .then(data=>{
        setDefinitions(data.definitions||[]);
        setMappedIds(new Set<string>(data.mdblistMappedIds||[]));
        setMdblistConfigured(Boolean(data.mdblistConfigured));
      })
      .finally(()=>setLoading(false));
  },[kind]);

  const ordered=useMemo(()=>{
    const index=new Map(prefs.order.map((id,i)=>[id,i]));
    return definitions.filter(d=>!prefs.hidden.includes(d.id)).sort((a,b)=>(index.get(a.id)??9999)-(index.get(b.id)??9999)||Number(Boolean(b.featured))-Number(Boolean(a.featured))||a.title.localeCompare(b.title));
  },[definitions,prefs]);
  const categories=useMemo(()=>['All',...Array.from(new Set(definitions.map(d=>d.category)))],[definitions]);
  const visible=category==='All'?ordered:ordered.filter(d=>d.category===category);

  function update(next:Prefs){setPrefs(next);savePrefs(kind,next)}
  function togglePinned(id:string){update({...prefs,pinned:prefs.pinned.includes(id)?prefs.pinned.filter(x=>x!==id):[...prefs.pinned,id]})}
  function hide(id:string){update({...prefs,hidden:[...new Set([...prefs.hidden,id])]})}
  function move(id:string,delta:number){const base=ordered.map(x=>x.id);const from=base.indexOf(id);if(from<0)return;const to=Math.max(0,Math.min(base.length-1,from+delta));base.splice(to,0,base.splice(from,1)[0]);update({...prefs,order:base})}
  function reset(){update(emptyPrefs)}
  async function open(def:Def){setSelected(def);setItems([]);setSource('');setListLoading(true);try{const data=await fetch(`/api/astrawave/builtin-catalogs?id=${encodeURIComponent(def.id)}`,{cache:'no-store'}).then(r=>r.json());setItems(data.items||[]);setSource(data.source||'AstraWave catalog')}finally{setListLoading(false)}}

  if(loading)return <div className="setupNotice"><p>Loading {kind==='movie'?'75 movie':'75 TV'} catalogs…</p></div>;
  const mappedCount=definitions.filter(d=>mappedIds.has(d.id)).length;
  return <section className="builtinCatalogBrowser">
    <div className="row-head"><div><h3>{kind==='movie'?'Movie Catalogs':'TV Show Catalogs'}</h3><small>{ordered.length} enabled • {definitions.length} total • {mappedCount} MDBList mapped</small></div><button className="ghostBtn" onClick={()=>setManage(v=>!v)}>{manage?'Done':'Manage'}</button></div>
    {!mdblistConfigured&&mappedCount>0&&<div className="setupNotice"><small>{mappedCount} catalogs have verified MDBList mappings. They automatically use AstraWave metadata fallback until the server MDBList connection is configured.</small></div>}
    <div className="sourceChips">{categories.map(c=><button key={c} className={category===c?'active':''} onClick={()=>setCategory(c)}>{c}</button>)}</div>
    <div className="builtinCatalogDirectory">{visible.map(def=><article key={def.id} className={selected?.id===def.id?'active':''}>
      <button className="catalogOpen" onClick={()=>open(def)}><b>{def.title}</b><small>{def.category}{def.featured?' • Featured':''}{def.service?' • Service':''}{mappedIds.has(def.id)?' • MDBList':''}</small></button>
      {manage&&<div className="catalogManage"><button onClick={()=>togglePinned(def.id)}>{prefs.pinned.includes(def.id)?'Unpin':'Pin'}</button><button onClick={()=>move(def.id,-1)}>↑</button><button onClick={()=>move(def.id,1)}>↓</button><button onClick={()=>hide(def.id)}>Hide</button></div>}
    </article>)}</div>
    {manage&&prefs.hidden.length>0&&<div className="setupNotice"><b>{prefs.hidden.length} hidden catalogs</b><button className="ghostBtn" onClick={reset}>Reset catalog layout</button></div>}
    {selected&&<div className="builtinCatalogResults"><div className="row-head"><div><h3>{selected.title}</h3><small>{source}{items.length?` • ${items.length} titles`:''}</small></div></div>{listLoading?<div className="setupNotice"><p>Refreshing catalog…</p></div>:items.length?<div className="card-strip">{items.map(item=><Link className="card-link" key={`${item.kind}:${item.id}`} href={`/app/title/${item.kind}/${item.id}`}><article className="aw-card"><div className="aw-poster" style={item.posterUrl?{backgroundImage:`linear-gradient(to top,rgba(5,7,12,.92),rgba(5,7,12,.05)),url(${item.posterUrl})`}:undefined}>{!item.posterUrl&&<span>{item.title.slice(0,1)}</span>}</div><strong>{item.title}</strong><small>{item.subtitle||selected.title}</small></article></Link>)}</div>:<div className="setupNotice"><p>No items are available in this catalog right now.</p></div>}</div>}
  </section>;
}
