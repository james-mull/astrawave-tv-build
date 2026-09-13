import { NextResponse } from 'next/server';

export const dynamic='force-dynamic';

const COOKIE_NAME='astrawave_rd_access';

export async function POST(){
  const response=NextResponse.json({connected:false});
  response.cookies.set(COOKIE_NAME,'',{httpOnly:true,secure:true,sameSite:'lax',path:'/',maxAge:0});
  return response;
}
