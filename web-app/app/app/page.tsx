import Link from 'next/link';
import { CalendarDays, Compass, Film, Home, Library, LiveTv, Music2, Search, Tv2, Trophy, UserCircle2 } from 'lucide-react';

const nav = [
  ['Home',Home],['Movies',Film],['TV Shows',Tv2],['Live TV',LiveTv],['Guide',CalendarDays],['Sports',Trophy],['Music & Podcasts',Music2],['Discover',Compass],['Search',Search],['My AstraWave',UserCircle2]
] as const;
const rows = [
  ['Continue Watching',['Night Drive','Northbound','Signal','Afterlight']],
  ['Sports Starting Soon',['Broncos vs Chiefs','Nuggets vs Lakers','Dodgers vs Padres','Avalanche vs Stars']],
  ['Live Now',['Local News','Weather Live','Music Live','World News']],
  ['Trending Movies',['Orbit','The Last Ridge','Echo City','Beyond Midnight']],
  ['New Episodes',['Frontier S2:E4','Signal S1:E8','Deep Water S3:E2','Afterlight S1:E5']],
  ['Continue Listening',['Daily Brief','Road Mix','Tech Weekly','True Crime Daily']]
];

export default function WebAppHome(){
  return <main className="appShell">
    <aside className="appSide"><Link href="/" className="brand"><span>AW</span>AstraWave</Link><div className="appNav">{nav.map(([label,Icon],i)=><button className={i===0?'appNavItem active':'appNavItem'} key={label}><Icon size={19}/><span>{label}</span></button>)}</div><div className="profile"><div className="avatar">J</div><div><b>My Profile</b><span>Free plan</span></div></div></aside>
    <section className="appMain"><header className="appTop"><div><small>GOOD EVENING</small><h1>What are you watching?</h1></div><div className="topActions"><button className="searchBox"><Search size={17}/> Search AstraWave</button><button className="round"><Library size={19}/></button></div></header>
      <section className="appHero"><div className="heroShade"><span className="pillTag">FEATURED TONIGHT</span><h2>Everything you love,<br/>ready when you are.</h2><p>Pick up where you left off, see what’s live, or let AstraWave find something great.</p><div><button className="primaryBtn">▶ Play Featured</button><button className="ghostBtn">+ My List</button></div></div></section>
      {rows.map(([title,items])=><section className="contentRow" key={title as string}><div className="rowHead"><h3>{title as string}</h3><button>See all</button></div><div className="appCards">{(items as string[]).map((item,index)=><article className="appCard" key={item}><div className="appPoster"><span>{item.charAt(0)}</span>{title==='Live Now'&&<b className="liveBadge">LIVE</b>}{title==='Sports Starting Soon'&&<b className="sportBadge">TODAY</b>}</div><strong>{item}</strong><small>{title==='Sports Starting Soon'?'View watch options':'AstraWave'}</small></article>)}</div></section>)}
    </section>
  </main>
}
