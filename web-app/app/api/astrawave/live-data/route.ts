import { NextRequest, NextResponse } from 'next/server';

type Def={id:string;name:string;url:string};
type HealthMeta={rank?:number;healthScore?:number;uptimePercent?:number;latencyMs?:number;quality?:string};
type Raw={id:string;tvgId?:string;name:string;group?:string;logoUrl?:string;streamUrl:string;sourceId:string;sourceName:string;sourceIndex:number;channelIndex:number;health?:HealthMeta};
type Program={title:string;start:number;stop:number;category?:string};
type GuideIndex={channels?:Array<{tvgId?:string;id?:string;title?:string}>;programs?:Record<string,Program[]>;generatedAt?:string;scheduledChannels?:number};
type NexusStream={url?:string;quality?:string|null;referrer?:string|null;user_agent?:string|null;rank?:number;health?:{status?:string;score?:number;uptime?:number;latency_ms?:number}};
type NexusChannel={id?:string;online?:boolean;score?:number;streams?:NexusStream[]};

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
const nexusUsHealthUrl='https://dearbulut.github.io/iptv/api/v1/by-country/us.json';

function parseM3u(text:string,def:Def,sourceIndex:number){
  const out:Raw[]=[];const lines=text.split(/\r?\n/).map(x=>x.trim()).filter(Boolean);let pending='';
  for(const line of lines){
    if(line.startsWith('#EXTINF')) pending=line;
    else if(!line.startsWith('#')&&pending){
      const attr=(name:string)=>pending.match(new RegExp(`${name}="([^"]*)"`,'i'))?.[1];
      const tvgId=attr('tvg-id')||undefined;const name=pending.split(',').pop()?.trim()||'Channel';
      out.push({id:tvgId||`${def.id}:${out.length}`,tvgId,name,group:attr('group-title')||undefined,logoUrl:attr('tvg-logo')||undefined,streamUrl:line,sourceId:def.id,sourceName:def.name,sourceIndex,channelIndex:out.length});pending='';
    }
  }
  return out;
}

function norm(value:string){return value.toLowerCase().replace(/\b(uhd|fhd|hd|sd|4k|hevc|h\.?26[45]|60fps|backup|alt|east|west|central|us|usa|stream)\b/g,' ').replace(/[^a-z0-9]+/g,' ').trim()}

async function load(def:Def,sourceIndex:number){
  const response=await fetch(def.url,{next:{revalidate:300}});if(!response.ok)throw new Error(`${def.name} ${response.status}`);return parseM3u(await response.text(),def,sourceIndex);
}

async function nexusHealth():Promise<Map<string,HealthMeta>>{
  const out=new Map<string,HealthMeta>();
  try{
    const response=await fetch(nexusUsHealthUrl,{next:{revalidate:300}});if(!response.ok)return out;
    const body=await response.json() as NexusChannel[];
    for(const channel of Array.isArray(body)?body:[]){
      if(channel.online===false)continue;
      for(const stream of channel.streams||[]){
        if(!stream.url||stream.health?.status!=='online'||stream.referrer||stream.user_agent)continue;
        out.set(stream.url,{
          rank:stream.rank,
          healthScore:stream.health?.score??channel.score,
          uptimePercent:stream.health?.uptime,
          latencyMs:stream.health?.latency_ms,
          quality:stream.quality||undefined,
        });
      }
    }
  }catch{}
  return out;
}

async function epg():Promise<GuideIndex|null>{
  try{const response=await fetch(epgUrl,{next:{revalidate:300}});if(!response.ok)return null;return await response.json()}catch{return null}
}

function guideMaps(guide:GuideIndex|null){
  const titleToId=new Map<string,string>();
  for(const c of guide?.channels||[]) titleToId.set(norm(c.title||''),String(c.tvgId||c.id||''));
  return titleToId;
}

function programsFor(channel:{tvgId?:string;name:string},guide:GuideIndex|null,titleToId:Map<string,string>){
  const epgId=channel.tvgId||titleToId.get(norm(channel.name));
  return (epgId&&guide?.programs?.[epgId])||[];
}

function decoratePrograms(programs:Program[]){
  const now=Date.now();const current=programs.find(p=>p.start<=now&&p.stop>now);const next=programs.find(p=>p.start>now);
  return {programs,now:current?.title,next:next?.title};
}

function providerOrder(rows:Raw[],guide:GuideIndex|null){
  const titleToId=guideMaps(guide);
  return rows.map((row,index)=>{
    const schedule=decoratePrograms(programsFor(row,guide,titleToId));
    const source={id:`${row.sourceId}:${row.channelIndex}`,provider:row.sourceName,sourceId:row.sourceId,streamUrl:row.streamUrl,group:row.group};
    return {id:`raw:${row.sourceId}:${row.channelIndex}:${index}`,tvgId:row.tvgId,name:row.name,group:row.group,logoUrl:row.logoUrl,streamUrl:row.streamUrl,sources:[source],sourceCount:1,provider:row.sourceName,providerOrder:index,...schedule};
  });
}

function sourceComparator(a:Raw,b:Raw){
  const ah=a.health;const bh=b.health;
  return (bh?.rank||bh?.healthScore||0)-(ah?.rank||ah?.healthScore||0)
    ||(bh?.uptimePercent||0)-(ah?.uptimePercent||0)
    ||(ah?.latencyMs??999999)-(bh?.latencyMs??999999)
    ||a.sourceIndex-b.sourceIndex
    ||a.channelIndex-b.channelIndex;
}

function merge(rows:Raw[],guide:GuideIndex|null){
  const groups=new Map<string,Raw[]>();
  for(const channel of rows){const key=channel.tvgId?`id:${channel.tvgId.toLowerCase()}`:`name:${norm(channel.name)||channel.id}`;groups.set(key,[...(groups.get(key)||[]),channel])}
  const titleToId=guideMaps(guide);
  return Array.from(groups.values()).map((items,groupIndex)=>{
    const ordered=[...items].sort(sourceComparator);
    const preferred=ordered.find(x=>x.tvgId)||ordered[0];
    const sources=ordered.map((x,index)=>({
      id:`${preferred.id}:${index}`,
      provider:x.sourceName,
      sourceId:x.sourceId,
      streamUrl:x.streamUrl,
      group:x.group,
      quality:x.health?.quality,
      healthScore:x.health?.healthScore,
      uptimePercent:x.health?.uptimePercent,
      latencyMs:x.health?.latencyMs,
    }));
    const schedule=decoratePrograms(programsFor(preferred,guide,titleToId));
    return {id:preferred.tvgId||norm(preferred.name)||preferred.id,tvgId:preferred.tvgId,name:preferred.name,group:preferred.group||ordered.find(x=>x.group)?.group,logoUrl:preferred.logoUrl||ordered.find(x=>x.logoUrl)?.logoUrl,streamUrl:sources[0]?.streamUrl,sources,sourceCount:sources.length,provider:sources[0]?.provider||preferred.sourceName,providerOrder:groupIndex,healthScore:sources[0]?.healthScore,uptimePercent:sources[0]?.uptimePercent,latencyMs:sources[0]?.latencyMs,...schedule};
  }).sort((a,b)=>(b.healthScore||0)-(a.healthScore||0)||a.name.localeCompare(b.name));
}

export async function GET(request:NextRequest){
  const requested=request.nextUrl.searchParams.get('source')||'all-free';
  const mode=request.nextUrl.searchParams.get('mode')==='provider'?'provider':'managed';
  const selected=requested==='all-free'?core:core.filter(x=>x.id===requested);
  const wantsNexusHealth=mode==='managed'&&selected.some(x=>x.id==='nexus-us');
  const [settled,guide,healthMap]=await Promise.all([
    Promise.allSettled(selected.map((def,index)=>load(def,index))),
    epg(),
    wantsNexusHealth?nexusHealth():Promise.resolve(new Map<string,HealthMeta>()),
  ]);
  const rows=settled.flatMap(r=>r.status==='fulfilled'?r.value:[]).map(row=>row.sourceId==='nexus-us'&&healthMap.has(row.streamUrl)?{...row,health:healthMap.get(row.streamUrl)}:row);
  const managedChannels=merge(rows,guide);
  const channels=mode==='provider'?providerOrder(rows,guide):managedChannels;
  return NextResponse.json({
    activeSource:requested,
    mode,
    sourceOptions:[{id:'all-free',name:'All Free Sources'},...core.map(({id,name})=>({id,name}))],
    channels,
    stats:{channels:channels.length,rawChannels:rows.length,mergedChannels:managedChannels.length,epgLinked:channels.filter(x=>x.programs.length).length,healthRankedSources:rows.filter(x=>x.health?.healthScore!==undefined).length,epgGeneratedAt:guide?.generatedAt||null,epgScheduledChannels:guide?.scheduledChannels||0},
    failures:settled.flatMap((r,i)=>r.status==='rejected'?[{source:selected[i].name,error:String(r.reason)}]:[]),
    policy:'Unified mode consolidates duplicate public channels and uses fresh IPTV Nexus health telemetry when available. Provider Order is a non-destructive fallback that preserves upstream playlist order exactly; customer-authorized providers can use the same contract.',
  });
}
