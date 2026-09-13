'use client';

import { useEffect, useState } from 'react';
import { onAuthStateChanged, User } from 'firebase/auth';
import { Headphones, Languages, Subtitles, Tv2 } from 'lucide-react';
import { firebaseAuth } from '../../../../lib/firebase';
import { CloudAppConfig, FirebaseData } from '../../../../lib/firebase-data';

const languageOptions=[['en','English'],['es','Spanish'],['fr','French'],['de','German'],['it','Italian'],['pt','Portuguese'],['ja','Japanese'],['ko','Korean']];

export default function PlaybackPreferencesPage(){
  const [user,setUser]=useState<User|null>(null);
  const [config,setConfig]=useState<CloudAppConfig>({version:1,preferredSubtitleLanguage:'en',preferredAudioLanguage:'en',subtitlesEnabled:true,preferForcedSubtitles:true,preferHearingImpairedSubtitles:false,autoplayNextEpisode:true,preferSurroundAudio:true});
  const [message,setMessage]=useState('');
  const [saving,setSaving]=useState(false);

  useEffect(()=>firebaseAuth?onAuthStateChanged(firebaseAuth,current=>{
    setUser(current);
    if(!current)return;
    FirebaseData.getAppConfig(current.uid).then(existing=>{if(existing)setConfig(currentConfig=>({...currentConfig,...existing}))}).catch(()=>{});
  }):undefined,[]);

  function update<K extends keyof CloudAppConfig>(key:K,value:CloudAppConfig[K]){setConfig(current=>({...current,[key]:value}))}

  async function save(){
    if(!user){setMessage('Sign in first so playback preferences can sync across AstraWave devices.');return}
    setSaving(true);setMessage('Saving playback preferences…');
    try{
      const next={...config,version:(config.version||0)+1};
      await FirebaseData.saveAppConfig(user.uid,next);
      setConfig(next);
      try{localStorage.setItem('astrawave:playback-preferences',JSON.stringify(next))}catch{}
      setMessage('✓ Playback preferences saved and synced.');
    }catch(error){setMessage(error instanceof Error?error.message:'Could not save playback preferences')}finally{setSaving(false)}
  }

  return <main className="playbackPrefs"><header><small>ASTRAWAVE PLAYER</small><h1>Playback preferences</h1><p>Set your default subtitle, audio and binge behavior once. Signed-in devices use the same preferences.</p></header>
    <section className="prefGrid">
      <article><Subtitles/><h2>Subtitles</h2><label><span>Default language</span><select value={config.preferredSubtitleLanguage||'en'} onChange={e=>update('preferredSubtitleLanguage',e.target.value)}>{languageOptions.map(([value,label])=><option key={value} value={value}>{label}</option>)}</select></label><Toggle label="Enable subtitles by default" value={config.subtitlesEnabled!==false} onChange={value=>update('subtitlesEnabled',value)}/><Toggle label="Prefer forced subtitles" value={config.preferForcedSubtitles!==false} onChange={value=>update('preferForcedSubtitles',value)}/><Toggle label="Prefer hearing-impaired tracks" value={Boolean(config.preferHearingImpairedSubtitles)} onChange={value=>update('preferHearingImpairedSubtitles',value)}/></article>
      <article><Headphones/><h2>Audio</h2><label><span>Preferred language</span><select value={config.preferredAudioLanguage||config.preferredLanguage||'en'} onChange={e=>update('preferredAudioLanguage',e.target.value)}>{languageOptions.map(([value,label])=><option key={value} value={value}>{label}</option>)}</select></label><Toggle label="Prefer surround / premium audio" value={config.preferSurroundAudio!==false} onChange={value=>update('preferSurroundAudio',value)}/><p className="hint">AstraWave will prefer compatible surround, Atmos/DD+/DTS-style tracks when providers expose that metadata.</p></article>
      <article><Tv2/><h2>Binge</h2><Toggle label="AutoNext episodes" value={config.autoplayNextEpisode!==false} onChange={value=>update('autoplayNextEpisode',value)}/><p className="hint">Episode playback already pre-resolves the selected S/E and keeps separate resume state. This preference controls automatic advancement after playback ends.</p></article>
      <article><Languages/><h2>Language & region</h2><label><span>App language</span><select value={config.preferredLanguage||'en'} onChange={e=>update('preferredLanguage',e.target.value)}>{languageOptions.map(([value,label])=><option key={value} value={value}>{label}</option>)}</select></label><label><span>Region</span><input value={config.preferredRegion||'US'} maxLength={2} onChange={e=>update('preferredRegion',e.target.value.toUpperCase())}/></label></article>
    </section>
    <button className="saveBtn" onClick={save} disabled={saving}>{saving?'Saving…':'Save & Sync'}</button>{message&&<div className="notice">{message}</div>}
    <style jsx global>{`body{margin:0;background:#07090d;color:#f7f8fb;font-family:Inter,system-ui,sans-serif}.playbackPrefs{max-width:1050px;margin:auto;padding:34px 22px 100px}.playbackPrefs header{max-width:760px}.playbackPrefs header small{color:#9e8fff;letter-spacing:.14em}.playbackPrefs h1{font-size:clamp(38px,6vw,64px);letter-spacing:-.055em;margin:8px 0 12px}.playbackPrefs header p,.hint{color:#8f9aaa;line-height:1.6}.prefGrid{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-top:26px}.prefGrid article{border:1px solid #26303c;background:linear-gradient(145deg,#101620,#0b0f15);border-radius:20px;padding:20px;box-shadow:0 18px 50px #0003}.prefGrid article>svg{color:#9c8cff}.prefGrid h2{margin:10px 0 18px}.prefGrid label{display:grid;gap:7px;margin-top:12px;color:#adb6c5;font-size:12px}.prefGrid select,.prefGrid input{height:44px;border:1px solid #303a48;background:#0b1016;color:white;border-radius:11px;padding:0 11px}.toggleRow{display:flex;align-items:center;justify-content:space-between;gap:14px;margin-top:13px;padding-top:13px;border-top:1px solid #202833;color:#c6ccd6;font-size:12px}.toggleRow button{width:48px;height:28px;border-radius:999px;border:1px solid #35404f;background:#171d26;padding:3px;display:flex;justify-content:flex-start;cursor:pointer}.toggleRow button.active{justify-content:flex-end;background:#30275b;border-color:#7362dc}.toggleRow i{display:block;width:20px;height:20px;border-radius:50%;background:#eef0f6}.saveBtn{margin-top:18px;height:46px;border:0;border-radius:12px;padding:0 18px;font-weight:850;background:white;color:#090b0f}.notice{margin-top:12px;border:1px solid #2e3947;background:#0e141c;border-radius:11px;padding:11px 13px;color:#aab4c3}@media(max-width:720px){.prefGrid{grid-template-columns:1fr}}`}</style>
  </main>
}

function Toggle({label,value,onChange}:{label:string;value:boolean;onChange:(value:boolean)=>void}){return <div className="toggleRow"><span>{label}</span><button className={value?'active':''} onClick={()=>onChange(!value)} aria-pressed={value}><i/></button></div>}
