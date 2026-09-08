import { NextRequest, NextResponse } from 'next/server';

type TraktKind = 'movies' | 'shows';
type TraktMode = 'trending' | 'popular' | 'anticipated';

function clientId() {
  return process.env.TRAKT_CLIENT_ID?.trim() || '';
}

function tmdbToken() {
  return process.env.TMDB_BEARER_TOKEN?.trim() || '';
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

async function tmdbDetails(kind: TraktKind, tmdbId?: string | null) {
  const token = tmdbToken();
  if (!token || !tmdbId) return null;
  const resource = kind === 'movies' ? 'movie' : 'tv';
  const response = await fetch(`https://api.themoviedb.org/3/${resource}/${encodeURIComponent(tmdbId)}?language=en-US`, {
    headers: { Authorization: `Bearer ${token}`, accept: 'application/json' },
    cache: 'force-cache',
    next: { revalidate: 21600 },
  });
  if (!response.ok) return null;
  return response.json();
}

const image = (path?: string | null, size = 'w500') => path ? `https://image.tmdb.org/t/p/${size}${path}` : null;

async function mapItems(raw: any[], kind: TraktKind) {
  const base = raw.slice(0, 30).map((entry: any) => {
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

  return Promise.all(base.map(async (item: any) => {
    const detail = await tmdbDetails(kind, item.tmdbId);
    return {
      ...item,
      title: detail?.title || detail?.name || item.title,
      year: item.year || Number(String(detail?.release_date || detail?.first_air_date || '').slice(0, 4)) || null,
      posterUrl: image(detail?.poster_path),
      backdropUrl: image(detail?.backdrop_path, 'w1280'),
      overview: detail?.overview || null,
    };
  }));
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
  return NextResponse.json({ mode, kind, items: await mapItems(result.body || [], kind) });
}
