import { NextRequest, NextResponse } from 'next/server';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

type AudioKind = 'radio' | 'podcast' | 'music';

function numberParam(value: string | null, fallback: number, max: number) {
  const parsed = Number(value ?? fallback);
  return Number.isFinite(parsed) ? Math.max(1, Math.min(max, Math.floor(parsed))) : fallback;
}

async function radioSearch(query: string, tag: string, country: string, limit: number, offset: number) {
  const servers = [
    'https://de1.api.radio-browser.info',
    'https://nl1.api.radio-browser.info',
    'https://at1.api.radio-browser.info',
    'https://all.api.radio-browser.info',
  ];
  const params = new URLSearchParams({
    hidebroken: 'true',
    order: 'votes',
    reverse: 'true',
    limit: String(limit),
    offset: String(offset),
  });
  if (query) params.set('name', query);
  if (tag) params.set('tag', tag);
  if (country) params.set('countrycode', country.toUpperCase());

  let rows: any[] = [];
  for (const server of servers) {
    try {
      const response = await fetch(`${server}/json/stations/search?${params.toString()}`, {
        cache: 'no-store',
        headers: { 'user-agent': 'AstraWave/1.0' },
      });
      if (!response.ok) continue;
      rows = await response.json();
      break;
    } catch {}
  }

  return rows
    .filter(row => row.stationuuid && row.name && (row.url_resolved || row.url))
    .map(row => ({
      id: `radio-browser:${row.stationuuid}`,
      kind: 'radio' as const,
      title: String(row.name).trim(),
      subtitle: [row.countrycode || row.country, String(row.tags || '').split(',')[0]].filter(Boolean).join(' • '),
      posterUrl: String(row.favicon || '').startsWith('http') ? row.favicon : undefined,
      streamUrl: row.url_resolved || row.url,
      country: row.countrycode || undefined,
      genre: String(row.tags || '').split(',')[0] || undefined,
    }));
}

async function appleSearch(kind: 'podcast' | 'music', query: string, limit: number) {
  const params = new URLSearchParams({
    country: 'US',
    limit: String(Math.min(limit, 200)),
    term: query || (kind === 'podcast' ? 'popular podcasts' : 'top music'),
    media: kind === 'podcast' ? 'podcast' : 'music',
    entity: kind === 'podcast' ? 'podcast' : 'song',
  });
  const response = await fetch(`https://itunes.apple.com/search?${params.toString()}`, { cache: 'no-store' });
  if (!response.ok) throw new Error(`Apple directory ${response.status}`);
  const root = await response.json() as { results?: any[] };
  return (root.results || []).map(row => kind === 'podcast' ? ({
    id: `itunes:${row.collectionId || row.trackId}`,
    kind: 'podcast' as const,
    title: row.collectionName || row.trackName || 'Podcast',
    subtitle: [row.artistName, row.primaryGenreName].filter(Boolean).join(' • '),
    posterUrl: row.artworkUrl600 || row.artworkUrl100 || undefined,
    feedUrl: row.feedUrl || undefined,
  }) : ({
    id: `itunes-music:${row.trackId || row.trackName}`,
    kind: 'music' as const,
    title: row.trackName || 'Track',
    subtitle: [row.artistName, row.collectionName, row.primaryGenreName].filter(Boolean).join(' • '),
    posterUrl: row.artworkUrl100 || undefined,
    streamUrl: row.previewUrl || undefined,
    externalUrl: row.trackViewUrl || undefined,
  }));
}

export async function GET(request: NextRequest) {
  const url = new URL(request.url);
  const kind = (url.searchParams.get('kind') || 'radio') as AudioKind;
  const query = (url.searchParams.get('q') || '').trim();
  const tag = (url.searchParams.get('tag') || '').trim();
  const country = (url.searchParams.get('country') || '').trim();
  const limit = numberParam(url.searchParams.get('limit'), kind === 'radio' ? 180 : 100, kind === 'radio' ? 500 : 200);
  const offset = Math.max(0, Number(url.searchParams.get('offset') || 0) || 0);

  try {
    const items = kind === 'radio'
      ? await radioSearch(query, tag, country, limit, offset)
      : await appleSearch(kind, query, limit);
    return NextResponse.json({ kind, query, tag, country, offset, limit, count: items.length, items });
  } catch (error) {
    return NextResponse.json(
      { kind, query, tag, country, offset, limit, count: 0, items: [], error: error instanceof Error ? error.message : 'Audio directory unavailable' },
      { status: 502 },
    );
  }
}
