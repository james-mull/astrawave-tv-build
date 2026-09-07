import { createHash } from 'crypto';
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
      source: 'Radio Browser',
    }));
}

async function podcastIndexSearch(query: string, limit: number) {
  const apiKey = process.env.PODCASTINDEX_API_KEY?.trim();
  const apiSecret = process.env.PODCASTINDEX_API_SECRET?.trim();
  if (!apiKey || !apiSecret) return null;

  const timestamp = Math.floor(Date.now() / 1000).toString();
  const authorization = createHash('sha1').update(`${apiKey}${apiSecret}${timestamp}`).digest('hex');
  const endpoint = query
    ? `https://api.podcastindex.org/api/1.0/search/byterm?${new URLSearchParams({ q: query, max: String(Math.min(limit, 200)) }).toString()}`
    : `https://api.podcastindex.org/api/1.0/podcasts/trending?${new URLSearchParams({ max: String(Math.min(limit, 200)) }).toString()}`;

  const response = await fetch(endpoint, {
    cache: 'no-store',
    headers: {
      'User-Agent': 'AstraWave/1.0',
      'X-Auth-Date': timestamp,
      'X-Auth-Key': apiKey,
      Authorization: authorization,
    },
  });
  if (!response.ok) throw new Error(`Podcast Index ${response.status}`);
  const root = await response.json() as { feeds?: any[] };
  return (root.feeds || []).map(row => ({
    id: `podcastindex:${row.id || row.podcastGuid || row.url}`,
    kind: 'podcast' as const,
    title: row.title || 'Podcast',
    subtitle: [row.author || row.ownerName, row.language, row.categories ? Object.values(row.categories)[0] : undefined]
      .filter(Boolean).join(' • '),
    posterUrl: row.artwork || row.image || undefined,
    feedUrl: row.url || undefined,
    description: row.description || undefined,
    explicit: Boolean(row.explicit),
    source: 'Podcast Index',
    podcastGuid: row.podcastGuid || undefined,
    newestItemPublishTime: row.newestItemPublishTime || undefined,
  }));
}

async function applePodcastSearch(query: string, limit: number) {
  const params = new URLSearchParams({
    country: 'US',
    limit: String(Math.min(limit, 200)),
    term: query || 'popular podcasts',
    media: 'podcast',
    entity: 'podcast',
  });
  const response = await fetch(`https://itunes.apple.com/search?${params.toString()}`, { cache: 'no-store' });
  if (!response.ok) throw new Error(`Apple podcast directory ${response.status}`);
  const root = await response.json() as { results?: any[] };
  return (root.results || []).map(row => ({
    id: `itunes:${row.collectionId || row.trackId}`,
    kind: 'podcast' as const,
    title: row.collectionName || row.trackName || 'Podcast',
    subtitle: [row.artistName, row.primaryGenreName].filter(Boolean).join(' • '),
    posterUrl: row.artworkUrl600 || row.artworkUrl100 || undefined,
    feedUrl: row.feedUrl || undefined,
    source: 'Apple Podcasts directory',
  }));
}

async function podcastSearch(query: string, limit: number) {
  const index = await podcastIndexSearch(query, limit).catch(() => null);
  const apple = await applePodcastSearch(query, limit).catch(() => []);
  const combined = [...(index || []), ...apple];
  const seen = new Set<string>();
  return combined.filter(item => {
    const key = String(item.feedUrl || item.title).toLowerCase();
    if (!key || seen.has(key)) return false;
    seen.add(key);
    return true;
  }).slice(0, Math.min(limit, 300));
}

async function musicSearch(query: string, limit: number) {
  const params = new URLSearchParams({
    country: 'US',
    limit: String(Math.min(limit, 200)),
    term: query || 'top music',
    media: 'music',
    entity: 'song',
  });
  const response = await fetch(`https://itunes.apple.com/search?${params.toString()}`, { cache: 'no-store' });
  if (!response.ok) throw new Error(`Apple music directory ${response.status}`);
  const root = await response.json() as { results?: any[] };
  return (root.results || []).map(row => ({
    id: `itunes-music:${row.trackId || row.trackName}`,
    kind: 'music' as const,
    title: row.trackName || 'Track',
    subtitle: [row.artistName, row.collectionName, row.primaryGenreName].filter(Boolean).join(' • '),
    posterUrl: row.artworkUrl100 || undefined,
    streamUrl: row.previewUrl || undefined,
    externalUrl: row.trackViewUrl || undefined,
    source: 'Apple Music catalog preview',
  }));
}

export async function GET(request: NextRequest) {
  const url = new URL(request.url);
  const kind = (url.searchParams.get('kind') || 'radio') as AudioKind;
  const query = (url.searchParams.get('q') || '').trim();
  const tag = (url.searchParams.get('tag') || '').trim();
  const country = (url.searchParams.get('country') || '').trim();
  const limit = numberParam(url.searchParams.get('limit'), kind === 'radio' ? 180 : 100, kind === 'radio' ? 500 : 300);
  const offset = Math.max(0, Number(url.searchParams.get('offset') || 0) || 0);

  try {
    const items = kind === 'radio'
      ? await radioSearch(query, tag, country, limit, offset)
      : kind === 'podcast'
        ? await podcastSearch(query, limit)
        : await musicSearch(query, limit);
    return NextResponse.json({
      kind,
      query,
      tag,
      country,
      offset,
      limit,
      count: items.length,
      items,
      providers: kind === 'podcast'
        ? ['Podcast Index (when configured)', 'Apple Podcasts directory fallback']
        : kind === 'music'
          ? ['Apple Music catalog previews']
          : ['Radio Browser'],
    });
  } catch (error) {
    return NextResponse.json(
      { kind, query, tag, country, offset, limit, count: 0, items: [], error: error instanceof Error ? error.message : 'Audio directory unavailable' },
      { status: 502 },
    );
  }
}
