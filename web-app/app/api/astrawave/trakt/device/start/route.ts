import { NextRequest, NextResponse } from 'next/server';
import { verifyFirebaseUser } from '../../../../../lib/trakt-device';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function POST(request: NextRequest) {
  const clientId = process.env.TRAKT_CLIENT_ID?.trim();
  if (!clientId) return NextResponse.json({ connected: false, error: 'Trakt is not configured' }, { status: 503 });
  const auth = request.headers.get('authorization') || '';
  const idToken = auth.startsWith('Bearer ') ? auth.slice(7).trim() : '';
  if (!idToken) return NextResponse.json({ connected: false, error: 'AstraWave sign-in is required' }, { status: 401 });
  try {
    await verifyFirebaseUser(idToken);
    const response = await fetch('https://api.trakt.tv/oauth/device/code', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ client_id: clientId }),
      cache: 'no-store',
    });
    const data = await response.json() as Record<string, unknown>;
    if (!response.ok) return NextResponse.json({ connected: false, error: `Trakt device auth ${response.status}`, detail: data }, { status: 502 });
    return NextResponse.json({ connected: false, ...data });
  } catch (error) {
    return NextResponse.json({ connected: false, error: error instanceof Error ? error.message : 'Unable to start Trakt connection' }, { status: 502 });
  }
}
