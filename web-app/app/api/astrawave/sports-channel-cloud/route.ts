import { NextRequest, NextResponse } from 'next/server';

export const dynamic = 'force-dynamic';

type ChannelSource={provider:string;sourceId:string;streamUrl:string;group?:string};
type Channel={id:string;tvgId?:string;name:string;group?:string;logoUrl?:string;sources:ChannelSource[];sourceCount:number};
type SportsEvent={id:string;league:string;sport?:string;title:string;startTime:string;status:string;homeTeam?:string;awayTeam?:string;badge?:string;broadcasts:string[];source:string};

const channelFeeds=[
  ['astrawave-free','AstraWave Free TV','https://raw.githubusercontent.com/james-mull/astrawave-tv-build/feature/nuvio-core-rebuild/astrawave-free-tv/astrawave-free-tv.m3u'],
  ['nexus-us','IPTV Nexus US','https://dearbulut.github.io/iptv/playlists/country/us.m3u'],
  ['public-tv','AstraWave Public TV','https://raw.githubusercontent.com/freecasthub/public-iptv/main/playlist.m3u'],
  ['free-tv','Free-TV Public','https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8'],
  ['iptv-org-us','IPTV.org US','https://iptv-org.github.io/iptv/countries/us.m3u'],
  ['iptv-org-sports','IPTV.org Sports','https://iptv-org.github.io/iptv/categories/sports.m3u'],
  ['world-verified','World IPTV Verified','https://romaxa55.github.io/world_ip_tv/output/index.m3u'],
] as const;

const espnLeagues=[
  ['football','nfl','NFL'],['football','college-football','NCAA Football'],
  ['basketball','nba','NBA'],['basketball','wnba','WNBA'],['basketball','mens-college-basketball','NCAA Basketball'],
  ['baseball','mlb','MLB'],['hockey','nhl','NHL'],['soccer','usa.1','MLS'],
] as const;

function iso(offset=0){const d=new Date();d.setUTCDate(d.getUTCDate()+offset);return d.toISOString().slice(0,10)}
function ymd(date:string){return date.replaceAll('-','')}
function norm(value:string){return value.toLowerCase().replace(/\b(uhd|fhd|hd|sd|4k|hevc|h\.?26[45]|60fps|backup|alt|east|west|central|network|channel|tv|us|usa)\b/g,' ').replace(/[^a-z0-9]+/g,' ').trim()}
function aliases(value:string){
  const n=norm(value);const out=new Set([n]);
  const swaps:Record<string,string[]>= {
    'espn':['espn','espn1'], 'espn 2':['espn2'], 'espn2':['espn 2'], 'tnt':['tnt sports'], 'tbs':['tbs'],
    'fox sports 1':['fs1','fox sports 1'], 'fs1':['fox sports 1'], 'fox sports 2':['fs2','fox sports 2'], 'fs2':['fox sports 2'],
    'cbs sports':['cbs sports network','cbssn'], 'cbs sports network':['cbs sports','cbssn'], 'nbc sports':['nbc sports'],
    'nfl':['nfl network'], 'nfl network':['nfl'], 'nba':['nba tv'], 'nba tv':['nba'], 'mlb':['mlb network'], 'mlb network':['mlb'],
    'nhl':['nhl network'], 'nhl network':['nhl'], 'golf':['golf channel'], 'golf channel':['golf'],
  };
  for(const [key,vals] of Object.entries(swaps)) if(n===key||n.includes(key)) vals.forEach(x=>out.add(norm(x)));
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

async function channels():Promise<{channels:Channel[];failures:string[]}> {
  const settled=await Promise.allSettled(channelFeeds.map(async([id,name,url])=>{
    const r=await fetch(url,{next:{revalidate:300}});if(!r.ok)throw new Error(`${name} ${r.status}`);return parseM3u(await r.text(),id,name);
  }));
  const rows=settled.flatMap(x=>x.status==='fulfilled'?x.value:[]);
  const grouped=new Map<string,typeof rows>();
  for(const row of rows){const key=row.tvgId?`id:${row.tvgId.toLowerCase()}`:`name:${norm(row.name)||row.id}`;grouped.set(key,[...(grouped.get(key)||[]),row])}
  const list=Array.from(grouped.values()).map(group=>{
    const preferred=group.find(x=>x.tvgId)||group[0];
    const sources=group.map(x=>({provider:x.provider,sourceId:x.sourceId,streamUrl:x.streamUrl,group:x.group}));
    return {id:preferred.tvgId||norm(preferred.name)||preferred.id,tvgId:preferred.tvgId,name:preferred.name,group:preferred.group||group.find(x=>x.group)?.group,logoUrl:preferred.logoUrl||group.find(x=>x.logoUrl)?.logoUrl,sources,sourceCount:sources.length};
  }).sort((a,b)=>a.name.localeCompare(b.name));
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
      return {id:`espn:${e.id}`,league:label,sport,title:e.name||e.shortName||'Event',startTime:e.date||`${date}T00:00:00Z`,status:e.status?.type?.description||e.status?.type?.state||'scheduled',homeTeam:home?.team?.displayName,awayTeam:away?.team?.displayName,badge:e.competitions?.[0]?.competitors?.[0]?.team?.logo,broadcasts,source:'ESPN'} as SportsEvent;
    });
  }));
  return all.flatMap(x=>x.status==='fulfilled'?x.value:[]);
}

function sameEvent(a:SportsEvent,b:SportsEvent){
  const teamsA=[norm(a.homeTeam||''),norm(a.awayTeam||'')].filter(Boolean).sort().join('|');
  const teamsB=[norm(b.homeTeam||''),norm(b.awayTeam||'')].filter(Boolean).sort().join('|');
  return teamsA&&teamsA===teamsB || norm(a.title)===norm(b.title);
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
    const names=aliases(channel.name);let score=0;let reason='';
    for(const w of wanted)for(const n of names){
      if(w===n){score=Math.max(score,100);reason='exact broadcaster match'}
      else if(w.length>=3&&n.length>=3&&(w.includes(n)||n.includes(w))){score=Math.max(score,82);reason='broadcaster alias match'}
    }
    if(/sports/i.test(channel.group||'')&&score>0)score+=3;
    return {channel,score,reason};
  }).filter(x=>x.score>0).sort((a,b)=>b.score-a.score||b.channel.sourceCount-a.channel.sourceCount).slice(0,8).map(x=>({
    id:x.channel.id,name:x.channel.name,logoUrl:x.channel.logoUrl,group:x.channel.group,matchScore:x.score,matchReason:x.reason,sourceCount:x.channel.sourceCount,candidates:x.channel.sources
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
  return NextResponse.json({generatedAt:new Date().toISOString(),dates,stats:{events:events.length,eventsWithBroadcasts:events.filter(x=>x.broadcasts.length).length,eventsWithChannelMatches:events.filter(x=>x.channelMatches.length).length,channels:channelList.length},events,channels:channelList,failures,policy:'Sports schedules and broadcaster metadata are aggregated separately from playback. Only public/reviewed or customer-authorized stream candidates are returned.'});
}
