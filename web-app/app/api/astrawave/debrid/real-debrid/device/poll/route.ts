import { NextRequest, NextResponse } from 'next/server';

export const dynamic='force-dynamic';

const CLIENT_ID='X245A4XAIBGVM';
const DEVICE_GRANT='http://oauth.net/grant_type/device/1.0';
const COOKIE_NAME='astrawave_rd_access';

export async function POST(request:NextRequest){
  try{
    const body=await request.json() as {deviceCode?:string};
    const deviceCode=String(body.deviceCode||'').trim();
    if(!deviceCode)return NextResponse.json({error:'Missing device code'},{status:400});

    const credentialsUrl=new URL('https://api.real-debrid.com/oauth/v2/device/credentials');
    credentialsUrl.searchParams.set('client_id',CLIENT_ID);
    credentialsUrl.searchParams.set('code',deviceCode);
    const credentialsResponse=await fetch(credentialsUrl,{cache:'no-store',signal:AbortSignal.timeout(10000)});
    if(!credentialsResponse.ok){
      if(credentialsResponse.status===400||credentialsResponse.status===403)return NextResponse.json({pending:true},{status:202});
      return NextResponse.json({error:`Real-Debrid credentials ${credentialsResponse.status}`},{status:502});
    }
    const credentials=await credentialsResponse.json() as {client_id?:string;client_secret?:string};
    if(!credentials.client_id||!credentials.client_secret)return NextResponse.json({pending:true},{status:202});

    const tokenResponse=await fetch('https://api.real-debrid.com/oauth/v2/token',{
      method:'POST',cache:'no-store',signal:AbortSignal.timeout(10000),headers:{'content-type':'application/x-www-form-urlencoded'},
      body:new URLSearchParams({client_id:credentials.client_id,client_secret:credentials.client_secret,code:deviceCode,grant_type:DEVICE_GRANT}),
    });
    const token=await tokenResponse.json().catch(()=>({})) as {access_token?:string;refresh_token?:string;expires_in?:number;token_type?:string;error?:string};
    if(!tokenResponse.ok||!token.access_token)return NextResponse.json({error:token.error||`Real-Debrid token ${tokenResponse.status}`},{status:502});
    const expiresIn=Math.max(60,Math.min(Number(token.expires_in||3600),60*60*24*30));
    const response=NextResponse.json({connected:true,accessToken:token.access_token,refreshToken:token.refresh_token||null,expiresIn,tokenType:token.token_type||'Bearer'});
    response.cookies.set(COOKIE_NAME,token.access_token,{httpOnly:true,secure:true,sameSite:'lax',path:'/',maxAge:expiresIn});
    return response;
  }catch(error){console.error('Real-Debrid device poll error',error);return NextResponse.json({error:'Could not finish Real-Debrid authorization'},{status:500})}
}
