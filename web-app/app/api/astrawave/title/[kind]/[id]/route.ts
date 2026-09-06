import { NextResponse } from 'next/server';

const img=(path?:string|null,size='w500')=>path?`https://image.tmdb.org/t/p/${size}${path}`:undefined;

async function tmdb(path:string){
  const token=process.env.TMDB_BEARER_TOKEN;
  if(!token)return null;
  const response=await fetch(`https://api.themoviedb.org/3${path}`,{headers:{Authorization:`Bearer ${token}`,accept:'application/json'},next:{revalidate:900}});
  if(!response.ok)throw new Error(`TMDB ${response.status}`);
  return response.json();
}

function mapRelated(items:any[],kind:'movie'|'series',currentId:string){
  const seen=new Set<string>();
  return (items||[]).flatMap((item:any)=>{
    const id=String(item.id||'');if(!id||id===currentId||seen.has(id))return[];seen.add(id);
    return [{id,kind,title:item.title||item.name||'Untitled',subtitle:item.release_date||item.first_air_date||undefined,posterUrl:img(item.poster_path),backdropUrl:img(item.backdrop_path,'w1280'),overview:item.overview||undefined,score:Number(item.vote_average||0)}];
  }).slice(0,24);
}

export async function GET(_:Request,context:{params:Promise<{kind:string;id:string}>}){
  try{
    const {kind,id}=await context.params;
    if(!/^\d+$/.test(id)||!['movie','series'].includes(kind))return NextResponse.json({error:'Invalid title'},{status:400});
    const isMovie=kind==='movie';
    const path=isMovie?`/movie/${id}?language=en-US&append_to_response=credits,videos,recommendations,similar,release_dates`:`/tv/${id}?language=en-US&append_to_response=credits,videos,recommendations,similar,content_ratings`;
    const data=await tmdb(path);
    if(!data)return NextResponse.json({configured:false});
    const trailers=(data.videos?.results||[]).filter((v:any)=>v.site==='YouTube'&&['Trailer','Teaser','Clip','Featurette'].includes(v.type)).sort((a:any,b:any)=>Number(b.official)-Number(a.official)).slice(0,8).map((v:any)=>({key:v.key,name:v.name,type:v.type,official:Boolean(v.official),url:`https://www.youtube.com/watch?v=${encodeURIComponent(v.key)}`}));
    const cast=(data.credits?.cast||[]).slice(0,12).map((p:any)=>({id:String(p.id),name:p.name,role:p.character||undefined,profileUrl:img(p.profile_path,'w185')}));
    const crew=(data.credits?.crew||[]).filter((p:any)=>['Director','Creator','Writer','Screenplay','Executive Producer'].includes(p.job)).slice(0,10).map((p:any)=>({id:String(p.id),name:p.name,role:p.job}));
    let rating:string|null=null;
    if(isMovie){
      const us=data.release_dates?.results?.find((x:any)=>x.iso_3166_1==='US');
      rating=(us?.release_dates||[]).map((x:any)=>String(x.certification||'').trim()).find(Boolean)||null;
    }else{
      rating=data.content_ratings?.results?.find((x:any)=>x.iso_3166_1==='US')?.rating||null;
    }
    const runtime=isMovie?Number(data.runtime||0)||null:Number(data.episode_run_time?.[0]||0)||null;
    const related=mapRelated([...(data.recommendations?.results||[]),...(data.similar?.results||[])],kind as 'movie'|'series',id);
    return NextResponse.json({configured:true,details:{id,kind,title:data.title||data.name||'Untitled',overview:data.overview||'',tagline:data.tagline||'',posterUrl:img(data.poster_path),backdropUrl:img(data.backdrop_path,'original'),releaseDate:data.release_date||data.first_air_date||undefined,runtimeMinutes:runtime,rating,score:Number(data.vote_average||0),voteCount:Number(data.vote_count||0),genres:(data.genres||[]).map((g:any)=>g.name),status:data.status||undefined,seasons:Number(data.number_of_seasons||0)||undefined,episodes:Number(data.number_of_episodes||0)||undefined,homepage:data.homepage||undefined,cast,crew,trailers,related}});
  }catch(error){console.error('AstraWave title details error',error);return NextResponse.json({error:'Title details unavailable'},{status:500})}
}