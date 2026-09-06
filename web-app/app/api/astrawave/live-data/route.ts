import { NextRequest, NextResponse } from 'next/server';

type Def={id:string;name:string;url:string};
type Raw={id:string;tvgId?:string;name:string;group?:string;logoUrl?:string;streamUrl:string;sourceId:string;sourceName:string};
type Program={title:string;start:number;stop:number;category?:string};

const core:Def[]=[
  {id:'astrawave-free',name:'AstraWave Free TV',url:'https://raw.githubusercontent.com/james-mull/astrawave-tv-build/feature/nuvio-core-rebuild/astrawave-free-tv/astrawave-free-tv.m3u'},
  {id:'nexus-us',name:'IPTV Nexus US',url:'https://dearbulut.github.io/iptv/playlists/country/us.m3u'},
  {id:'public-tv',name:'AstraWave Public TV',url:'https://raw.githubusercontent.com/freecasthub/public-iptv/main/playlist.m3u'},
  {id:'free-tv',name:'Free-TV Public',url:'https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8'},
  {id:'iptv-org-us',name:'IPTV.org Public',url:'https://iptv-org.github.io/iptv/countries/us.m3u'},
  {id:'iptv-org-sports',name:'IPTV.org Sports',url:'https://iptv-org.github.io/iptv/categories/sports.m3u'},
  {id:'world-verified',name:'World IPTV Verified',url:'https://romaxa55.github.io/world_ip_tv/output/index.m3u'},
];

const epgUrl=process.env.ASTRAWAVE_EPG_JSON_URL||'https://raw.githubusercontent.com/james-mull/astrawave-tv-build/feature/nuvio-core-rebuild/astrawave-epg/us-live-guide.json';

function parseM3u(text:string,def:Def){
  const out:Raw[]=[];const lines=text.split(/\r?\n/).map(x=>x.trim()).filter(Boolean);let pending='';
  for(const line of lines){
    if(line.startsWith('#EXTINF')) pending=line;
    else if(!line.startsWith('#')&&pending){
      const attr=(name:string)=>pending.match(new RegExp(`${name}="([^"]*)"`,'i'))?.[1];
      const tvgId=attr('tvg-id')||undefined;const name=pending.split(',').pop()?.trim()||'Channel';
      out.push({id:tvgId||`${def.id}:${out.length}`,tvgId,name,group:attr('group-title')||undefined,logoUrl:attr('tvg-logo')||undefined,streamUrl:line,sourceId:def.id,sourceName:def.name});pending='';
    }
  }
  return out;
}

function norm(value:string){return value.toLowerCase().replace(/\b(uhd|fhd|hd|sd|4k|hevc|h\.?26[45]|60fps|backup|alt|east|west|central|us|usa|stream)\b/g,' ').replace(/[^a-z0-9]+/g,' ').trim()}

async function load(def:Def){
  const response=await fetch(def.url,{next:{revalidate:300}});if(!response.ok)throw new Error(`${def.name} ${response.status}`);return parseM3u(await response.text(),def);
}

async function epg(){
  try{const response=await fetch(epgUrl,{next:{revalidate:300}});if(!response.ok)return null;return await response.json()}catch{return null}
}

function merge(rows:Raw[],guide:any){
  const groups=new Map<string,Raw[]>();
  for(const channel of rows){const key=channel.tvgId?`id:${channel.tvgId.toLowerCase()}`:`name:${norm(channel.name)||channel.id}`;groups.set(key,[...(groups.get(key)||[]),channel])}
  const titleToId=new Map<string,string>();
  for(const c of guide?.channels||[]) titleToId.set(norm(c.title||''),String(c.tvgId||c.id||''));
  const now=Date.now();
  return Array.from(groups.values()).map(items=>{
    const preferred=items.find(x=>x.tvgId)||items[0];const sources=items.map((x,index)=>({id:`${preferred.id}:${index}`,provider:x.sourceName,sourceId:x.sourceId,streamUrl:x.streamUrl,group:x.group}));
    const epgId=preferred.tvgId||titleToId.get(norm(preferred.name));const programs:Program[]=(epgId&&guide?.programs?.[epgId])||[];
    const current=programs.find(p=>p.start<=now&&p.stop>now);const next=programs.find(p=>p.start>now);
    return {id:preferred.tvgId||norm(preferred.name)||preferred.id,tvgId:preferred.tvgId,name:preferred.name,group:preferred.group||items.find(x=>x.group)?.group,logoUrl:preferred.logoUrl||items.find(x=>x.logoUrl)?.logoUrl,streamUrl:sources[0]?.streamUrl,sources,sourceCount:sources.length,now:current?.title,next:next?.title,programs};
  }).sort((a,b)=>a.name.localeCompare(b.name));
}

export async function GET(request:NextRequest){
  const requested=request.nextUrl.searchParams.get('source')||'all-free';
  const selected=requested==='all-free'?core:core.filter(x=>x.id===requested);
  const settled=await Promise.allSettled(selected.map(load));
  const rows=settled.flatMap(r=>r.status==='fulfilled'?r.value:[]);
  const guide=await epg();
  const channels=merge(rows,guide);
  return NextResponse.json({
    activeSource:requested,
    sourceOptions:[{id:'all-free',name:'All Free Sources'},...core.map(({id,name})=>({id,name}))],
    channels,
    stats:{channels:channels.length,epgLinked:channels.filter(x=>x.programs.length).length,epgGeneratedAt:guide?.generatedAt||null,epgScheduledChannels:guide?.scheduledChannels||0},
    failures:settled.flatMap((r,i)=>r.status==='rejected'?[{source:selected[i].name,error:String(r.reason)}]:[]),
  });
}
