import { NextResponse } from 'next/server';

export const dynamic='force-dynamic';

const CLIENT_ID='X245A4XAIBGVM';

export async function POST(){
  try{
    const url=new URL('https://api.real-debrid.com/oauth/v2/device/code');
    url.searchParams.set('client_id',CLIENT_ID);
    url.searchParams.set('new_credentials','yes');
    const response=await fetch(url,{cache:'no-store',signal:AbortSignal.timeout(10000)});
    const body=await response.json().catch(()=>({}));
    if(!response.ok)return NextResponse.json({error:body?.error||`Real-Debrid ${response.status}`},{status:502});
    return NextResponse.json({deviceCode:body.device_code,userCode:body.user_code,verificationUrl:body.verification_url,interval:Number(body.interval||5),expiresIn:Number(body.expires_in||1800)});
  }catch(error){console.error('Real-Debrid device start error',error);return NextResponse.json({error:'Could not start Real-Debrid authorization'},{status:500})}
}
