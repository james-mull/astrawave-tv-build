import Link from 'next/link';
import { ArrowRight, Check, Cloud, MonitorPlay, Radio, ShieldCheck, Sparkles, Tv, Trophy } from 'lucide-react';

const features = [
  ['Movies & TV','Deep TMDB catalogs, reviewed addon rows, title context, watchlists and source-aware playback.'],
  ['Live TV + Guide','Merged public/customer-authorized sources with source switching and AstraWave EPG now/next data.'],
  ['Sports Hub','A three-day event slate with broadcaster matching, sports channels and multiview-ready workflows.'],
  ['Music & Podcasts','Public radio, podcasts, music and video-podcast discovery in a dedicated listening experience.'],
  ['Web Control Center','Manage addons, repositories, IPTV sources and preferences once, then sync them to signed-in TV devices.'],
  ['Smart Sources','Health checks, quality ranking, deduplication, fallback and rights-aware source eligibility.']
];

export default function MarketingHome() {
  return <main>
    <nav className="nav shell">
      <Link href="/" className="brand"><span>AW</span>AstraWave</Link>
      <div className="navLinks"><a href="#features">Features</a><a href="#pricing">Pricing</a><a href="#faq">FAQ</a></div>
      <div className="navActions"><Link className="ghostBtn" href="/app/control">Control Center</Link><Link className="primaryBtn" href="/app">Open AstraWave</Link></div>
    </nav>

    <section className="hero shell">
      <div className="eyebrow"><Sparkles size={16}/> Your entertainment, finally together</div>
      <h1>Everything you watch.<br/><span>One premium home.</span></h1>
      <p className="heroCopy">Movies, TV shows, live channels, sports, music, podcasts and your own authorized media sources—organized into one fast experience that follows you from web to TV.</p>
      <div className="heroActions"><Link className="primaryBtn big" href="/app">Launch Web App <ArrowRight size={18}/></Link><Link className="secondaryBtn big" href="/app/control"><Cloud size={17}/> Configure Your TV</Link></div>
      <div className="trustRow"><span><Check/> TMDB catalogs</span><span><Check/> Android TV + Fire TV</span><span><Check/> M3U, XMLTV & Xtream</span><span><Check/> Addon/repo cloud sync</span></div>

      <div className="productFrame">
        <aside><div className="miniBrand">AW</div>{['Home','Movies','TV Shows','Live TV','Guide','Sports','Music','Discover','Sources'].map((x,i)=><div key={x} className={i===0?'side active':'side'}>{x}</div>)}</aside>
        <div className="mockContent"><div className="mockHero"><div><small>ASTRAWAVE PREMIUM EXPERIENCE</small><h2>What do you want to watch tonight?</h2><p>Jump back in, catch the biggest game, switch live sources or discover something new.</p><button>▶ Open AstraWave</button></div></div><h3>One account. Every screen.</h3><div className="cards">{['Continue','Live Guide','Sports','Discover'].map(x=><div className="poster" key={x}><div>{x[0]}</div><b>{x}</b></div>)}</div></div>
      </div>
    </section>

    <section className="proofStrip"><div className="shell proofGrid"><div><Tv/><strong>Live TV</strong><span>Merged source fabric</span></div><div><MonitorPlay/><strong>Movies & TV</strong><span>Deep catalog discovery</span></div><div><Trophy/><strong>Sports</strong><span>Event-first viewing</span></div><div><Cloud/><strong>Cloud Control</strong><span>Web → TV sync</span></div></div></section>

    <section id="features" className="section shell"><div className="sectionHead"><span>ONE APP. LESS FRICTION.</span><h2>Built like a real entertainment platform.</h2><p>AstraWave combines discovery, playback, live TV, sports and user-managed integrations without forcing configuration clutter onto every page.</p></div><div className="featureGrid">{features.map(([title,copy],i)=><article key={title}><div className="featureIcon">{i+1}</div><h3>{title}</h3><p>{copy}</p></article>)}</div></section>

    <section className="section split shell"><div><span className="kicker">CONFIGURE ONCE</span><h2>Your TV setup can start on the web.</h2><p>Use the AstraWave Control Center to add compatible Stremio manifests, CloudStream repository URLs, customer-authorized M3U/XMLTV sources and experience preferences. Save them to your AstraWave account and signed-in TV devices restore the same configuration.</p><ul className="checkList">{['Custom Stremio manifests','CloudStream repo registry','M3U + XMLTV','Xtream on Android','Profiles & preferences','Cloud-synced configuration'].map(x=><li key={x}><Check/>{x}</li>)}</ul><Link href="/app/control" className="primaryBtn">Open Control Center <ArrowRight size={16}/></Link></div><div className="sourcePanel"><div className="sourceTop"><ShieldCheck/> Source & Sync Health</div>{[['TMDB + reviewed catalogs','Ready'],['AstraWave EPG','Connected'],['Web → TV config','Cloud Sync'],['Customer sources','User controlled']].map(([a,b])=><div className="sourceRow" key={a}><span>{a}</span><b>{b}</b></div>)}</div></section>

    <section id="pricing" className="section shell"><div className="sectionHead"><span>PREMIUM PRODUCT</span><h2>Software worth paying for every month.</h2><p>The paid plan is for the platform features, synchronization, advanced discovery and source-management experience—not unauthorized content.</p></div><div className="pricingGrid"><article className="priceCard"><div className="plan">AstraWave Free</div><div className="price">$0</div><p>Try the core experience.</p>{['Core entertainment UI','Authorized/free media','Basic live TV + guide','One user-managed source','Watchlist + progress','Web access'].map(x=><div className="priceFeature" key={x}><Check/>{x}</div>)}<Link href="/app" className="secondaryBtn full">Start Free</Link></article><article className="priceCard featured"><div className="popular">PREMIUM</div><div className="plan">AstraWave+</div><div className="price">$19.99 <small>/ month</small></div><p>The complete multi-device entertainment control layer.</p>{['Unlimited source profiles','Web Control Center → TV sync','Advanced EPG + source failover','Multiple profiles + cloud library','Expanded sports + multiview','AI-assisted discovery & customization'].map(x=><div className="priceFeature" key={x}><Check/>{x}</div>)}<Link href="/app/control" className="primaryBtn full">Configure AstraWave+</Link></article></div></section>

    <section id="download" className="cta shell"><div><span>WATCH ANYWHERE</span><h2>Set it up on the web. Enjoy it on the TV.</h2><p>AstraWave’s browser experience doubles as a configuration surface for the Android TV/Fire TV app, keeping supported preferences and sources aligned across devices.</p></div><div className="ctaButtons"><Link href="/app" className="primaryBtn big">Open Web App</Link><Link href="/app/control" className="secondaryBtn big">Open Control Center</Link></div></section>

    <section id="faq" className="section shell"><div className="sectionHead"><span>FAQ</span><h2>Know what you’re getting.</h2></div><div className="faqGrid">{[
      ['Does AstraWave include movies and live TV?','AstraWave provides metadata/discovery plus authorized/free media integrations and combines supported user-connected sources into one interface.'],
      ['Can I configure my TV from the web?','Yes. Sign into the same AstraWave account in the web Control Center and TV app. Supported addons, repository choices, IPTV sources and experience preferences are stored in your private cloud configuration and restored on TV.'],
      ['Can I use M3U, XMLTV or Xtream?','Yes. Web can save M3U/XMLTV source profiles; Android supports M3U/XMLTV and Xtream source models. Only use providers you are authorized to access.'],
      ['What does AstraWave+ pay for?','The software platform: cloud sync, advanced guide/source management, profiles, sports, discovery, personalization and premium UX—not unauthorized content.']
    ].map(([q,a])=><article key={q}><h3>{q}</h3><p>{a}</p></article>)}</div></section>

    <footer className="footer shell"><div className="brand"><span>AW</span>AstraWave</div><p>All your entertainment. One place.</p><div>© 2026 AstraWave</div></footer>
  </main>
}