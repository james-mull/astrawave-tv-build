'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { AstraWaveApi, SourceCandidate } from '../../../../../lib/astrawave-api';

export default function TitlePlaybackPage({ params }: { params: Promise<{ kind: string; id: string }> }) {
  const [route, setRoute] = useState<{ kind: string; id: string } | null>(null);
  const [sources, setSources] = useState<SourceCandidate[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selected, setSelected] = useState<SourceCandidate | null>(null);

  useEffect(() => { params.then(setRoute); }, [params]);
  useEffect(() => {
    if (!route) return;
    setLoading(true); setError('');
    AstraWaveApi.sources(route.kind, route.id)
      .then((items) => { setSources(items); setSelected(items.find((x) => x.url) || null); })
      .catch((e) => setError(e instanceof Error ? e.message : 'Could not load sources'))
      .finally(() => setLoading(false));
  }, [route]);

  return <main style={{minHeight:'100vh',background:'#080a0f',color:'#f7f8fb',padding:'28px'}}>
    <div style={{maxWidth:1100,margin:'0 auto'}}>
      <Link href="/app" style={{color:'#a78bfa',textDecoration:'none'}}>← Back to AstraWave</Link>
      <h1 style={{fontSize:'clamp(30px,5vw,54px)',margin:'24px 0 6px'}}>Watch on AstraWave</h1>
      <p style={{color:'#a7aebb',marginTop:0}}>AstraWave checks enabled, rights-approved sources and puts the strongest playable option first.</p>

      {loading && <div style={{padding:'28px',background:'#141924',borderRadius:18,marginTop:24}}>Finding playable sources…</div>}
      {error && <div style={{padding:'20px',background:'#2a151a',borderRadius:18,marginTop:24}}>{error}</div>}
      {!loading && !error && sources.length===0 && <div style={{padding:'28px',background:'#141924',borderRadius:18,marginTop:24}}>No approved playable source is currently available for this title. Connect additional permitted providers or try another title.</div>}

      {selected?.url && <section style={{marginTop:26,background:'#050609',borderRadius:20,overflow:'hidden',border:'1px solid #252c3b'}}>
        <video src={selected.url} controls playsInline style={{width:'100%',maxHeight:'68vh',display:'block',background:'#000'}} />
        <div style={{padding:16,color:'#a7aebb'}}>{selected.provider} {selected.quality ? `• ${selected.quality}` : ''} {selected.licenseLabel ? `• ${selected.licenseLabel}` : ''}</div>
      </section>}

      {sources.length>0 && <section style={{marginTop:28}}><h2>Source options</h2><div style={{display:'grid',gap:10}}>{sources.map((source)=><button key={source.id} onClick={()=>setSelected(source)} style={{textAlign:'left',padding:'16px 18px',borderRadius:14,border:selected?.id===source.id?'1px solid #8b5cf6':'1px solid #252c3b',background:selected?.id===source.id?'#201934':'#141924',color:'#f7f8fb',cursor:'pointer'}}>
        <strong>{source.provider}</strong><div style={{fontSize:13,color:'#a7aebb',marginTop:5}}>{[source.quality,source.direct?'Direct':null,source.licenseLabel].filter(Boolean).join(' • ')}</div>
      </button>)}</div></section>}
    </div>
  </main>;
}
