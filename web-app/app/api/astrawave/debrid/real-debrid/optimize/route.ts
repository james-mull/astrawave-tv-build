import { NextRequest, NextResponse } from 'next/server';

export const dynamic='force-dynamic';

type Candidate={id:string;provider:string;url?:string;quality?:string;codec?:string;hdr?:string;bitrateKbps?:number;latencyMs?:number;uptimePercent?:number;direct?:boolean;licenseLabel?:string};

function safeHttpUrl(value:string){
  try{const url=new URL(value);return url.protocol==='https:'||url.protocol==='http:'?url:null}catch{return null}
}

function inferQuality(label:string){
  if(/2160|4k|uhd/i.test(label))return'2160p';
  if(/1440/i.test(label))return'1440p';
  if(/1080|fhd/i.test(label))return'1080p';
  if(/720|\bhd\b/i.test(label))return'720p';
  if(/480/i.test(label))return'480p';
  return undefined;
}

async function optimizeOne(accessToken:string,source:Candidate){
  const original=safeHttpUrl(String(source.url||''));if(!original)return source;
  try{
    const response=await fetch('https://api.real-debrid.com/rest/1.0/unrestrict/link',{
      method:'POST',cache:'no-store',signal:AbortSignal.timeout(10000),headers:{Authorization:`Bearer ${accessToken}`,'content-type':'application/x-www-form-urlencoded',accept:'application/json'},
      body:new URLSearchParams({link:original.toString()}),
    });
    if(!response.ok)return source;
    const body=await response.json() as {download?:string;filename?:string;mimeType?:string};
    const optimized=safeHttpUrl(String(body.download||''));if(!optimized||optimized.toString()===original.toString())return source;
    return{...source,id:`rd:${source.id}`,provider:`Real-Debrid • ${source.provider}`,url:optimized.toString(),quality:inferQuality(body.filename||'')||source.quality,direct:true,licenseLabel:'User-linked debrid optimization'} as Candidate;
  }catch{return source}
}

export async function POST(request:NextRequest){
  try{
    const body=await request.json() as {accessToken?:string;sources?:Candidate[]};
    const accessToken=String(body.accessToken||'').trim();
    if(!accessToken)return NextResponse.json({error:'Missing access token'},{status:400});
    const sources=(body.sources||[]).filter(source=>source&&typeof source==='object').slice(0,8);
    const optimized=await Promise.all(sources.map(source=>optimizeOne(accessToken,source)));
    const seen=new Set<string>();
    const deduped=optimized.filter(source=>{const key=String(source.url||source.id);if(!key||seen.has(key))return false;seen.add(key);return true});
    return NextResponse.json({sources:deduped,optimizedCount:deduped.filter(source=>String(source.provider).startsWith('Real-Debrid •')).length,policy:'AstraWave only sends already-accepted user-authorized playback candidates to the linked Real-Debrid account. This endpoint does not discover titles, torrents, or files.'});
  }catch(error){console.error('Real-Debrid optimize error',error);return NextResponse.json({error:'Debrid optimization failed'},{status:500})}
}
