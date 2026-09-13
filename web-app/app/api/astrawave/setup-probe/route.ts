import { NextRequest, NextResponse } from 'next/server';

type ProbeKind='stremio'|'cloudstream'|'m3u';

function safePublicUrl(raw:string){
  try{
    const url=new URL(raw.trim());
    if(!['http:','https:'].includes(url.protocol))return null;
    const host=url.hostname.toLowerCase();
    if(host==='localhost'||host==='127.0.0.1'||host==='::1'||host.endsWith('.local')||host.startsWith('10.')||host.startsWith('192.168.')||host.startsWith('169.254.'))return null;
    const m=host.match(/^172\.(\d+)\./);if(m&&Number(m[1])>=16&&Number(m[1])<=31)return null;
    return url;
  }catch{return null}
}

export async function GET(request:NextRequest){
  const kind=(request.nextUrl.searchParams.get('kind')||'') as ProbeKind;
  const raw=request.nextUrl.searchParams.get('url')||'';
  if(!['stremio','cloudstream','m3u'].includes(kind))return NextResponse.json({ok:false,error:'Unsupported setup type'},{status:400});
  const url=safePublicUrl(raw);if(!url)return NextResponse.json({ok:false,error:'Use a public http(s) URL. Local/private server addresses are saved without remote probing.'},{status:400});
  try{
    const response=await fetch(url,{cache:'no-store',redirect:'follow',headers:{'User-Agent':'AstraWave-Setup/1.0'}});
    if(!response.ok)return NextResponse.json({ok:false,error:`Source returned HTTP ${response.status}`},{status:200});
    const text=await response.text();
    if(kind==='m3u'){
      const channelCount=(text.match(/#EXTINF/gi)||[]).length;
      const valid=text.includes('#EXTM3U')||channelCount>0;
      return NextResponse.json({ok:valid,kind,channelCount,summary:valid?`${channelCount} playlist entries found`:'This does not look like an M3U playlist'});
    }
    let json:any;try{json=JSON.parse(text)}catch{return NextResponse.json({ok:false,error:'Source did not return valid JSON'})}
    if(kind==='stremio'){
      const valid=Boolean(json?.id&&json?.name&&Array.isArray(json?.resources));
      return NextResponse.json({ok:valid,kind,name:json?.name||null,id:json?.id||null,resources:Array.isArray(json?.resources)?json.resources.length:0,catalogs:Array.isArray(json?.catalogs)?json.catalogs.length:0,summary:valid?`${json.name} • ${Array.isArray(json.catalogs)?json.catalogs.length:0} catalogs`:'This does not look like a Stremio manifest'});
    }
    const rows=Array.isArray(json)?json:Array.isArray(json?.plugins)?json.plugins:Array.isArray(json?.repos)?json.repos:[];
    const valid=Array.isArray(rows)||Boolean(json?.name||json?.id);
    return NextResponse.json({ok:valid,kind,name:json?.name||json?.id||null,entries:Array.isArray(rows)?rows.length:0,summary:valid?`${json?.name||'CloudStream repository'} • ${Array.isArray(rows)?rows.length:0} entries`:'This does not look like a CloudStream repository'});
  }catch(error){return NextResponse.json({ok:false,error:error instanceof Error?error.message:'Could not reach source'})}
}
