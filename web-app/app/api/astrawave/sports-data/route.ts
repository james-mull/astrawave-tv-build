import { NextResponse } from 'next/server';

function isoDate(offset:number){const d=new Date();d.setUTCDate(d.getUTCDate()+offset);return d.toISOString().slice(0,10)}

async function day(date:string){
  const key=process.env.THESPORTSDB_API_KEY||'123';
  const response=await fetch(`https://www.thesportsdb.com/api/v1/json/${key}/eventsday.php?d=${date}`,{next:{revalidate:300}});
  if(!response.ok)return[];
  const body=await response.json();
  return (body.events||[]).map((event:any)=>({
    id:String(event.idEvent||`${date}:${event.strEvent}`),
    league:event.strLeague||event.strSport||'Sports',
    sport:event.strSport||undefined,
    title:event.strEvent||'Event',
    startTime:`${event.dateEvent||date}T${event.strTime||'00:00:00'}`,
    status:event.strStatus||'scheduled',
    broadcaster:event.strTVStation||undefined,
    homeTeam:event.strHomeTeam||undefined,
    awayTeam:event.strAwayTeam||undefined,
    badge:event.strThumb||event.strPoster||undefined,
    date:event.dateEvent||date,
  }));
}

export async function GET(){
  const dates=[0,1,2].map(isoDate);
  const settled=await Promise.allSettled(dates.map(day));
  const events=settled.flatMap(r=>r.status==='fulfilled'?r.value:[]).sort((a,b)=>a.startTime.localeCompare(b.startTime));
  const leagues=Array.from(new Set(events.map(e=>e.league))).slice(0,30);
  return NextResponse.json({dates,events:events.slice(0,180),leagues,count:events.length});
}
