import { NextRequest, NextResponse } from 'next/server';

export const dynamic = 'force-dynamic';

type ChannelSource={
  provider:string;
  sourceId:string;
  streamUrl:string;
  group?:string;
  quality?:string;
  healthScore?:number;
  uptimePercent?:number;
  latencyMs?:number;
};
type Channel={id:string;tvgId?:string;name:string;group?:string;logoUrl?:string;sources:ChannelSource[];sourceCount:number;healthScore?:number;online?:boolean};
type SportsEvent={id:string;league:string;sport?:string;title:string;startTime:string;status:string;homeTeam?:string;awayTeam?:string;badge?:string;broadcasts:string[];source:string};
type NexusStream={
  url?:string;
  quality?:string|null;
  referrer?:string|null;
  user_agent?:string|null;
  rank?:number;
  health?:{status?:string;score?:number;uptime?:number;latency_ms?:number};
};
type NexusChannel={
  id?:string;
  name?:string;
  alt_names?:string[];
  network?:string|null;
  categories?:string[];
  logo?:string|null;
  score?:number;
  online?:boolean;
  streams?:NexusStream[];
};

const channelFeeds=[
  ['astrawave-free','AstraWave Free TV','https://raw.githubusercontent.com/james-mull/astrawave-tv-build/feature/nuvio-core-rebuild/astrawave-free-tv/astrawave-free-tv.m3u'],
  ['nexus-us','IPTV Nexus US','https://dearbulut.github.io/iptv/playlists/country/us.m3u'],
  ['public-tv','AstraWave Public TV','https://raw.githubusercontent.com/freecasthub/public-iptv/main/playlist.m3u'],
  ['free-tv','Free-TV Public','https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8'],
  ['iptv-org-us','IPTV.org US','https://iptv-org.github.io/iptv/countries/us.m3u'],
  ['iptv-org-sports','IPTV.org Sports','https://iptv-org.github.io/iptv/categories/sports.m3u'],
  ['world-verified','World IPTV Verified','https://romaxa55.github.io/world_ip_tv/output/index.m3u'],
] as const;

const nexusSportsUrl='https://dearbulut.github.io/iptv/api/v1/by-category/sports.json';

const espnLeagues=[
  ['football','nfl','NFL'],['football','college-football','NCAA Football'],
  ['basketball','nba','NBA'],['basketball','wnba','WNBA'],['basketball','mens-college-basketball','NCAA Basketball'],['basketball','womens-college-basketball','NCAA Women'],
  ['baseball','mlb','MLB'],['baseball','college-baseball','NCAA Baseball'],['hockey','nhl','NHL'],
  ['soccer','usa.1','MLS'],['soccer','usa.nwsl','NWSL'],['soccer','eng.1','Premier League'],['soccer','esp.1','LaLiga'],['soccer','ger.1','Bundesliga'],['soccer','ita.1','Serie A'],['soccer','fra.1','Ligue 1'],
  ['soccer','uefa.champions','UEFA Champions League'],['soccer','uefa.europa','UEFA Europa League'],['soccer','mex.1','Liga MX'],['soccer','bra.1','Brazil Serie A'],['soccer','arg.1','Argentina Liga'],
  ['tennis','atp','ATP Tennis'],['tennis','wta','WTA Tennis'],['golf','pga','PGA Golf'],['racing','f1','Formula 1'],['mma','ufc','UFC'],
] as const;

function iso(offset=0){const d=new Date();d.setUTCDate(d.getUTCDate()+offset);return d.toISOString().slice(0,10)}
function ymd(date:string){return date.replaceAll('-','')}
function norm(value:string){
  return value.toLowerCase()
    .replace(/espn\s*\+/g,'espnplus')
    .replace(/paramount\s*\+/g,'paramountplus')
    .replace(/\b(uhd|fhd|hd|sd|4k|hevc|h\.?26[45]|60fps|backup|alt|east|west|central|network|channel|tv|us|usa|feed)\b/g,' ')
    .replace(/[^a-z0-9]+/g,' ')
    .trim()
}
function aliases(value:string){
  const n=norm(value);const out=new Set([n]);
  const swaps:Record<string,string[]>= {
    'espn':['espn','espn1'], 'espn 2':['espn2'], 'espn2':['espn 2'], 'espn u':['espnu'], 'espnu':['espn u'], 'espn news':['espnews'], 'espnews':['espn news'],
    'espnplus':['espnplus'], 'espn deportes':['espn deportes'],
    'abc':['abc'], 'cbs':['cbs'], 'nbc':['nbc'], 'fox':['fox'], 'cw':['the cw'], 'the cw':['cw'], 'tnt':['tnt sports'], 'tbs':['tbs'], 'tru tv':['trutv'], 'trutv':['tru tv'],
    'fox sports 1':['fs1','fox sports 1'], 'fs1':['fox sports 1'], 'fox sports 2':['fs2','fox sports 2'], 'fs2':['fox sports 2'], 'fox deportes':['fox deportes'],
    'cbs sports':['cbs sports network','cbssn'], 'cbs sports network':['cbs sports','cbssn'], 'cbssn':['cbs sports network'], 'cbs sports golazo':['golazo','cbs sports golazo'], 'golazo':['cbs sports golazo'],
    'nbc sports':['nbc sports'], 'sec':['sec network'], 'acc':['acc network','accn'], 'accn':['acc network'], 'big ten':['big ten network','btn'], 'btn':['big ten network'],
    'nfl':['nfl network'], 'nba':['nba tv'], 'mlb':['mlb network'], 'nhl':['nhl network'], 'golf':['golf channel'],
    'usa':['usa network'], 'peacock':['peacock'], 'amazon prime':['prime video'], 'prime video':['amazon prime'], 'paramountplus':['paramountplus'],
    'telemundo':['telemundo'], 'univision':['univision'], 'tudn':['tudn'],
  };
  for(const [key,vals] of Object.entries(swaps)) if(n===key) vals.forEach(x=>out.add(norm(x)));
  return [...out].filter(Boolean);
}

function parseM3u(text:string,sourceId:string,provider:string){
  const rows:{id:string;tvgId?:string;name:string;group?:string;logoUrl?:string;streamUrl:string;sourceId:string;provider:string}[]=[];
  const lines=text.split(/\r?\n/).map(x=>x.trim()).filter(Boolean);let ext='';
  for(const line of lines){
    if(line.startsWith('#EXTINF'))ext=line;
    else if(!line.startsWith('#')&&ext){
      const attr=(name:string)=>ext.match(new RegExp(`${name}="([^"]*)"`,'i'))?.[1];
      const tvgId=attr('tvg-id')||undefined;const name=ext.split(',').pop()?.trim()||'Channel';
      rows.push({id:tvgId||`${sourceId}:${rows.length}`,tvgId,name,group:attr('group-title')||undefined,logoUrl:attr('tvg-logo')||undefined,streamUrl:line,sourceId,provider});ext='';
    }
  }
  return rows;
}

async function nexusSports():Promise<Channel[]> {
  try{
    const response=await fetch(nexusSportsUrl,{next:{revalidate:300}});if(!response.ok)return[];
    const body=await response.json() as NexusChannel[];
    return (Array.isArray(body)?body:[]).flatMap(channel=>{
      if(!channel.id||!channel.name||channel.online===false)return[];
      const sources=(channel.streams||[])
        .filter(stream=>stream.url&&stream.health?.status==='online'&&!stream.referrer&&!stream.user_agent)
        .sort((a,b)=>(b.rank||b.health?.score||0)-(a.rank||a.health?.score||0))
        .map((stream,index)=>({
          provider:'IPTV Nexus Healthy',sourceId:`nexus-sports:${channel.id}:${index}`,streamUrl:String(stream.url),group:'Sports',quality:stream.quality||undefined,
          healthScore:stream.health?.score,uptimePercent:stream.health?.uptime,latencyMs:stream.health?.latency_ms,
        }));
      if(!sources.length)return[];
      return [{id:channel.id,tvgId:channel.id,name:channel.name,group:'Sports',logoUrl:channel.logo||undefined,sources,sourceCount:sources.length,healthScore:channel.score,online:true}];
    });
  }catch{return[]}
}

async function channels():Promise<{channels:Channel[];failures:string[]}> {
  const [healthySports,settled]=await Promise.all([
    nexusSports(),
    Promise.allSettled(channelFeeds.map(async([id,name,url])=>{
      const r=await fetch(url,{next:{revalidate:300}});if(!r.ok)throw new Error(`${name} ${r.status}`);return parseM3u(await r.text(),id,name);
    })),
  ]);
  const rows=settled.flatMap(x=>x.status==='fulfilled'?x.value:[]);
  const grouped=new Map<string,typeof rows>();
  for(const row of rows){const key=row.tvgId?`id:${row.tvgId.toLowerCase()}`:`name:${norm(row.name)||row.id}`;grouped.set(key,[...(grouped.get(key)||[]),row])}
  const fallback=Array.from(grouped.values()).map(group=>{
    const preferred=group.find(x=>x.tvgId)||group[0];
    const sources=group.map(x=>({provider:x.provider,sourceId:x.sourceId,streamUrl:x.streamUrl,group:x.group}));
    return {id:preferred.tvgId||norm(preferred.name)||preferred.id,tvgId:preferred.tvgId,name:preferred.name,group:preferred.group||group.find(x=>x.group)?.group,logoUrl:preferred.logoUrl||group.find(x=>x.logoUrl)?.logoUrl,sources,sourceCount:sources.length} as Channel;
  });
  const combined=new Map<string,Channel>();
  for(const channel of [...healthySports,...fallback]){
    const key=channel.tvgId?`id:${channel.tvgId.toLowerCase()}`:`name:${norm(channel.name)||channel.id}`;
    const prior=combined.get(key);
    if(!prior){combined.set(key,channel);continue}
    const sources=[...prior.sources,...channel.sources].filter((source,index,array)=>array.findIndex(x=>x.streamUrl===source.streamUrl)===index)
      .sort((a,b)=>(b.healthScore||0)-(a.healthScore||0)||(b.uptimePercent||0)-(a.uptimePercent||0)||(a.latencyMs||999999)-(b.latencyMs||999999));
    combined.set(key,{...prior,logoUrl:prior.logoUrl||channel.logoUrl,group:prior.group||channel.group,sources,sourceCount:sources.length,healthScore:Math.max(prior.healthScore||0,channel.healthScore||0),online:prior.online||channel.online});
  }
  const list=[...combined.values()].sort((a,b)=>(b.healthScore||0)-(a.healthScore||0)||a.name.localeCompare(b.name));
  return {channels:list,failures:settled.flatMap((x,i)=>x.status==='rejected'?[`${channelFeeds[i][1]}: ${String(x.reason)}`]:[])};
}

async function sportsDb(date:string):Promise<SportsEvent[]> {
  const key=process.env.THESPORTSDB_API_KEY||'123';
  try{
    const r=await fetch(`https://www.thesportsdb.com/api/v1/json/${key}/eventsday.php?d=${date}`,{next:{revalidate:180}});if(!r.ok)return[];
    const body=await r.json();return (body.events||[]).map((e:any)=>({
      id:`tsdb:${e.idEvent||`${date}:${e.strEvent}`}`,league:e.strLeague||e.strSport||'Sports',sport:e.strSport||undefined,title:e.strEvent||'Event',
      startTime:`${e.dateEvent||date}T${e.strTime||'00:00:00'}`,status:e.strStatus||'scheduled',homeTeam:e.strHomeTeam||undefined,awayTeam:e.strAwayTeam||undefined,
      badge:e.strThumb||e.strPoster||undefined,broadcasts:String(e.strTVStation||'').split(/[/,;|]/).map((x:string)=>x.trim()).filter(Boolean),source:'TheSportsDB'
    }));
  }catch{return[]}
}

async function espn(date:string):Promise<SportsEvent[]> {
  const all=await Promise.allSettled(espnLeagues.map(async([sport,league,label])=>{
    const r=await fetch(`https://site.api.espn.com/apis/site/v2/sports/${sport}/${league}/scoreboard?dates=${ymd(date)}`,{next:{revalidate:120}});if(!r.ok)return[];
    const body=await r.json();return (body.events||[]).map((e:any)=>{
      const c=e.competitions?.[0]||{};const comps=c.competitors||[];const home=comps.find((x:any)=>x.homeAway==='home');const away=comps.find((x:any)=>x.homeAway==='away');
      const broadcasts=(c.broadcasts||[]).flatMap((b:any)=>b.names||[]).map((x:any)=>String(x).trim()).filter(Boolean);
      return {id:`espn:${sport}:${league}:${e.id}`,league:label,sport,title:e.name||e.shortName||'Event',startTime:e.date||`${date}T00:00:00Z`,status:e.status?.type?.description||e.status?.type?.state||'scheduled',homeTeam:home?.team?.displayName,awayTeam:away?.team?.displayName,badge:e.competitions?.[0]?.competitors?.[0]?.team?.logo,broadcasts,source:'ESPN'} as SportsEvent;
    });
  }));
  return all.flatMap(x=>x.status==='fulfilled'?x.value:[]);
}

function sameEvent(a:SportsEvent,b:SportsEvent){
  const teamsA=[norm(a.homeTeam||''),norm(a.awayTeam||'')].filter(Boolean).sort().join('|');
  const teamsB=[norm(b.homeTeam||''),norm(b.awayTeam||'')].filter(Boolean).sort().join('|');
  return Boolean((teamsA&&teamsA===teamsB) || norm(a.title)===norm(b.title));
}

function mergeEvents(primary:SportsEvent[],enrichment:SportsEvent[]){
  const out=[...primary];
  for(const event of enrichment){
    const match=out.find(x=>sameEvent(x,event));
    if(match){match.broadcasts=[...new Set([...match.broadcasts,...event.broadcasts])];if(!match.badge)match.badge=event.badge;if(match.status==='scheduled'&&event.status)match.status=event.status}
    else out.push(event);
  }
  return out.sort((a,b)=>a.startTime.localeCompare(b.startTime));
}

function matchChannels(event:SportsEvent,list:Channel[]){
  const wanted=[...new Set(event.broadcasts.flatMap(aliases))];
  if(!wanted.length)return[];
  return list.map(channel=>{
    const names=[channel.name,...aliases(channel.name)];let score=0;let reason='';
    for(const w of wanted)for(const raw of names){const n=norm(raw);
      if(w===n){score=Math.max(score,100);reason='exact broadcaster match'}
      else if(w.length>=3&&n.length>=3&&(w.includes(n)||n.includes(w))){score=Math.max(score,82);reason='broadcaster alias match'}
    }
    if(/sports/i.test(channel.group||'')&&score>0)score+=3;
    if(channel.online&&score>0)score+=2;
    if((channel.healthScore||0)>=80&&score>0)score+=2;
    return {channel,score,reason};
  }).filter(x=>x.score>0).sort((a,b)=>b.score-a.score||(b.channel.healthScore||0)-(a.channel.healthScore||0)||b.channel.sourceCount-a.channel.sourceCount).slice(0,8).map(x=>({
    id:x.channel.id,name:x.channel.name,logoUrl:x.channel.logoUrl,group:x.channel.group,matchScore:x.score,matchReason:x.reason,sourceCount:x.channel.sourceCount,healthScore:x.channel.healthScore,candidates:x.channel.sources
  }));
}

export async function GET(request:NextRequest){
  const date=request.nextUrl.searchParams.get('date')||iso(0);
  const horizon=Math.min(Math.max(Number(request.nextUrl.searchParams.get('days')||3),1),7);
  const dates=Array.from({length:horizon},(_,i)=>{const d=new Date(`${date}T00:00:00Z`);d.setUTCDate(d.getUTCDate()+i);return d.toISOString().slice(0,10)});
  const [{channels:channelList,failures},eventDays]=await Promise.all([
    channels(),
    Promise.all(dates.map(async d=>mergeEvents(await sportsDb(d),await espn(d))))
  ]);
  const events=eventDays.flat().map(event=>{
    const matches=matchChannels(event,channelList);return {...event,channelMatches:matches,watchable:matches.some(x=>x.candidates.length>0)};
  });
  const healthyChannels=channelList.filter(channel=>channel.online||(channel.healthScore||0)>0).length;
  return NextResponse.json({
    generatedAt:new Date().toISOString(),dates,
    stats:{events:events.length,eventsWithBroadcasts:events.filter(x=>x.broadcasts.length).length,eventsWithChannelMatches:events.filter(x=>x.channelMatches.length).length,channels:channelList.length,healthyChannels},
    events,channels:channelList,failures,
    policy:'Sports schedules and broadcaster metadata are aggregated separately from playback. Public/reviewed fresh-install candidates are health-ranked; customer-authorized sources remain eligible through the device resolver.'
  });
}
