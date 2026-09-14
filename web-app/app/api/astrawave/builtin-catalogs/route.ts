import { NextRequest, NextResponse } from 'next/server';
import { allBuiltInCatalogs, movieCatalogs, tvCatalogs } from '../../../../lib/builtin-catalogs';
import { mdblistPathForCatalog } from '../../../../lib/mdblist-catalog-map';

const image=(path?:string|null,size='w500')=>path?`https://image.tmdb.org/t/p/${size}${path}`:undefined;

async function tmdb(path:string){
  const token=process.env.TMDB_BEARER_TOKEN?.trim();
  if(!token)return null;
  const response=await fetch(`https://api.themoviedb.org/3${path}`,{headers:{Authorization:`Bearer ${token}`,accept:'application/json'},next:{revalidate:14400}});
  if(!response.ok)throw new Error(`TMDB ${response.status}`);
  return response.json();
}

async function mdblist(listPath:string,kind:'movie'|'series'){
  const apikey=process.env.MDBLIST_API_KEY?.trim();if(!apikey)return null;
  const media=kind==='movie'?'movie':'show';
  const url=`https://api.mdblist.com/lists/${listPath}/items/${media}?apikey=${encodeURIComponent(apikey)}&limit=60&offset=0`;
  const response=await fetch(url,{headers:{accept:'application/json','User-Agent':'AstraWave/1.0'},next:{revalidate:14400}});
  if(!response.ok)throw new Error(`MDBList ${response.status}`);
  return response.json();
}

function mapTmdb(items:any[],kind:'movie'|'series'){
  const seen=new Set<string>();
  return (items||[]).flatMap((x:any)=>{const id=String(x.id||'');const title=String(x.title||x.name||'').trim();if(!id||!title)return[];const key=`${kind}:${id}`;if(seen.has(key))return[];seen.add(key);return[{id,kind,title,subtitle:x.release_date||x.first_air_date||undefined,posterUrl:image(x.poster_path),backdropUrl:image(x.backdrop_path,'w1280'),overview:x.overview||undefined,score:Number(x.vote_average||0),popularity:Number(x.popularity||0),genreIds:Array.isArray(x.genre_ids)?x.genre_ids:[]}];}).slice(0,60);
}

function mapMdb(payload:any,kind:'movie'|'series'){
  const source=Array.isArray(payload)?payload:(kind==='movie'?payload?.movies:payload?.shows)||payload?.items||[];
  const seen=new Set<string>();
  return (source||[]).flatMap((x:any)=>{
    const tmdb=String(x.tmdbid||x.tmdb_id||'').trim();
    const imdb=String(x.imdbid||x.imdb_id||'').trim();
    const fallback=String(x.id||'').trim();
    const id=tmdb||imdb||fallback;
    const title=String(x.title||x.name||'').trim();if(!id||!title)return[];
    const key=`${kind}:${id}`;if(seen.has(key))return[];seen.add(key);
    return[{id,kind,title,subtitle:String(x.release_year||x.year||x.released||'').trim()||undefined,posterUrl:x.poster||x.poster_url||undefined,backdropUrl:x.backdrop||x.backdrop_url||undefined,overview:x.description||x.overview||undefined,score:Number(x.score||0)||undefined,externalIds:{tmdb:tmdb||undefined,imdb:imdb||undefined,trakt:x.traktid||x.trakt_id||undefined}}];
  }).slice(0,60);
}

function fallbackSearchPath(title:string,kind:'movie'|'series'){
  const query=encodeURIComponent(title.replace(/\b(movies?|shows?|series|tv|essentials|favorites|picks|showcase)\b/gi,' ').replace(/\s+/g,' ').trim());
  return `/search/${kind==='movie'?'movie':'tv'}?query=${query}&include_adult=false&language=en-US`;
}

export async function GET(request:NextRequest){
  const id=request.nextUrl.searchParams.get('id')?.trim();
  const kind=request.nextUrl.searchParams.get('kind')==='series'?'series':'movie';
  if(!id){
    const definitions=kind==='series'?tvCatalogs:movieCatalogs;
    return NextResponse.json({kind,count:definitions.length,definitions,mdblistConfigured:Boolean(process.env.MDBLIST_API_KEY)},{headers:{'Cache-Control':'public, s-maxage=14400, stale-while-revalidate=86400'}});
  }
  const definition=allBuiltInCatalogs.find(x=>x.id===id);if(!definition)return NextResponse.json({error:'Unknown catalog'}, {status:404});
  const cacheHeaders={'Cache-Control':'public, s-maxage=14400, stale-while-revalidate=86400'};
  const listPath=mdblistPathForCatalog(id);
  if(listPath&&process.env.MDBLIST_API_KEY){
    try{
      const payload=await mdblist(listPath,definition.kind);
      const items=mapMdb(payload,definition.kind);
      if(items.length)return NextResponse.json({configured:true,definition,source:'MDBList',items},{headers:cacheHeaders});
    }catch{/* fall through to TMDB */}
  }
  if(!process.env.TMDB_BEARER_TOKEN)return NextResponse.json({configured:false,definition,source:listPath?'MDBList unavailable':'Metadata not configured',items:[]},{headers:cacheHeaders});
  try{
    const path=definition.tmdbPath||fallbackSearchPath(definition.title,definition.kind);
    const payload=await tmdb(path);
    const items=mapTmdb(payload?.results||[],definition.kind);
    return NextResponse.json({configured:true,definition,source:definition.tmdbPath?'TMDB dynamic catalog':'TMDB metadata fallback',items},{headers:cacheHeaders});
  }catch(error){
    return NextResponse.json({configured:false,definition,items:[],error:error instanceof Error?error.message:'Catalog unavailable'},{status:200,headers:cacheHeaders});
  }
}
