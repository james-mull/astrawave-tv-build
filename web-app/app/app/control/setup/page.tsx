'use client';

import { useEffect, useMemo, useState } from 'react';
import { onAuthStateChanged, User } from 'firebase/auth';
import { CheckCircle2, Cloud, Database, Link2, RadioTower, Server, ShieldCheck, Sparkles, Tv2 } from 'lucide-react';
import { firebaseAuth } from '../../../../lib/firebase';
import { CloudAddon, CloudAppConfig, CloudSource, FirebaseData } from '../../../../lib/firebase-data';

type SetupKind='stremio'|'cloudstream'|'m3u'|'xtream'|'plex'|'jellyfin';
const options:[SetupKind,string,string,React.ComponentType<any>][]=[
  ['stremio','Stremio','Paste a manifest URL and AstraWave validates it.',Sparkles],
  ['cloudstream','CloudStream','Paste a repo URL. Community plugins stay opt-in.',Database],
  ['m3u','M3U / XMLTV','Add an authorized IPTV playlist and optional guide.',RadioTower],
  ['xtream','Xtream','Add your provider URL and account details.',Tv2],
  ['plex','Plex','Connect your own Plex server/library endpoint.',Server],
  ['jellyfin','Jellyfin','Connect your own Jellyfin server/library endpoint.',Cloud],
];

function hash(value:string){let h=0;for(let i=0;i<value.length;i++)h=((h<<5)-h)+value.charCodeAt(i)|0;return Math.abs(h)}

export default function EasySetupPage(){
  const [user,setUser]=useState<User|null>(null),[kind,setKind]=useState<SetupKind>('stremio'),[name,setName]=useState(''),[url,setUrl]=useState(''),[extraUrl,setExtraUrl]=useState(''),[username,setUsername]=useState(''),[password,setPassword]=useState(''),[message,setMessage]=useState(''),[probing,setProbing]=useState(false),[probeOk,setProbeOk]=useState(false),[saving,setSaving]=useState(false);
  useEffect(()=>firebaseAuth?onAuthStateChanged(firebaseAuth,setUser):undefined,[]);
  useEffect(()=>{setProbeOk(false);setMessage('');setName('');setUrl('');setExtraUrl('');setUsername('');setPassword('')},[kind]);
  const selected=useMemo(()=>options.find(x=>x[0]===kind)!,[kind]);
  const SelectedIcon=selected[3];
  const canProbe=['stremio','cloudstream','m3u'].includes(kind)&&/^https?:\/\//i.test(url.trim());

  async function probe(){
    if(!canProbe)return;setProbing(true);setMessage('Checking source…');setProbeOk(false);
    try{const r=await fetch(`/api/astrawave/setup-probe?kind=${kind}&url=${encodeURIComponent(url.trim())}`,{cache:'no-store'});const body=await r.json();setProbeOk(Boolean(body.ok));setMessage(body.ok?`✓ ${body.summary||'Source looks valid'}`:body.error||body.summary||'Source could not be validated')}catch{setMessage('Could not validate source')}finally{setProbing(false)}
  }

  async function save(){
    if(!url.trim()){setMessage('Enter the source/server URL first.');return}
    if(!user){setMessage('Sign in from Sources first so this setup can sync to your devices.');return}
    setSaving(true);setMessage('Saving to AstraWave Cloud…');
    try{
      if(kind==='stremio'||kind==='cloudstream'){
        const current=await FirebaseData.getAppConfig(user.uid)||({version:1,addons:[]} as CloudAppConfig);
        const id=`custom-${kind}-${hash(url.trim())}`;const addon:CloudAddon={id,name:name.trim()||(`${kind==='stremio'?'Stremio Addon':'CloudStream Repo'}`),kind,url:url.trim(),enabled:true,reviewed:false,custom:true};
        const addons=[...(current.addons||[]).filter(x=>x.id!==id),addon];await FirebaseData.saveAppConfig(user.uid,{...current,addons,version:(current.version||0)+1});
      }else{
        const type=kind==='m3u'?'M3U':kind==='xtream'?'XTREAM':kind==='plex'?'PLEX':'JELLYFIN';
        const id=`source-${kind}-${hash(url.trim())}`;const config:Record<string,unknown>=kind==='m3u'?{m3uUrl:url.trim(),xmlTvUrl:extraUrl.trim()||null}:kind==='xtream'?{baseUrl:url.trim(),username:username.trim(),password}: {baseUrl:url.trim(),token:password||null};
        const source:CloudSource={id,name:name.trim()||selected[1],type,enabled:true,priority:20,config};await FirebaseData.saveSource(user.uid,source);
      }
      setMessage('✓ Added and synced. Your signed-in AstraWave devices can use this configuration.');setProbeOk(true)
    }catch(error){setMessage(error instanceof Error?error.message:'Could not save source')}finally{setSaving(false)}
  }

  return <main className="easySetup"><header><small>ASTRAWAVE EASY CONTENT SETUP</small><h1>Add content without the technical mess.</h1><p>Choose how you already access content, paste the one URL you were given, and AstraWave handles the configuration. Playback still requires sources you are authorized to use.</p></header>
    <section className="choices">{options.map(([id,label,desc,Icon])=><button key={id} className={kind===id?'active':''} onClick={()=>setKind(id)}><Icon/><b>{label}</b><span>{desc}</span></button>)}</section>
    <section className="wizard"><div className="wizardHead"><SelectedIcon/><div><small>STEP 1 OF 2</small><h2>{selected[1]}</h2><p>{selected[2]}</p></div></div>
      <div className="fields"><label>Display name <input value={name} onChange={e=>setName(e.target.value)} placeholder={`My ${selected[1]}`}/></label><label>{kind==='stremio'?'Manifest URL':kind==='cloudstream'?'Repository URL':kind==='m3u'?'M3U playlist URL':'Server / provider URL'} <input value={url} onChange={e=>{setUrl(e.target.value);setProbeOk(false)}} placeholder={kind==='stremio'?'https://.../manifest.json':kind==='cloudstream'?'https://.../repo.json':'https://...'}/></label>
      {kind==='m3u'&&<label>XMLTV guide URL <input value={extraUrl} onChange={e=>setExtraUrl(e.target.value)} placeholder="Optional"/></label>}
      {kind==='xtream'&&<><label>Username <input value={username} onChange={e=>setUsername(e.target.value)}/></label><label>Password <input type="password" value={password} onChange={e=>setPassword(e.target.value)}/></label></>}
      {(kind==='plex'||kind==='jellyfin')&&<label>Access token / API key <input type="password" value={password} onChange={e=>setPassword(e.target.value)} placeholder="Your own server credential"/></label>}</div>
      <div className="actions">{canProbe&&<button className="secondary" onClick={probe} disabled={probing}><ShieldCheck/> {probing?'Checking…':'Check source'}</button>}<button className="primary" onClick={save} disabled={saving}><CheckCircle2/> {saving?'Saving…':'Add to AstraWave'}</button></div>{message&&<div className={probeOk?'notice ok':'notice'}>{message}</div>}
    </section>
    <section className="how"><article><Link2/><h3>Stremio</h3><p>Paste a manifest URL. AstraWave can sync the addon definition across devices and use eligible catalog/stream resources according to authorization rules.</p></article><article><Database/><h3>CloudStream</h3><p>Paste a repository URL. Repositories can be discovered easily, while individual community plugins remain opt-in rather than automatically trusted.</p></article><article><RadioTower/><h3>IPTV</h3><p>M3U + XMLTV and Xtream are available for customer-authorized providers. The player can still use Unified and Provider Order behavior.</p></article><article><Server/><h3>Your media</h3><p>Plex and Jellyfin give AstraWave another clean path to content you already own or are authorized to access.</p></article></section>
    <div className="policy"><ShieldCheck/> AstraWave organizes and plays eligible sources. It does not turn an untrusted repository or addon into an authorized source automatically.</div>
    <style jsx global>{`body{margin:0;background:#07090d;color:#f7f8fb;font-family:Inter,system-ui,sans-serif}.easySetup{max-width:1120px;margin:auto;padding:30px 22px 100px}.easySetup header small,.wizard small{color:#9787f1;letter-spacing:.13em}.easySetup header h1{font-size:clamp(38px,6vw,68px);line-height:.98;letter-spacing:-.055em;margin:9px 0 14px}.easySetup header p,.wizard p,.how p{color:#929cab;line-height:1.6}.choices{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin:28px 0}.choices button{min-height:138px;border:1px solid #252e3b;background:#0d1219;color:white;border-radius:18px;padding:18px;text-align:left;display:flex;flex-direction:column;align-items:flex-start;gap:8px;cursor:pointer}.choices button.active{border-color:#7867e8;background:linear-gradient(145deg,#211a40,#111522)}.choices button svg{color:#a999ff}.choices button b{font-size:17px}.choices button span{font-size:11px;color:#8994a3;line-height:1.45}.wizard{border:1px solid #293340;background:linear-gradient(145deg,#101620,#0b0f15);border-radius:22px;padding:24px}.wizardHead{display:flex;gap:14px;align-items:flex-start}.wizardHead>svg{width:34px;height:34px;color:#a999ff}.wizard h2{font-size:28px;margin:3px 0}.fields{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-top:18px}.fields label{display:grid;gap:6px;color:#b5bdc9;font-size:12px}.fields input{height:46px;border:1px solid #2b3542;background:#0d1218;color:white;border-radius:11px;padding:0 12px;outline:none}.fields input:focus{border-color:#7867e8}.actions{display:flex;gap:9px;margin-top:18px}.actions button{min-height:44px;border-radius:11px;padding:0 15px;font-weight:800;display:flex;align-items:center;gap:7px}.primary{border:0;background:white;color:#090b0f}.secondary{border:1px solid #303b49;background:#141a22;color:white}.notice{margin-top:12px;padding:11px 13px;border-radius:11px;background:#22171b;border:1px solid #53313b;color:#e9a6b0}.notice.ok{background:#102017;border-color:#28513a;color:#8de2a9}.how{display:grid;grid-template-columns:repeat(4,1fr);gap:10px;margin-top:20px}.how article{border:1px solid #222b36;background:#0d1117;border-radius:16px;padding:16px}.how svg{color:#9484ef}.how h3{margin:9px 0 4px}.how p{font-size:11px}.policy{margin-top:16px;display:flex;gap:8px;align-items:center;color:#7e8997;font-size:11px}@media(max-width:820px){.choices{grid-template-columns:1fr 1fr}.how{grid-template-columns:1fr 1fr}.fields{grid-template-columns:1fr}}@media(max-width:520px){.choices,.how{grid-template-columns:1fr}.actions{flex-direction:column}.actions button{justify-content:center}}`}</style>
  </main>
}
