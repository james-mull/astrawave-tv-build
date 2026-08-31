import { NextRequest, NextResponse } from 'next/server';

type CatalogItem = {
  id: string;
  kind: 'movie' | 'series' | 'episode' | 'live' | 'sport' | 'song' | 'podcast';
  title: string;
  subtitle?: string;
  posterUrl?: string;
  backdropUrl?: string;
};

const tmdbImage = (path?: string | null, size = 'w500') => path ? `https://image.tmdb.org/t/p/${size}${path}` : undefined;

async function tmdb(path: string) {
  const token = process.env.TMDB_BEARER_TOKEN;
  if (!token) return null;
  const res = await fetch(`https://api.themoviedb.org/3${path}`, {
    headers: { Authorization: `Bearer ${token}`, accept: 'application/json' },
    cache: 'no-store',
  });
  if (!res.ok) throw new Error(`TMDB ${res.status}`);
  return res.json();
}

function mapTmdb(items: any[], kind: 'movie' | 'series'): CatalogItem[] {
  return items.slice(0, 30).map((item) => ({
    id: String(item.id),
    kind,
    title: item.title || item.name || 'Untitled',
    subtitle: item.release_date || item.first_air_date || undefined,
    posterUrl: tmdbImage(item.poster_path),
    backdropUrl: tmdbImage(item.backdrop_path, 'w1280'),
  }));
}

function parseM3u(text: string) {
  const channels: any[] = [];
  const lines = text.split(/\r?\n/).map((x) => x.trim()).filter(Boolean);
  let pending = '';
  for (const line of lines) {
    if (line.startsWith('#EXTINF')) pending = line;
    else if (!line.startsWith('#') && pending) {
      const attr = (name: string) => pending.match(new RegExp(`${name}="([^"]*)"`, 'i'))?.[1];
      const name = pending.split(',').pop()?.trim() || 'Channel';
      channels.push({
        id: attr('tvg-id') || `${channels.length}`,
        name,
        group: attr('group-title') || undefined,
        logoUrl: attr('tvg-logo') || undefined,
        sourceCount: 1,
        url: line,
      });
      pending = '';
    }
  }
  return channels;
}

async function liveChannels() {
  const url = process.env.ASTRAWAVE_LIVE_M3U_URL;
  if (!url) return [];
  const res = await fetch(url, { cache: 'no-store' });
  if (!res.ok) throw new Error(`Live feed ${res.status}`);
  return parseM3u(await res.text());
}

async function sportsToday() {
  const date = new Date().toISOString().slice(0, 10);
  const key = process.env.THESPORTSDB_API_KEY || '123';
  const res = await fetch(`https://www.thesportsdb.com/api/v1/json/${key}/eventsday.php?d=${date}`, { cache: 'no-store' });
  if (!res.ok) return [];
  const body = await res.json();
  return (body.events || []).slice(0, 40).map((event: any) => ({
    id: String(event.idEvent || event.strEvent),
    league: event.strLeague || event.strSport || 'Sports',
    title: event.strEvent || 'Event',
    startTime: `${event.dateEvent || date}T${event.strTime || '00:00:00'}`,
    status: event.strStatus || 'scheduled',
    broadcaster: event.strTVStation || undefined,
  }));
}

async function homeRows() {
  const [moviesRaw, showsRaw, sports] = await Promise.all([
    tmdb('/trending/movie/day'),
    tmdb('/trending/tv/day'),
    sportsToday(),
  ]);
  const movies = moviesRaw ? mapTmdb(moviesRaw.results || [], 'movie') : [];
  const shows = showsRaw ? mapTmdb(showsRaw.results || [], 'series') : [];
  return {
    rows: [
      { title: 'Trending Movies', items: movies.slice(0, 12) },
      { title: 'Trending TV', items: shows.slice(0, 12) },
      { title: 'Sports Today', items: sports.slice(0, 12).map((x: any) => ({ id: x.id, kind: 'sport', title: x.title, subtitle: x.league })) },
    ].filter((row) => row.items.length),
  };
}

export async function GET(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  try {
    const { path } = await context.params;
    const route = '/' + path.join('/');

    if (route === '/v1/home') return NextResponse.json(await homeRows());
    if (route === '/v1/catalog/movies/trending') {
      const data = await tmdb('/trending/movie/day');
      return NextResponse.json(data ? mapTmdb(data.results || [], 'movie') : []);
    }
    if (route === '/v1/catalog/series/trending') {
      const data = await tmdb('/trending/tv/day');
      return NextResponse.json(data ? mapTmdb(data.results || [], 'series') : []);
    }
    if (route === '/v1/search') {
      const q = request.nextUrl.searchParams.get('q')?.trim();
      if (!q) return NextResponse.json([]);
      const data = await tmdb(`/search/multi?query=${encodeURIComponent(q)}&include_adult=false`);
      if (!data) return NextResponse.json([]);
      const items: CatalogItem[] = (data.results || []).filter((x: any) => x.media_type === 'movie' || x.media_type === 'tv').slice(0, 30).map((x: any) => ({
        id: String(x.id),
        kind: x.media_type === 'movie' ? 'movie' : 'series',
        title: x.title || x.name || 'Untitled',
        subtitle: x.release_date || x.first_air_date || undefined,
        posterUrl: tmdbImage(x.poster_path),
        backdropUrl: tmdbImage(x.backdrop_path, 'w1280'),
      }));
      return NextResponse.json(items);
    }
    if (route === '/v1/live/channels' || route === '/v1/live/guide') return NextResponse.json(await liveChannels());
    if (route === '/v1/sports/today') return NextResponse.json(await sportsToday());
    if (route.startsWith('/v1/sources/')) return NextResponse.json([]);

    return NextResponse.json({ error: 'Not found' }, { status: 404 });
  } catch (error) {
    console.error('AstraWave API error', error);
    return NextResponse.json({ error: 'AstraWave service error' }, { status: 500 });
  }
}
