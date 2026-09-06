'use client';

import Link from 'next/link';
import { FormEvent, useEffect, useMemo, useState } from 'react';
import { onAuthStateChanged, User } from 'firebase/auth';
import { Cloud, CloudOff, Link2, Plus, RadioTower, Save, ShieldCheck, SlidersHorizontal, Tv2, X } from 'lucide-react';
import { firebaseAuth } from '../../../lib/firebase';
import { FirebaseAuthService } from '../../../lib/firebase-auth';
import { CloudAddon, CloudAppConfig, CloudSource, FirebaseData } from '../../../lib/firebase-data';
import { AstraWaveApi, SourceRegistry } from '../../../lib/astrawave-api';

const defaultConfig: CloudAppConfig = {
  version: 1,
  activeLiveSource: 'all-free',
  theme: 'dark',
  homeDensity: 'comfortable',
  autoplayTrailers: false,
  aiDiscovery: true,
  preferredLanguage: 'en-US',
  preferredRegion: 'US',
  addons: [],
};

export default function AstraWaveControlCenter(){
  const [user,setUser]=useState<User|null>(null);
  const [registry,setRegistry]=useState<SourceRegistry|null>(null);
  const [config,setConfig]=useState<CloudAppConfig>(defaultConfig);
  const [sources,setSources]=useState<CloudSource[]>([]);
  const [email,setEmail]=useState('');
  const [password,setPassword]=useState('');
  const [message,setMessage]=useState('');
  const [addonUrl,setAddonUrl]=useState('');
  const [repoUrl,setRepoUrl]=useState('');
  const [m3uName,setM3uName]=useState('My IPTV');
  const [m3uUrl,setM3uUrl]=useState('');
  const [xmltvUrl,setXmltvUrl]=useState('');
  const [busy,setBusy]=useState(false);

  useEffect(()=>{
    AstraWaveApi.sourceRegistry().then(setRegistry).catch(()=>setRegistry(null));
    if(!firebaseAuth) return;
    return onAuthStateChanged(firebaseAuth, async next=>{
      setUser(next);
      if(!next){setConfig(defaultConfig);setSources([]);return}
      const [cloudConfig,cloudSources]=await Promise.all([FirebaseData.getAppConfig(next.uid),FirebaseData.listSources(next.uid)]);
      setConfig(cloudConfig?{...defaultConfig,...cloudConfig}:defaultConfig);
      setSources(cloudSources);
    });
  },[]);

  useEffect(()=>{
    if(!registry) return;
    setConfig(current=>{
      if(current.addons?.length) return current;
      const addons:CloudAddon[]=[...registry.stremio,...registry.cloudstream,...registry.providerCatalogs].map(x=>({id:x.id,name:x.name,kind:x.kind as CloudAddon['kind'],url:x.url,enabled:x.enabledByDefault,reviewed:x.reviewed,custom:false}));
      return {...current,addons};
    });
  },[registry]);

  const enabledCount=useMemo(()=>config.addons?.filter(x=>x.enabled).length||0,[config.addons]);

  async function authSubmit(e:FormEvent){
    e.preventDefault();setBusy(true);setMessage('');
    try{await FirebaseAuthService.signInWithEmail(email,password);setMessage('Signed in. Your cloud configuration is connected.')}catch(error){setMessage(error instanceof Error?error.message:'Sign in failed')}
    finally{setBusy(false)}
  }

  function toggleAddon(id:string){setConfig(c=>({...c,addons:(c.addons||[]).map(x=>x.id===id?{...x,enabled:!x.enabled}:x)}))}
  function addCustom(kind:'stremio'|'cloudstream',url:string){
    const clean=url.trim();if(!clean)return;
    const id=`custom-${kind}-${Math.abs(hash(clean))}`;
    const name=kind==='stremio'?'Custom Stremio Addon':'Custom CloudStream Repo';
    setConfig(c=>({...c,addons:[...(c.addons||[]).filter(x=>x.id!==id),{id,name,kind,url:clean,enabled:true,reviewed:false,custom:true}]}));
    if(kind==='stremio')setAddonUrl('');else setRepoUrl('');
  }

  function removeAddon(id:string){setConfig(c=>({...c,addons:(c.addons||[]).filter(x=>x.id!==id)}))}

  function addSource(){
    if(!m3uUrl.trim()) return;
    const source:CloudSource={id:`source-${Date.now()}`,name:m3uName.trim()||'My IPTV',type:'M3U',enabled:true,priority:20,config:{m3uUrl:m3uUrl.trim(),xmlTvUrl:xmltvUrl.trim()||null}};
    setSources(s=>[...s,source]);setM3uUrl('');setXmltvUrl('');
  }

  async function saveAll(){
    if(!user){setMessage('Sign in first so your TV app can receive this configuration.');return}
    setBusy(true);setMessage('');
    try{
      await FirebaseData.saveAppConfig(user.uid,{...config,version:(config.version||0)+1});
      await Promise.all(sources.map(source=>FirebaseData.saveSource(user.uid,source)));
      setConfig(c=>({...c,version:(c.version||0)+1}));
      setMessage('Saved to AstraWave Cloud. Signed-in TV devices will pull this configuration.');
    }catch(error){setMessage(error instanceof Error?error.message:'Could not save configuration')}
    finally{setBusy(false)}
  }

  return <main className="control-shell"><header><Link href="/app">← Back to AstraWave</Link><div><small>ASTRAWAVE WEB CONTROL CENTER</small><h1>Configure once. Watch everywhere.</h1><p>Add sources, addons, repos and experience preferences here, then sync them to your signed-in TV app.</p></div><div className={user?'cloud on':'cloud'}>{user?<Cloud/>:<CloudOff/>}<span>{user?'Cloud connected':'Not signed in'}</span></div></header>

    {!user&&<section className="panel auth"><div><h2>Sign in to sync devices</h2><p>Use the same AstraWave account on web and TV. Configuration remains private to your Firebase user.</p></div><form onSubmit={authSubmit}><input type="email" placeholder="Email" value={email} onChange={e=>setEmail(e.target.value)} required/><input type="password" placeholder="Password" value={password} onChange={e=>setPassword(e.target.value)} required/><button disabled={busy}>Sign in</button></form></section>}

    <section className="dashboard"><article><Tv2/><b>{sources.length}</b><span>custom live sources</span></article><article><Link2/><b>{enabledCount}</b><span>enabled addons/repos</span></article><article><Cloud/><b>v{config.version||1}</b><span>cloud config version</span></article><article><ShieldCheck/><b>Private</b><span>user-scoped Firestore</span></article></section>

    <section className="panel"><div className="head"><div><small>LIVE TV</small><h2>Sources & Guide</h2><p>Add a customer-authorized M3U and optional XMLTV guide. Public built-in sources remain available in the app.</p></div></div><div className="form-grid"><input value={m3uName} onChange={e=>setM3uName(e.target.value)} placeholder="Source name"/><input value={m3uUrl} onChange={e=>setM3uUrl(e.target.value)} placeholder="M3U URL"/><input value={xmltvUrl} onChange={e=>setXmltvUrl(e.target.value)} placeholder="XMLTV URL (optional)"/><button onClick={addSource}><Plus size={16}/> Add source</button></div><div className="rows">{sources.map((source,i)=><div key={source.id}><RadioTower/><div><b>{source.name}</b><small>{String(source.config?.m3uUrl||'')}</small></div><label><input type="checkbox" checked={source.enabled} onChange={()=>setSources(s=>s.map((x,j)=>j===i?{...x,enabled:!x.enabled}:x))}/> Enabled</label><button onClick={()=>setSources(s=>s.filter(x=>x.id!==source.id))}><X/></button></div>)}</div></section>

    <section className="panel"><div className="head"><div><small>ADDONS</small><h2>Stremio-compatible catalogs</h2><p>Add a manifest URL or enable/disable reviewed defaults. Catalog metadata can sync across devices; playback remains subject to source authorization.</p></div></div><div className="add-line"><input value={addonUrl} onChange={e=>setAddonUrl(e.target.value)} placeholder="https://.../manifest.json"/><button onClick={()=>addCustom('stremio',addonUrl)}><Plus size={16}/> Add addon</button></div><AddonRows kind="stremio" addons={config.addons||[]} toggle={toggleAddon} remove={removeAddon}/></section>

    <section className="panel"><div className="head"><div><small>REPOSITORIES</small><h2>CloudStream repo registry</h2><p>Reviewed and custom repo URLs can be synchronized. Community extensions remain opt-in and are not automatically trusted for playback.</p></div></div><div className="add-line"><input value={repoUrl} onChange={e=>setRepoUrl(e.target.value)} placeholder="https://.../repo.json"/><button onClick={()=>addCustom('cloudstream',repoUrl)}><Plus size={16}/> Add repo</button></div><AddonRows kind="cloudstream" addons={config.addons||[]} toggle={toggleAddon} remove={removeAddon}/></section>

    <section className="panel"><div className="head"><div><small>CUSTOMIZATION</small><h2>Experience settings</h2></div></div><div className="settings-grid"><label>Home density<select value={config.homeDensity} onChange={e=>setConfig(c=>({...c,homeDensity:e.target.value as CloudAppConfig['homeDensity']}))}><option value="comfortable">Comfortable</option><option value="compact">Compact</option></select></label><label>Region<input value={config.preferredRegion||'US'} onChange={e=>setConfig(c=>({...c,preferredRegion:e.target.value.toUpperCase()}))}/></label><label>Language<input value={config.preferredLanguage||'en-US'} onChange={e=>setConfig(c=>({...c,preferredLanguage:e.target.value}))}/></label><label className="toggle"><input type="checkbox" checked={config.aiDiscovery!==false} onChange={e=>setConfig(c=>({...c,aiDiscovery:e.target.checked}))}/> AI-assisted discovery</label><label className="toggle"><input type="checkbox" checked={config.autoplayTrailers===true} onChange={e=>setConfig(c=>({...c,autoplayTrailers:e.target.checked}))}/> Autoplay trailers</label></div></section>

    <section className="syncbar"><div><Cloud/><div><b>Push configuration to your devices</b><span>{user?`Signed in as ${user.email||user.uid}`:'Sign in to enable sync'}</span></div></div><button onClick={saveAll} disabled={busy||!user}><Save size={17}/> Save to all devices</button></section>
    {message&&<div className="message">{message}</div>}

    <style jsx global>{`
      *{box-sizing:border-box}body{margin:0;background:#07090d;color:#f7f8fb;font-family:Inter,system-ui,sans-serif}.control-shell{max-width:1240px;margin:auto;padding:28px 22px 100px}.control-shell>header{display:grid;grid-template-columns:auto 1fr auto;gap:24px;align-items:start;padding:16px 0 32px;border-bottom:1px solid #222a35}.control-shell>header>a{color:#a995ff}.control-shell>header h1{font-size:clamp(36px,5vw,62px);letter-spacing:-.05em;margin:8px 0}.control-shell>header p,.panel p{color:#929dab;line-height:1.6}.cloud{display:flex;gap:8px;align-items:center;padding:9px 11px;border:1px solid #3a2730;background:#1b1115;color:#dc8490;border-radius:999px}.cloud.on{border-color:#244f36;background:#0f1c15;color:#68da8d}.dashboard{display:grid;grid-template-columns:repeat(4,1fr);gap:10px;margin:22px 0}.dashboard article{background:#0e1218;border:1px solid #232b36;border-radius:16px;padding:16px}.dashboard b,.dashboard span{display:block}.dashboard b{font-size:24px;margin-top:10px}.dashboard span{font-size:11px;color:#7f8997}.panel{background:#0c1016;border:1px solid #232b36;border-radius:20px;padding:22px;margin:14px 0}.panel h2{margin:4px 0;font-size:26px}.panel small{color:#9080e8;letter-spacing:.12em}.form-grid{display:grid;grid-template-columns:1fr 1.5fr 1.5fr auto;gap:8px}.form-grid input,.add-line input,.settings-grid input,.settings-grid select,.auth input{height:42px;border:1px solid #29323e;background:#11161e;color:white;border-radius:10px;padding:0 11px;outline:0}.form-grid button,.add-line button,.auth button,.syncbar button{border:0;background:white;color:#090b0f;border-radius:10px;padding:0 14px;font-weight:800;display:flex;align-items:center;gap:7px;justify-content:center}.rows>div,.addon-row{display:grid;grid-template-columns:32px 1fr auto auto;gap:10px;align-items:center;padding:12px 0;border-top:1px solid #1e252e}.rows b,.rows small,.addon-row b,.addon-row small,.addon-row code{display:block}.rows small,.addon-row small{color:#7f8997}.rows button,.addon-row>button{background:none;border:0;color:#a7b0bd}.add-line{display:grid;grid-template-columns:1fr auto;gap:8px;margin:15px 0}.addon-row code{font-size:10px;color:#697483;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.addon-row label{display:flex;gap:6px;align-items:center}.auth{display:grid;grid-template-columns:1fr 1fr;gap:20px}.auth form{display:grid;gap:8px}.auth button{min-height:42px}.settings-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:12px}.settings-grid label{display:grid;gap:6px;color:#aab3bf}.settings-grid .toggle{display:flex;align-items:center}.syncbar{position:sticky;bottom:16px;margin-top:24px;background:#121823eF;border:1px solid #35405a;border-radius:16px;padding:14px;display:flex;justify-content:space-between;align-items:center;gap:14px;backdrop-filter:blur(16px)}.syncbar>div{display:flex;align-items:center;gap:10px}.syncbar b,.syncbar span{display:block}.syncbar span{color:#8c97a6;font-size:11px}.syncbar button{min-height:42px}.message{margin-top:12px;padding:12px 14px;border:1px solid #314057;background:#101722;border-radius:12px;color:#cdd6e4}@media(max-width:850px){.control-shell>header{grid-template-columns:1fr}.cloud{justify-self:start}.dashboard{grid-template-columns:1fr 1fr}.form-grid{grid-template-columns:1fr}.auth{grid-template-columns:1fr}.settings-grid{grid-template-columns:1fr 1fr}}@media(max-width:560px){.dashboard,.settings-grid{grid-template-columns:1fr}.syncbar{align-items:stretch;flex-direction:column}.syncbar button{width:100%}.addon-row,.rows>div{grid-template-columns:28px 1fr auto}.addon-row label{grid-column:2}.form-grid button,.add-line button{min-height:42px}}
    `}</style>
  </main>
}

function AddonRows({kind,addons,toggle,remove}:{kind:'stremio'|'cloudstream';addons:CloudAddon[];toggle:(id:string)=>void;remove:(id:string)=>void}){
  return <div>{addons.filter(x=>x.kind===kind).map(x=><div className="addon-row" key={x.id}><SlidersHorizontal/><div><b>{x.name}{x.custom?' • Custom':''}</b><small>{x.reviewed?'Reviewed default':'User-managed / opt-in'}</small><code>{x.url}</code></div><label><input type="checkbox" checked={x.enabled} onChange={()=>toggle(x.id)}/> Enabled</label>{x.custom&&<button onClick={()=>remove(x.id)}><X/></button>}</div>)}</div>
}
function hash(value:string){let h=0;for(let i=0;i<value.length;i++)h=((h<<5)-h)+value.charCodeAt(i)|0;return h}
