'use client';

import { useEffect, useRef, useState } from 'react';
import { CheckCircle2, ExternalLink, Loader2, LogOut, ShieldCheck, Zap } from 'lucide-react';

const TOKEN_KEY='astrawave:rd-access-token';

type Profile={connected:boolean;username?:string|null;type?:string|null;expiration?:string|null;avatar?:string|null};
type DeviceState={deviceCode:string;userCode:string;verificationUrl:string;interval:number;expiresIn:number};

export default function DebridPage(){
  const [profile,setProfile]=useState<Profile|null>(null);
  const [device,setDevice]=useState<DeviceState|null>(null);
  const [message,setMessage]=useState('');
  const [connecting,setConnecting]=useState(false);
  const cancelRef=useRef(false);

  async function verify(token:string){
    const response=await fetch('/api/astrawave/debrid/real-debrid/verify',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({accessToken:token}),cache:'no-store'});
    if(!response.ok)throw new Error('Real-Debrid session is no longer valid.');
    const body=await response.json() as Profile;setProfile(body);return body;
  }

  useEffect(()=>{
    cancelRef.current=false;
    const token=sessionStorage.getItem(TOKEN_KEY);
    if(token)verify(token).catch(()=>{sessionStorage.removeItem(TOKEN_KEY);setProfile(null)});
    return()=>{cancelRef.current=true};
  },[]);

  async function connect(){
    setConnecting(true);setMessage('Starting secure device authorization…');setProfile(null);setDevice(null);cancelRef.current=false;
    try{
      const start=await fetch('/api/astrawave/debrid/real-debrid/device/start',{method:'POST',cache:'no-store'});
      const body=await start.json() as DeviceState&{error?:string};if(!start.ok)throw new Error(body.error||'Could not start authorization');
      setDevice(body);setMessage('Open Real-Debrid, enter the code shown below, and approve AstraWave. This page will detect approval automatically.');
      window.open(body.verificationUrl,'_blank','noopener,noreferrer');
      const deadline=Date.now()+body.expiresIn*1000;
      while(!cancelRef.current&&Date.now()<deadline){
        await new Promise(resolve=>setTimeout(resolve,Math.max(3,body.interval)*1000));
        const poll=await fetch('/api/astrawave/debrid/real-debrid/device/poll',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({deviceCode:body.deviceCode}),cache:'no-store'});
        if(poll.status===202)continue;
        const result=await poll.json() as {connected?:boolean;accessToken?:string;error?:string};
        if(!poll.ok||!result.accessToken)throw new Error(result.error||'Authorization failed');
        sessionStorage.setItem(TOKEN_KEY,result.accessToken);
        await verify(result.accessToken);setDevice(null);setMessage('✓ Real-Debrid connected for this browser session. Play Best can now optimize already-authorized sources.');return;
      }
      if(!cancelRef.current)throw new Error('Authorization code expired. Start again for a new code.');
    }catch(error){setMessage(error instanceof Error?error.message:'Could not connect Real-Debrid')}finally{setConnecting(false)}
  }

  function disconnect(){cancelRef.current=true;sessionStorage.removeItem(TOKEN_KEY);setProfile(null);setDevice(null);setConnecting(false);setMessage('Real-Debrid disconnected from this browser session.');}

  return <main className="debridSetup"><header><small>ASTRAWAVE • USER-LINKED DEBRID</small><h1>Fast sources, without handing AstraWave your password.</h1><p>Connect Real-Debrid using its device authorization flow. AstraWave only uses the linked account to optimize playback candidates that your existing source resolver already accepted.</p></header>
    <section className="debridCard"><div className="brandMark"><Zap/></div><div className="debridCopy"><div className="statusLine">{profile?.connected?<><CheckCircle2/> Connected</>:<><ShieldCheck/> Not connected</>}</div><h2>Real-Debrid</h2>{profile?.connected?<><p className="account">{profile.username||'Connected account'}{profile.type?` • ${profile.type}`:''}{profile.expiration?` • expires ${new Date(profile.expiration).toLocaleDateString()}`:''}</p><p>Your access token stays in this browser tab/session storage. It is not written into AstraWave Firestore configuration.</p></>:<p>Use a short device code. No Real-Debrid username or password is entered into AstraWave.</p>}</div><div className="debridActions">{profile?.connected?<button className="secondary" onClick={disconnect}><LogOut/> Disconnect</button>:<button className="primary" onClick={connect} disabled={connecting}>{connecting?<Loader2 className="spin"/>:<ShieldCheck/>} {connecting?'Waiting for approval…':'Connect Real-Debrid'}</button>}</div></section>
    {device&&<section className="deviceBox"><small>REAL-DEBRID DEVICE CODE</small><strong>{device.userCode}</strong><p>Enter this code at Real-Debrid. AstraWave is checking for approval automatically.</p><a href={device.verificationUrl} target="_blank" rel="noreferrer"><ExternalLink/> Open authorization page</a></section>}
    {message&&<div className="notice">{message}</div>}
    <section className="policyGrid"><article><ShieldCheck/><h3>Authorization boundary</h3><p>Debrid is an optimizer, not a discovery engine. AstraWave does not use this connection to crawl for titles, torrents, or files.</p></article><article><Zap/><h3>Play Best boost</h3><p>When an accepted source can be improved, its optimized direct URL gets promoted in the same source-ranking and fallback system.</p></article><article><LogOut/><h3>Session-only on web</h3><p>Closing the browser session removes the web token. Android keeps persistent credentials in its Keystore-backed credential store.</p></article></section>
    <style jsx global>{`body{margin:0;background:radial-gradient(circle at 75% -10%,#291f5955,transparent 35%),#07090d;color:#f8f9fc;font-family:Inter,system-ui,sans-serif}.debridSetup{max-width:980px;margin:auto;padding:36px 22px 100px}.debridSetup header{max-width:790px}.debridSetup header small,.deviceBox small{color:#9e90f5;letter-spacing:.13em}.debridSetup h1{font-size:clamp(40px,6vw,68px);letter-spacing:-.055em;line-height:.98;margin:9px 0 15px}.debridSetup header p,.debridCopy p,.policyGrid p,.deviceBox p{color:#929cab;line-height:1.6}.debridCard{margin-top:28px;border:1px solid #2b3442;background:linear-gradient(145deg,#121925,#0b1017);border-radius:24px;padding:24px;display:grid;grid-template-columns:58px 1fr auto;gap:18px;align-items:center;box-shadow:0 28px 80px #0005}.brandMark{width:58px;height:58px;border-radius:18px;background:linear-gradient(145deg,#8d7df1,#594bb7);display:grid;place-items:center}.debridCopy h2{font-size:26px;margin:4px 0}.statusLine{display:flex;align-items:center;gap:6px;color:#8fe2aa;font-size:11px;font-weight:800}.statusLine svg{width:15px}.account{color:#c8cfdb!important}.debridActions button,.deviceBox a{border-radius:12px;min-height:44px;padding:0 15px;font-weight:800;display:inline-flex;align-items:center;justify-content:center;gap:7px}.primary{border:0;background:#fff;color:#080a0e}.secondary{border:1px solid #37404c;background:#161c25;color:#fff}.deviceBox{margin-top:16px;border:1px solid #524493;background:linear-gradient(145deg,#1b1535,#0e121a);border-radius:20px;padding:22px}.deviceBox strong{display:block;font-size:40px;letter-spacing:.16em;margin:10px 0}.deviceBox a{border:1px solid #6655bf;background:#29204c;color:#fff;width:max-content}.notice{margin-top:14px;border:1px solid #2d3846;background:#0d131b;border-radius:13px;padding:12px 14px;color:#b0bac9}.policyGrid{display:grid;grid-template-columns:repeat(3,1fr);gap:11px;margin-top:18px}.policyGrid article{border:1px solid #242d39;background:#0c1118;border-radius:17px;padding:17px}.policyGrid svg{color:#9a8cf0}.policyGrid h3{margin:9px 0 3px}.policyGrid p{font-size:11px}.spin{animation:spin .9s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}@media(max-width:760px){.debridCard{grid-template-columns:52px 1fr}.debridActions{grid-column:1/-1}.debridActions button{width:100%}.policyGrid{grid-template-columns:1fr}}`}</style>
  </main>
}
