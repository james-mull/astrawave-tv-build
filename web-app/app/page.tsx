import Link from 'next/link';
import { ArrowRight, Check, MonitorPlay, Radio, ShieldCheck, Sparkles, Tv, Trophy } from 'lucide-react';

const features = [
  ['Movies & TV','One beautiful catalog with source-aware playback and watchlists.'],
  ['Live TV + Guide','A clean merged guide for AstraWave Free and your M3U/Xtream sources.'],
  ['Sports Hub','See what is live, starting soon and where authorized broadcasts are available.'],
  ['Music & Podcasts','Continue listening across music, podcasts, video podcasts and radio.'],
  ['Smart Sources','Health checks, quality ranking, deduplication and automatic fallback.'],
  ['Every Screen','Android TV, Fire TV, Android mobile and the AstraWave web experience.']
];

export default function MarketingHome() {
  return <main>
    <nav className="nav shell">
      <Link href="/" className="brand"><span>AW</span>AstraWave</Link>
      <div className="navLinks"><a href="#features">Features</a><a href="#pricing">Pricing</a><a href="#faq">FAQ</a></div>
      <div className="navActions"><Link className="ghostBtn" href="/app">Open Web App</Link><a className="primaryBtn" href="#download">Get AstraWave</a></div>
    </nav>

    <section className="hero shell">
      <div className="eyebrow"><Sparkles size={16}/> Your entertainment, finally together</div>
      <h1>Everything you watch.<br/><span>One beautiful home.</span></h1>
      <p className="heroCopy">Movies, TV shows, live channels, sports, music, podcasts and your own media sources—organized into one fast, premium experience.</p>
      <div className="heroActions"><Link className="primaryBtn big" href="/app">Launch Web App <ArrowRight size={18}/></Link><a className="secondaryBtn big" href="#download">Get Android App</a></div>
      <div className="trustRow"><span><Check/> Free to start</span><span><Check/> Android TV + Fire TV</span><span><Check/> M3U & Xtream ready</span><span><Check/> No cable-style clutter</span></div>

      <div className="productFrame">
        <aside><div className="miniBrand">AW</div>{['Home','Movies','TV Shows','Live TV','Guide','Sports','Music','Discover'].map((x,i)=><div key={x} className={i===0?'side active':'side'}>{x}</div>)}</aside>
        <div className="mockContent"><div className="mockHero"><div><small>ASTRAWAVE ORIGINAL EXPERIENCE</small><h2>What do you want to watch tonight?</h2><p>Jump back in, catch the biggest game, or discover something new.</p><button>▶ Play Featured</button></div></div><h3>Continue Watching</h3><div className="cards">{['Night Drive','Northbound','Signal','Afterlight'].map(x=><div className="poster" key={x}><div>{x[0]}</div><b>{x}</b></div>)}</div></div>
      </div>
    </section>

    <section className="proofStrip"><div className="shell proofGrid"><div><Tv/><strong>Live TV</strong><span>Smart merged sources</span></div><div><MonitorPlay/><strong>Movies & TV</strong><span>Source-aware playback</span></div><div><Trophy/><strong>Sports</strong><span>Event-first discovery</span></div><div><Radio/><strong>Audio</strong><span>Music, podcasts & radio</span></div></div></section>

    <section id="features" className="section shell"><div className="sectionHead"><span>ONE APP. LESS FRICTION.</span><h2>Built around what you actually want to watch.</h2><p>AstraWave removes the jumping between apps, playlists and clunky interfaces.</p></div><div className="featureGrid">{features.map(([title,copy],i)=><article key={title}><div className="featureIcon">{i+1}</div><h3>{title}</h3><p>{copy}</p></article>)}</div></section>

    <section className="section split shell"><div><span className="kicker">BRING YOUR OWN SOURCES</span><h2>Your subscriptions and media stay yours.</h2><p>Connect supported personal libraries, M3U playlists, Xtream providers and compatible extensions. AstraWave organizes enabled sources into one interface and chooses the best healthy playback candidate where available.</p><ul className="checkList">{['M3U + XMLTV','Xtream Codes','Plex / Jellyfin / Emby','Supported debrid/cloud accounts','Podcasts and radio feeds','AstraWave authorized free media'].map(x=><li key={x}><Check/>{x}</li>)}</ul></div><div className="sourcePanel"><div className="sourceTop"><ShieldCheck/> Source Health</div>{[['AstraWave Free','Healthy'],['My M3U','Connected'],['Xtream Codes','Ready'],['Personal Media','Available']].map(([a,b])=><div className="sourceRow" key={a}><span>{a}</span><b>{b}</b></div>)}</div></section>

    <section id="pricing" className="section shell"><div className="sectionHead"><span>SIMPLE PRICING</span><h2>Start free. Upgrade for the power features.</h2></div><div className="pricingGrid"><article className="priceCard"><div className="plan">AstraWave Free</div><div className="price">$0</div><p>Everything needed to experience AstraWave.</p>{['Core entertainment UI','Free/authorized media','Basic live TV + guide','One M3U/Xtream source','Watchlist + continue watching','Web + Android access'].map(x=><div className="priceFeature" key={x}><Check/>{x}</div>)}<Link href="/app" className="secondaryBtn full">Start Free</Link></article><article className="priceCard featured"><div className="popular">BEST VALUE</div><div className="plan">AstraWave+</div><div className="price">$4.99 <small>/ month</small></div><p>For households and entertainment power users.</p>{['Unlimited playlists','Smart merged TV guide','Cloud sync + multiple profiles','Advanced source health/failover','Sports multiview roadmap','Advanced recommendations + AI'].map(x=><div className="priceFeature" key={x}><Check/>{x}</div>)}<a href="#download" className="primaryBtn full">Get AstraWave+</a></article></div></section>

    <section id="download" className="cta shell"><div><span>WATCH ANYWHERE</span><h2>Start on the web. Take AstraWave to your TV.</h2><p>Use AstraWave in your browser, then install the Android app on Android TV, Fire TV, phone or tablet.</p></div><div className="ctaButtons"><Link href="/app" className="primaryBtn big">Open Web App</Link><button className="secondaryBtn big">Android APK Coming Next</button></div></section>

    <section id="faq" className="section shell"><div className="sectionHead"><span>FAQ</span><h2>Know what you’re getting.</h2></div><div className="faqGrid">{[
      ['Does AstraWave include movies and live TV?','AstraWave includes its own authorized/free catalog and can combine supported user-connected media sources into one interface.'],
      ['Can I use M3U or Xtream?','Yes. The Android platform is being built around both M3U/XMLTV and Xtream Codes, including combined-channel and guide modes.'],
      ['Is there a web version?','Yes. The AstraWave web app is a first-class client alongside Android and Android TV. Browser playback depends on the source format and provider permissions.'],
      ['What does AstraWave+ pay for?','Cloud sync, advanced guide/source management, multiple profiles, advanced sports and other premium software features—not unauthorized content.']
    ].map(([q,a])=><article key={q}><h3>{q}</h3><p>{a}</p></article>)}</div></section>

    <footer className="footer shell"><div className="brand"><span>AW</span>AstraWave</div><p>All your entertainment. One place.</p><div>© 2026 AstraWave</div></footer>
  </main>
}
