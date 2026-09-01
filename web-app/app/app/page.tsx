'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { CalendarDays, Compass, Film, Home, Library, RadioTower, Music2, Search, Tv2, Trophy, UserCircle2 } from 'lucide-react';
import { AstraWaveApi, CatalogItem } from '../../lib/astrawave-api';

const nav = [
  ['Home',Home],['Movies',Film],['TV Shows',Tv2],['Live TV',RadioTower],['Guide',CalendarDays],['Sports',Trophy],['Music & Podcasts',Music2],['Discover',Compass],['Search',Search],['My AstraWave',UserCircle2]
] as const;

export default function WebAppHome(){
  const [rows,setRows] = useState<{ title: string; items: CatalogItem[] }[]>([]);
  const [loading,setLoading] = useState(true);
  const [notice,setNotice] = useState('Loading real AstraWave catalog data...');
  const [playing,setPlaying] = useState<CatalogItem | null>(null);

  useEffect(()=>{
    AstraWaveApi.home()
      .then((data)=>{
        setRows(data.rows);
        setNotice(data.rows.length ? '' : 'No real catalog data is configured yet. Add TMDB_BEARER_TOKEN for accurate movies and TV, and ASTRAWAVE_LIVE_M3U_URL for playable authorized live TV.');
      })
      .catch(()=>setNotice('AstraWave could not load configured providers. Check TMDB_BEARER_TOKEN and ASTRAWAVE_LIVE_M3U_URL.'))
      .finally(()=>setLoading(false));
  },[]);

  return <main className="appShell">
    <aside className="appSide"><Link href="/" className="brand"><span>AW</span>AstraWave</Link><div className="appNav">{nav.map(([label,Icon],i)=><button className={i===0?'appNavItem active':'appNavItem'} key={label}><Icon size={19}/><span>{label}</span></button>)}</div><div className="profile"><div className="avatar">A</div><div><b>My Profile</b><span>Free plan</span></div></div></aside>
    <section className="appMain"><header className="appTop"><div><small>ASTRAWAVE WEB</small><h1>What are you watching?</h1></div><div className="topActions"><button className="searchBox"><Search size={17}/> Search AstraWave</button><button className="round"><Library size={19}/></button></div></header>
      <section className="appHero"><div className="heroShade"><span className="pillTag">ALL YOUR ENTERTAINMENT</span><h2>Everything you love,<br/>ready when you are.</h2><p>Movies, TV, live channels, sports, music and podcasts across your enabled sources.</p><div><button className="primaryBtn">▶ Watch Now</button><button className="ghostBtn">+ My List</button></div></div></section>
      {loading && <section className="contentRow"><div className="rowHead"><h3>Loading AstraWave…</h3></div></section>}
      {notice && <section className="contentRow"><div className="setupNotice"><h3>Provider setup needed</h3><p>{notice}</p></div></section>}
      {playing?.streamUrl && <section className="contentRow playerPanel"><div className="rowHead"><h3>Now Playing: {playing.title}</h3><button onClick={()=>setPlaying(null)}>Close</button></div><video src={playing.streamUrl} controls playsInline autoPlay /></section>}
      {rows.map((row)=><section className="contentRow" key={row.title}><div className="rowHead"><h3>{row.title}</h3><button>See all</button></div><div className="appCards">{row.items.map((item)=>{
        const card=<article className="appCard"><div className="appPoster" style={item.posterUrl?{backgroundImage:`linear-gradient(to top,rgba(8,10,15,.9),rgba(8,10,15,.05)),url(${item.posterUrl})`,backgroundSize:'cover',backgroundPosition:'center'}:undefined}><span>{item.posterUrl?'':item.title.charAt(0)}</span>{item.kind==='live'&&<b className="liveBadge">LIVE</b>}{item.kind==='sport'&&<b className="sportBadge">TODAY</b>}</div><strong>{item.title}</strong><small>{item.subtitle || (item.kind==='sport'?'View event':'AstraWave')}</small></article>;
        if (item.kind === 'live') return <button className="cardButton" key={`${item.kind}-${item.id}`} onClick={()=> item.streamUrl ? setPlaying(item) : undefined}>{card}</button>;
        return (item.kind==='movie'||item.kind==='series')?<Link key={`${item.kind}-${item.id}`} href={`/app/title/${item.kind}/${item.id}`} style={{textDecoration:'none',color:'inherit'}}>{card}</Link>:<div key={`${item.kind}-${item.id}`}>{card}</div>;
      })}</div></section>)}
    </section>
  </main>
}
