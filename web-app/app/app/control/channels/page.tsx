'use client';

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { onAuthStateChanged, type User } from 'firebase/auth';
import { firebaseAuth } from '../../../../lib/firebase';
import {
  FirebaseData,
  type CloudChannelCustomization,
} from '../../../../lib/firebase-data';
import { AstraWaveApi, type LiveChannel } from '../../../../lib/astrawave-api';

type EditorDraft = {
  customName:string;
  customNumber:string;
  customGroup:string;
  hidden:boolean;
  sortOrder:string;
  epgIdOverride:string;
  logoUrlOverride:string;
};

const blankDraft:EditorDraft={customName:'',customNumber:'',customGroup:'',hidden:false,sortOrder:'',epgIdOverride:'',logoUrlOverride:''};

function draftFor(channel:LiveChannel, saved?:CloudChannelCustomization):EditorDraft{
  return {
    customName:saved?.customName ?? '',
    customNumber:saved?.customNumber?.toString() ?? '',
    customGroup:saved?.customGroup ?? '',
    hidden:saved?.hidden ?? false,
    sortOrder:saved?.sortOrder?.toString() ?? '',
    epgIdOverride:saved?.epgIdOverride ?? '',
    logoUrlOverride:saved?.logoUrlOverride ?? channel.logoUrl ?? '',
  };
}

export default function ChannelStudioPage(){
  const [user,setUser]=useState<User|null>(null);
  const [profileId,setProfileId]=useState('default');
  const [channels,setChannels]=useState<LiveChannel[]>([]);
  const [saved,setSaved]=useState<CloudChannelCustomization[]>([]);
  const [selectedId,setSelectedId]=useState<string|null>(null);
  const [draft,setDraft]=useState<EditorDraft>(blankDraft);
  const [query,setQuery]=useState('');
  const [status,setStatus]=useState('Loading Channel Studio…');
  const [busy,setBusy]=useState(false);

  useEffect(()=>{
    if(!firebaseAuth){setStatus('Firebase is not configured for this deployment.');return;}
    return onAuthStateChanged(firebaseAuth,next=>{setUser(next);if(!next)setStatus('Sign in from Control Center to sync channel edits to your TVs.');});
  },[]);

  useEffect(()=>{
    if(!user)return;
    let cancelled=false;
    setBusy(true);
    Promise.all([
      AstraWaveApi.liveData('all-free'),
      FirebaseData.listChannelCustomizations(user.uid),
    ]).then(([live,cloud])=>{
      if(cancelled)return;
      setChannels(live.channels ?? []);
      setSaved(cloud);
      setStatus(`${live.channels?.length ?? 0} channels loaded • ${cloud.filter(x=>x.profileId===profileId).length} cloud edits`);
    }).catch(error=>{if(!cancelled)setStatus(error instanceof Error?error.message:'Unable to load Channel Studio');})
      .finally(()=>{if(!cancelled)setBusy(false);});
    return()=>{cancelled=true;};
  },[user,profileId]);

  const savedMap=useMemo(()=>new Map(saved.filter(x=>x.profileId===profileId).map(x=>[x.channelId,x])),[saved,profileId]);
  const visible=useMemo(()=>{
    const q=query.trim().toLowerCase();
    return channels.filter(channel=>{
      const edit=savedMap.get(channel.id);
      const name=edit?.customName||channel.name;
      const group=edit?.customGroup||channel.group||'';
      return !q||name.toLowerCase().includes(q)||group.toLowerCase().includes(q)||(channel.sources||[]).join(' ').toLowerCase().includes(q);
    }).sort((a,b)=>{
      const ae=savedMap.get(a.id),be=savedMap.get(b.id);
      const ao=ae?.sortOrder||Number.MAX_SAFE_INTEGER,bo=be?.sortOrder||Number.MAX_SAFE_INTEGER;
      if(ao!==bo)return ao-bo;
      const an=ae?.customNumber||Number.MAX_SAFE_INTEGER,bn=be?.customNumber||Number.MAX_SAFE_INTEGER;
      if(an!==bn)return an-bn;
      return (ae?.customName||a.name).localeCompare(be?.customName||b.name);
    });
  },[channels,savedMap,query]);

  const selected=channels.find(x=>x.id===selectedId)||null;
  function choose(channel:LiveChannel){setSelectedId(channel.id);setDraft(draftFor(channel,savedMap.get(channel.id)));}

  async function saveEdit(){
    if(!user||!selected)return;
    setBusy(true);
    const id=`${profileId}:${selected.id}`.replace(/\//g,'_');
    const item:CloudChannelCustomization={
      id,profileId,channelId:selected.id,
      customName:draft.customName.trim()||null,
      customNumber:draft.customNumber.trim()?Number(draft.customNumber):null,
      customGroup:draft.customGroup.trim()||null,
      hidden:draft.hidden,
      sortOrder:draft.sortOrder.trim()?Number(draft.sortOrder):0,
      epgIdOverride:draft.epgIdOverride.trim()||null,
      logoUrlOverride:draft.logoUrlOverride.trim()||null,
    };
    try{
      await FirebaseData.saveChannelCustomization(user.uid,item);
      const next=await FirebaseData.listChannelCustomizations(user.uid);
      setSaved(next);setStatus(`Saved ${selected.name}. Signed-in TVs can pull this profile update automatically.`);
    }catch(error){setStatus(error instanceof Error?error.message:'Unable to save channel edit');}
    finally{setBusy(false);}
  }

  async function resetEdit(){
    if(!user||!selected)return;
    const existing=savedMap.get(selected.id);if(!existing)return;
    setBusy(true);
    try{
      await FirebaseData.deleteChannelCustomization(user.uid,existing.id);
      setSaved(await FirebaseData.listChannelCustomizations(user.uid));
      setDraft(draftFor(selected));setStatus(`Reset ${selected.name} to provider defaults.`);
    }catch(error){setStatus(error instanceof Error?error.message:'Unable to reset channel');}
    finally{setBusy(false);}
  }

  return <main style={{minHeight:'100vh',background:'#06070b',color:'#f6f3f9',padding:'28px',fontFamily:'Inter,system-ui,sans-serif'}}>
    <div style={{maxWidth:1500,margin:'0 auto'}}>
      <div style={{display:'flex',justifyContent:'space-between',gap:18,alignItems:'flex-start',marginBottom:24}}>
        <div><div style={{color:'#c38bff',fontWeight:800,letterSpacing:1}}>ASTRAWAVE CONTROL CENTER</div><h1 style={{fontSize:'clamp(34px,5vw,64px)',margin:'8px 0'}}>Channel Studio</h1><p style={{color:'#a39caa',maxWidth:760,fontSize:18}}>Edit the channel lineup once on the web. Rename, number, regroup, hide, repair logos and map XMLTV IDs, then sync the same private profile configuration to signed-in TVs.</p></div>
        <Link href="/app/control" style={{color:'#fff',background:'#19151f',padding:'12px 16px',borderRadius:14,textDecoration:'none'}}>← Control Center</Link>
      </div>

      <div style={{display:'flex',gap:10,flexWrap:'wrap',marginBottom:18}}>
        {['default','kids','guest'].map(id=><button key={id} onClick={()=>setProfileId(id)} style={chip(profileId===id)}>{id==='default'?'Main profile':id}</button>)}
        <span style={{padding:'10px 14px',borderRadius:99,background:'#121018',color:'#a39caa'}}>{status}</span>
      </div>

      {!user?<section style={panel}><h2>Sign in required</h2><p style={{color:'#a39caa'}}>Open Control Center and sign in. Channel Studio stores edits only in your private Firestore account.</p></section>:
      <div style={{display:'grid',gridTemplateColumns:'minmax(320px,0.9fr) minmax(360px,1.1fr)',gap:18,alignItems:'start'}}>
        <section style={panel}>
          <input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Search channels, groups or sources" style={input}/>
          <div style={{marginTop:14,maxHeight:'68vh',overflow:'auto'}}>
            {visible.slice(0,1000).map(channel=>{
              const edit=savedMap.get(channel.id);const active=channel.id===selectedId;
              return <button key={channel.id} onClick={()=>choose(channel)} style={{width:'100%',textAlign:'left',padding:'13px 14px',marginBottom:7,borderRadius:14,border:active?'1px solid #c38bff':'1px solid #28212f',background:active?'#241a31':'#0e0e14',color:'#f6f3f9',cursor:'pointer'}}>
                <div style={{display:'flex',justifyContent:'space-between',gap:10}}><strong>{edit?.customNumber?`${edit.customNumber}  `:''}{edit?.customName||channel.name}</strong><span style={{fontSize:12,color:edit?.hidden?'#ff6f83':edit?'#c38bff':'#746d79'}}>{edit?.hidden?'HIDDEN':edit?'CUSTOM':'EDIT'}</span></div>
                <div style={{fontSize:13,color:'#a39caa',marginTop:4}}>{edit?.customGroup||channel.group||'Uncategorized'} • {(channel.sources||[]).join(', ')||'Source pending'}</div>
                <div style={{fontSize:12,color:'#746d79',marginTop:3}}>{channel.now?`Now: ${channel.now}`:'No current EPG'}{edit?.epgIdOverride?' • EPG override':''}</div>
              </button>;
            })}
          </div>
        </section>

        <section style={{...panel,position:'sticky',top:18}}>
          {!selected?<><h2>Select a channel</h2><p style={{color:'#a39caa'}}>Choose a channel on the left to customize how it appears on Live TV and Guide.</p></>:
          <>
            <div style={{display:'flex',justifyContent:'space-between',gap:12}}><div><div style={{color:'#c38bff',fontWeight:800}}>CHANNEL</div><h2 style={{fontSize:30,margin:'5px 0'}}>{selected.name}</h2><div style={{color:'#a39caa'}}>{selected.group||'Uncategorized'} • {(selected.sources||[]).join(', ')}</div></div><label style={{display:'flex',gap:8,alignItems:'center'}}><input type="checkbox" checked={draft.hidden} onChange={e=>setDraft({...draft,hidden:e.target.checked})}/> Hide</label></div>
            <div style={{display:'grid',gridTemplateColumns:'1fr 160px',gap:10,marginTop:18}}><Field label="Custom name" value={draft.customName} set={v=>setDraft({...draft,customName:v})}/><Field label="Number" value={draft.customNumber} set={v=>setDraft({...draft,customNumber:v.replace(/\D/g,'')})}/></div>
            <Field label="Custom group" value={draft.customGroup} set={v=>setDraft({...draft,customGroup:v})}/>
            <Field label="EPG / tvg-id override" value={draft.epgIdOverride} set={v=>setDraft({...draft,epgIdOverride:v})}/>
            <Field label="Logo URL override" value={draft.logoUrlOverride} set={v=>setDraft({...draft,logoUrlOverride:v})}/>
            <Field label="Sort order" value={draft.sortOrder} set={v=>setDraft({...draft,sortOrder:v.replace(/[^\d-]/g,'')})}/>
            <div style={{display:'flex',gap:10,marginTop:18}}><button disabled={busy} onClick={saveEdit} style={primary}>Save & sync to TVs</button>{savedMap.has(selected.id)&&<button disabled={busy} onClick={resetEdit} style={secondary}>Reset</button>}</div>
            <div style={{marginTop:18,padding:14,borderRadius:14,background:'#0a0910',color:'#a39caa',fontSize:14}}>Current guide linkage: {selected.programs?.length||0} schedule entries. EPG overrides change metadata mapping only; they do not add, unlock or authorize a stream.</div>
          </>}
        </section>
      </div>}
    </div>
  </main>;
}

function Field({label,value,set}:{label:string;value:string;set:(v:string)=>void}){return <label style={{display:'block',marginTop:12,color:'#a39caa',fontSize:13}}>{label}<input value={value} onChange={e=>set(e.target.value)} style={{...input,marginTop:6}}/></label>}
const panel:React.CSSProperties={background:'#0d0d13',border:'1px solid #27212d',borderRadius:22,padding:18};
const input:React.CSSProperties={boxSizing:'border-box',width:'100%',background:'#08090d',border:'1px solid #352c3d',borderRadius:12,padding:'12px 13px',color:'#fff',outline:'none'};
const primary:React.CSSProperties={background:'#9d62e8',border:0,borderRadius:13,padding:'12px 17px',color:'#fff',fontWeight:800,cursor:'pointer'};
const secondary:React.CSSProperties={background:'#19151f',border:'1px solid #352c3d',borderRadius:13,padding:'12px 17px',color:'#fff',fontWeight:700,cursor:'pointer'};
function chip(active:boolean):React.CSSProperties{return {...secondary,borderRadius:99,background:active?'#6f3eb6':'#19151f'}}
