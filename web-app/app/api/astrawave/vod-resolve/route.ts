import { NextRequest, NextResponse } from 'next/server';

export const dynamic='force-dynamic';

type ManifestInput={name?:string;url:string};
type ResolveBody={kind?:'movie'|'series'|'episode';tmdbId?:string;imdbId?:string;season?:number;episode?:number;manifests?:ManifestInput[]};
type SourceCandidate={id:string;provider:string;url:string;quality?:string;codec?:string;hdr?:string;bitrateKbps?:number;direct:boolean;licenseLabel:string};

type Manifest={id?:string;name?:string;resources?:Array<string|{name?:string;types?:string[];idPrefixes?:string[]}>;types?:string[]};
type Stream={url?:string;externalUrl?:string;ytId?:string;infoHash?:string;name?:string;title?:string;description?:string;behaviorHints?:Record<string,unknown>};

function isPrivateHost(hostname:string){
  const host=hostname.toLowerCase().replace(/^\[|\]$/g,'');
  if(host==='localhost'||host.endsWith('.localhost')||host.endsWith('.local'))return true;
  if(host==='0.0.0.0'||host==='::'||host==='::1')return true;
  if(/^127\./.test(host)||/^10\./.test(host)||/^192\.168\./.test(host))return true;
  const m=host.match(/^172\.(\d+)\./);if(m&&Number(m[1])>=16&&Number(m[1])<=31)return true;
  if(/^169\.254\./.test(host)||/^100\.(6[4-9]|[7-9]\d|1[01]\d|12[0-7])\./.test(host))return true;
  if(/^fc/i.test(host)||/^fd/i.test(host)||/^fe[89ab]/i.test(host))return true;
  return false;
}

function safeRemoteUrl(value:string){
  try{const u=new URL(value);return (u.protocol==='https:'||u.protocol==='http:')&&!isPrivateHost(u.hostname)?u:null}catch{return null}
}

function manifestUrl(value:string){
  const u=safeRemoteUrl(value);if(!u)return null;
  if(!u.pathname.endsWith('/manifest.json')){
    u.pathname=u.pathname.replace(/\/$/,'')+'/manifest.json';u.search='';u.hash='';
  }
  return u;
}

function supportsStream(manifest:Manifest,type:'movie'|'series'){
  if(Array.isArray(manifest.types)&&manifest.types.length&&!manifest.types.includes(type))return false;
  return (manifest.resources||[]).some(resource=>{
    if(resource==='stream')return true;
    if(typeof resource!=='object'||resource?.name!=='stream')return false;
    return !resource.types?.length||resource.types.includes(type);
  });
}

function infer(text:string){
  const t=text.toLowerCase();
  const quality=/\b(2160p|4k|uhd)\b/i.test(text)?'2160p':/\b1080p\b/i.test(text)?'1080p':/\b720p\b/i.test(text)?'720p':/\b480p\b/i.test(text)?'480p':undefined;
  const codec=/\b(av1)\b/i.test(text)?'AV1':/\b(hevc|h\.?265|x265)\b/i.test(text)?'HEVC':/\b(h\.?264|x264|avc)\b/i.test(text)?'H.264':undefined;
  const hdr=/dolby[ .-]?vision|\bdv\b/i.test(text)?'Dolby Vision':/hdr10\+?/i.test(text)?'HDR10':/\bhdr\b/i.test(text)?'HDR':undefined;
  const bitrateMatch=t.match(/(\d+(?:\.\d+)?)\s*(mbps|mb\/s|kbps|kb\/s)/i);
  const bitrateKbps=bitrateMatch?Math.round(Number(bitrateMatch[1])*(bitrateMatch[2].toLowerCase().startsWith('m')?1000:1)):undefined;
  return{quality,codec,hdr,bitrateKbps};
}

function streamEndpoint(manifest:URL,type:'movie'|'series',id:string){
  const base=new URL(manifest.toString());
  base.pathname=base.pathname.replace(/\/manifest\.json$/,'')+`/stream/${type}/${id}.json`;
  base.search='';base.hash='';return base;
}

async function resolveManifest(input:ManifestInput,type:'movie'|'series',stremioId:string){
  const manifest=manifestUrl(input.url);if(!manifest)return{provider:input.name||'Stremio',sources:[] as SourceCandidate[],error:'Blocked or invalid manifest URL'};
  try{
    const manifestResponse=await fetch(manifest,{cache:'no-store',redirect:'error',signal:AbortSignal.timeout(8000)});
    if(!manifestResponse.ok)return{provider:input.name||manifest.hostname,sources:[] as SourceCandidate[],error:`Manifest ${manifestResponse.status}`};
    const body=await manifestResponse.json() as Manifest;
    const provider=String(input.name||body.name||body.id||manifest.hostname).slice(0,100);
    if(!supportsStream(body,type))return{provider,sources:[] as SourceCandidate[],error:`Addon does not advertise ${type} stream resources`};
    const endpoint=streamEndpoint(manifest,type,stremioId);
    const response=await fetch(endpoint,{cache:'no-store',redirect:'error',signal:AbortSignal.timeout(12000)});
    if(!response.ok)return{provider,sources:[] as SourceCandidate[],error:`Stream resource ${response.status}`};
    const payload=await response.json() as {streams?:Stream[]};
    const sources=(payload.streams||[]).flatMap((stream,index)=>{
      const direct=safeRemoteUrl(String(stream.url||''));
      if(!direct)return[];
      const label=[stream.name,stream.title,stream.description].filter(Boolean).join(' • ');
      const parsed=infer(label);
      return[{id:`stremio:${body.id||provider}:${index}`,provider,url:direct.toString(),...parsed,direct:true,licenseLabel:'User-enabled Stremio addon'}];
    });
    return{provider,sources,error:null};
  }catch(error){return{provider:input.name||manifest.hostname,sources:[] as SourceCandidate[],error:error instanceof Error?error.message:'Addon request failed'}
}

export async function POST(request:NextRequest){
  try{
    const body=await request.json() as ResolveBody;
    const kind=body.kind;
    const imdbId=String(body.imdbId||'').trim();
    if(!kind||!['movie','series','episode'].includes(kind))return NextResponse.json({error:'Invalid VOD kind'},{status:400});
    if(!/^tt\d+$/i.test(imdbId))return NextResponse.json({error:'IMDb identity is required for addon resolution'},{status:400});
    const manifests=(body.manifests||[]).filter(x=>x&&typeof x.url==='string').slice(0,12);
    const type: 'movie'|'series'=kind==='movie'?'movie':'series';
    let stremioId=imdbId;
    if(kind==='episode'){
      const season=Number(body.season),episode=Number(body.episode);
      if(!Number.isInteger(season)||season<0||!Number.isInteger(episode)||episode<1)return NextResponse.json({error:'Valid season and episode are required'},{status:400});
      stremioId=`${imdbId}:${season}:${episode}`;
    }
    const settled=await Promise.all(manifests.map(input=>resolveManifest(input,type,stremioId)));
    const seen=new Set<string>();
    const sources=settled.flatMap(x=>x.sources).filter(source=>{if(seen.has(source.url))return false;seen.add(source.url);return true}).slice(0,80);
    return NextResponse.json({kind,tmdbId:body.tmdbId||null,imdbId,season:body.season||null,episode:body.episode||null,sources,diagnostics:settled.map(x=>({provider:x.provider,count:x.sources.length,error:x.error})),policy:'Only direct HTTP(S) streams returned by user-enabled Stremio addons are admitted here. Redirects, local/private hosts, torrent/info-hash results and non-direct entries are intentionally excluded.'});
  }catch(error){console.error('AstraWave VOD resolver error',error);return NextResponse.json({error:'VOD resolution failed'},{status:500})}
}
