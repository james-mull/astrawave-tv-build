import { NextRequest, NextResponse } from 'next/server';

type TraktKind = 'movies' | 'shows';
type TraktMode = 'trending' | 'popular' | 'anticipated';

function clientId() {
  return process.env.TRAKT_CLIENT_ID?.trim() || '';
}

async function trakt(path: string) {
  const id = clientId();
  if (!id) return { ok: false as const, status: 503, error: 'Trakt is not configured.' };
  const response = await fetch(`https://api.trakt.tv${path}`, {
    headers: {
      'Content-Type': 'application/json',
      'trakt-api-version': '2',
      'trakt-api-key': id,
      'User-Agent': 'AstraWave/1.0',
    },
    cache: 'no-store',
  });
  if (!response.ok) {
    return { ok: false as const, status: response.status, error: `Trakt HTTP ${response.status}` };
  }
  return { ok: true as const, body: await response.json() };
}

function mapItems(raw: any[], kind: TraktKind) {
  return raw.slice(0, 30).map((entry: any) => {
    const node = entry.movie || entry.show || entry;
    const ids = node.ids || {};
    return {
      id: ids.tmdb ? String(ids.tmdb) : ids.imdb || ids.trakt ? String(ids.trakt) : '',
      imdbId: ids.imdb || null,
      tmdbId: ids.tmdb ? String(ids.tmdb) : null,
      traktId: ids.trakt ? String(ids.trakt) : null,
      kind: kind === 'movies' ? 'movie' : 'series',
      title: node.title || 'Untitled',
      year: node.year || null,
      watchers: entry.watchers ?? null,
      listCount: entry.list_count ?? null,
    };
  }).filter((item: any) => item.id && item.title);
}

export async function GET(request: NextRequest) {
  const mode = (request.nextUrl.searchParams.get('mode') || 'trending') as TraktMode;
  const kind = (request.nextUrl.searchParams.get('kind') || 'movies') as TraktKind;
  if (!['trending', 'popular', 'anticipated'].includes(mode)) {
    return NextResponse.json({ error: 'Unsupported Trakt mode.' }, { status: 400 });
  }
  if (!['movies', 'shows'].includes(kind)) {
    return NextResponse.json({ error: 'Unsupported Trakt kind.' }, { status: 400 });
  }
  const result = await trakt(`/${kind}/${mode}?limit=30`);
  if (!result.ok) return NextResponse.json({ error: result.error }, { status: result.status });
  return NextResponse.json({ mode, kind, items: mapItems(result.body || [], kind) });
}
