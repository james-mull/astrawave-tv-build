import { NextRequest, NextResponse } from 'next/server';
import { saveTraktTokens, verifyFirebaseUser } from '../../../../../lib/trakt-device';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function POST(request: NextRequest) {
  const clientId = process.env.TRAKT_CLIENT_ID?.trim();
  const clientSecret = process.env.TRAKT_CLIENT_SECRET?.trim();
  if (!clientId || !clientSecret) return NextResponse.json({ connected: false, error: 'Trakt is not fully configured' }, { status: 503 });
  const auth = request.headers.get('authorization') || '';
  const idToken = auth.startsWith('Bearer ') ? auth.slice(7).trim() : '';
  if (!idToken) return NextResponse.json({ connected: false, error: 'AstraWave sign-in is required' }, { status: 401 });
  let body: { device_code?: string };
  try { body = await request.json(); } catch { return NextResponse.json({ connected: false, error: 'Invalid payload' }, { status: 400 }); }
  const deviceCode = body.device_code?.trim() || '';
  if (!deviceCode) return NextResponse.json({ connected: false, error: 'Missing device code' }, { status: 400 });
  try {
    const uid = await verifyFirebaseUser(idToken);
    const response = await fetch('https://api.trakt.tv/oauth/device/token', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ code: deviceCode, client_id: clientId, client_secret: clientSecret }),
      cache: 'no-store',
    });
    const data = await response.json() as {
      access_token?: string; refresh_token?: string; token_type?: string; scope?: string; expires_in?: number; created_at?: number;
    } & Record<string, unknown>;
    if (response.status === 400 || response.status === 404) return NextResponse.json({ connected: false, pending: true }, { status: 202 });
    if (!response.ok || !data.access_token) return NextResponse.json({ connected: false, error: `Trakt token exchange ${response.status}` }, { status: 502 });
    await saveTraktTokens(uid, {
      access_token: data.access_token,
      refresh_token: data.refresh_token,
      token_type: data.token_type,
      scope: data.scope,
      expires_in: data.expires_in,
      created_at: data.created_at,
    });
    return NextResponse.json({ connected: true, provider: 'TRAKT' });
  } catch (error) {
    return NextResponse.json({ connected: false, error: error instanceof Error ? error.message : 'Unable to complete Trakt connection' }, { status: 502 });
  }
}
