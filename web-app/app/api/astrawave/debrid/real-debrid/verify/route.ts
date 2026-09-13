import { NextRequest, NextResponse } from 'next/server';

export const dynamic='force-dynamic';

export async function POST(request:NextRequest){
  try{
    const body=await request.json() as {accessToken?:string};
    const accessToken=String(body.accessToken||'').trim();
    if(!accessToken)return NextResponse.json({error:'Missing access token'},{status:400});
    const response=await fetch('https://api.real-debrid.com/rest/1.0/user',{cache:'no-store',signal:AbortSignal.timeout(10000),headers:{Authorization:`Bearer ${accessToken}`,accept:'application/json'}});
    const profile=await response.json().catch(()=>({})) as {username?:string;type?:string;expiration?:string;avatar?:string;error?:string};
    if(!response.ok)return NextResponse.json({error:profile.error||`Real-Debrid ${response.status}`},{status:401});
    return NextResponse.json({connected:true,username:profile.username||null,type:profile.type||null,expiration:profile.expiration||null,avatar:profile.avatar||null});
  }catch(error){console.error('Real-Debrid verify error',error);return NextResponse.json({error:'Could not verify Real-Debrid account'},{status:500})}
}
