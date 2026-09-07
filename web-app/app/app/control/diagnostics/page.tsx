'use client';

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { onAuthStateChanged, type User } from 'firebase/auth';
import { firebaseAuth } from '../../../../lib/firebase';
import { FirebaseData, type CloudPlaybackDiagnostic } from '../../../../lib/firebase-data';

export default function PlaybackDiagnosticsPage(){
  const [user,setUser]=useState<User|null>(null);
  const [profileId,setProfileId]=useState('default');
  const [items,setItems]=useState<CloudPlaybackDiagnostic[]>([]);
  const [status,setStatus]=useState('Loading playback diagnostics…');
  const [busy,setBusy]=useState(false);

  useEffect(()=>{
    if(!firebaseAuth){setStatus('Firebase is not configured for this deployment.');return;}
    return onAuthStateChanged(firebaseAuth,next=>{
      setUser(next);
      if(!next)setStatus('Sign in from Control Center to view private playback diagnostics.');
    });
  },[]);

  useEffect(()=>{
    if(!user)return;
    let cancelled=false;
    setBusy(true);
    FirebaseData.listPlaybackDiagnostics(user.uid,profileId)
      .then(values=>{
        if(cancelled)return;
        setItems(values);
        setStatus(`${values.length} Play Best decision${values.length===1?'':'s'} synced for ${profileId}`);
      })
      .catch(error=>{if(!cancelled)setStatus(error instanceof Error?error.message:'Unable to load playback diagnostics');})
      .finally(()=>{if(!cancelled)setBusy(false);});
    return()=>{cancelled=true;};
  },[user,profileId]);

  const summary=useMemo(()=>{
    const debrid=items.filter(x=>x.debridOptimized).length;
    const personal=items.filter(x=>x.personalMedia).length;
    const averageLatency=Math.round(items.filter(x=>typeof x.latencyMs==='number').reduce((sum,x)=>sum+(x.latencyMs||0),0)/Math.max(1,items.filter(x=>typeof x.latencyMs==='number').length));
    const averageBackups=(items.reduce((sum,x)=>sum+x.backupCount,0)/Math.max(1,items.length)).toFixed(1);
    return {debrid,personal,averageLatency,averageBackups};
  },[items]);

  return <main style={{minHeight:'100vh',background:'#06070b',color:'#f6f3f9',padding:'28px',fontFamily:'Inter,system-ui,sans-serif'}}>
    <div style={{maxWidth:1400,margin:'0 auto'}}>
      <div style={{display:'flex',justifyContent:'space-between',gap:18,alignItems:'flex-start',marginBottom:24}}>
        <div>
          <div style={{color:'#c38bff',fontWeight:800,letterSpacing:1}}>ASTRAWAVE CONTROL CENTER</div>
          <h1 style={{fontSize:'clamp(34px,5vw,64px)',margin:'8px 0'}}>Playback Diagnostics</h1>
          <p style={{color:'#a39caa',maxWidth:820,fontSize:18}}>See why Play Best chose a source across signed-in AstraWave devices. This history contains provider, quality, latency and failover metadata only—never stream URLs, tokens, passwords or request headers.</p>
        </div>
        <Link href="/app/control" style={{color:'#fff',background:'#19151f',padding:'12px 16px',borderRadius:14,textDecoration:'none'}}>← Control Center</Link>
      </div>

      <div style={{display:'flex',gap:10,flexWrap:'wrap',marginBottom:18}}>
        {['default','kids','guest'].map(id=><button key={id} onClick={()=>setProfileId(id)} style={chip(profileId===id)}>{id==='default'?'Main profile':id}</button>)}
        <span style={{padding:'10px 14px',borderRadius:99,background:'#121018',color:'#a39caa'}}>{busy?'Refreshing…':status}</span>
      </div>

      {!user?<section style={panel}><h2>Sign in required</h2><p style={{color:'#a39caa'}}>Open Control Center and sign in. Playback diagnostics are private account data.</p></section>:
      <>
        <section style={{display:'grid',gridTemplateColumns:'repeat(auto-fit,minmax(190px,1fr))',gap:12,marginBottom:18}}>
          <Metric label="Decisions" value={String(items.length)}/>
          <Metric label="Debrid optimized" value={String(summary.debrid)}/>
          <Metric label="Owned media" value={String(summary.personal)}/>
          <Metric label="Avg latency" value={items.length?`${summary.averageLatency} ms`:'—'}/>
          <Metric label="Avg backups" value={items.length?summary.averageBackups:'—'}/>
        </section>

        {items.length===0?<section style={panel}><h2>No diagnostics yet</h2><p style={{color:'#a39caa'}}>Use Play Best on a signed-in AstraWave TV or Android device. Sanitized source-decision metadata will appear here after cloud sync.</p></section>:
        <section style={panel}>
          <div style={{display:'grid',gap:10}}>
            {items.slice(0,100).map((item,index)=><article key={item.id} style={{background:index===0?'#17121f':'#0a0a10',border:index===0?'1px solid #9d62e8':'1px solid #26212c',borderRadius:16,padding:16}}>
              <div style={{display:'flex',justifyContent:'space-between',gap:12,alignItems:'flex-start',flexWrap:'wrap'}}>
                <div>
                  <div style={{color:index===0?'#c38bff':'#746d79',fontSize:12,fontWeight:800}}>{index===0?'LATEST DECISION':item.mediaType.toUpperCase()}</div>
                  <h3 style={{margin:'5px 0 3px',fontSize:20}}>{item.title}</h3>
                  <div style={{color:'#a39caa'}}>{item.provider}{item.quality?` • ${item.quality}`:''}</div>
                </div>
                <div style={{color:'#746d79',fontSize:12}}>{new Date(item.resolvedAtEpochMs).toLocaleString()}</div>
              </div>
              <div style={{display:'flex',gap:8,flexWrap:'wrap',marginTop:13}}>
                <Badge text={typeof item.latencyMs==='number'?`${item.latencyMs} ms`:'Latency n/a'}/>
                <Badge text={`${item.providerCount} provider${item.providerCount===1?'':'s'}`}/>
                <Badge text={`${item.backupCount} backup${item.backupCount===1?'':'s'}`}/>
                {item.debridOptimized&&<Badge text="Debrid optimized" accent/>}
                {item.personalMedia&&<Badge text="Owned media" accent/>}
              </div>
            </article>)}
          </div>
        </section>}
      </>}
    </div>
  </main>;
}

function Metric({label,value}:{label:string;value:string}){return <div style={{...panel,padding:16}}><div style={{color:'#746d79',fontSize:12,fontWeight:800}}>{label.toUpperCase()}</div><div style={{fontSize:28,fontWeight:900,marginTop:5}}>{value}</div></div>}
function Badge({text,accent=false}:{text:string;accent?:boolean}){return <span style={{padding:'7px 10px',borderRadius:99,background:accent?'#3a2353':'#15131a',color:accent?'#d8b6ff':'#aaa3af',fontSize:12,fontWeight:700}}>{text}</span>}
const panel:React.CSSProperties={background:'#0d0d13',border:'1px solid #27212d',borderRadius:22,padding:18};
const secondary:React.CSSProperties={background:'#19151f',border:'1px solid #352c3d',borderRadius:13,padding:'12px 17px',color:'#fff',fontWeight:700,cursor:'pointer'};
function chip(active:boolean):React.CSSProperties{return {...secondary,borderRadius:99,background:active?'#6f3eb6':'#19151f'}}
