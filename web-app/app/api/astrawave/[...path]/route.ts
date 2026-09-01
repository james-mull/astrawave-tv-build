import { NextRequest, NextResponse } from 'next/server';

type CatalogItem = {
  id: string;
  kind: 'movie' | 'series' | 'episode' | 'live' | 'sport' | 'song' | 'podcast';
  title: string;
  subtitle?: string;
  posterUrl?: string;
  backdropUrl?: string;
  streamUrl?: string;
  sources?: LiveStreamSource[];
};

type LiveStreamSource = {
  id: string;
  provider: string;
  streamUrl: string;
  group?: string;
};

type ParsedLiveChannel = {
  id: string;
  tvgId?: string;
  name: string;
  group?: string;
  logoUrl?: string;
  streamUrl: string;
  sourceName: string;
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
    id: String(item.id), kind,
    title: item.title || item.name || 'Untitled',
    subtitle: item.release_date || item.first_air_date || undefined,
    posterUrl: tmdbImage(item.poster_path),
    backdropUrl: tmdbImage(item.backdrop_path, 'w1280'),
  }));
}

function parseM3u(text: string, sourceName: string) {
  const channels: ParsedLiveChannel[] = [];
  const lines = text.split(/\r?\n/).map((x) => x.trim()).filter(Boolean);
  let pending = '';
  for (const line of lines) {
    if (line.startsWith('#EXTINF')) pending = line;
    else if (!line.startsWith('#') && pending) {
      const attr = (name: string) => pending.match(new RegExp(`${name}="([^"]*)"`, 'i'))?.[1];
      const tvgId = attr('tvg-id') || undefined;
      const name = pending.split(',').pop()?.trim() || 'Channel';
      channels.push({ id: tvgId || `${sourceName}:${channels.length}`, tvgId, name, group: attr('group-title') || undefined, logoUrl: attr('tvg-logo') || undefined, streamUrl: line, sourceName });
      pending = '';
    }
  }
  return channels;
}

const freeLiveM3uUrls = [
  'https://iptv-org.github.io/iptv/index.m3u',
  'https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8',
];

function playlistUrls() {
  const configured = [
    process.env.ASTRAWAVE_LIVE_M3U_URL,
    ...(process.env.ASTRAWAVE_LIVE_M3U_URLS || '').split(/[,\n]/),
  ].map((url) => url?.trim()).filter(Boolean) as string[];
  return Array.from(new Set(configured.length ? configured : freeLiveM3uUrls));
}

function playlistLabel(url: string) {
  try {
    const parsed = new URL(url);
    return parsed.hostname.replace(/^raw\.githubusercontent\.com$/i, 'githubusercontent.com');
  } catch {
    return 'Playlist';
  }
}

function normalizeChannelName(name: string) {
  return name
    .toLowerCase()
    .replace(/\b(uhd|fhd|hd|sd|4k|hevc|h\.?26[45]|60fps|backup|alt|east|west|central|us|usa)\b/g, ' ')
    .replace(/[^a-z0-9]+/g, ' ')
    .trim();
}

function mergeLiveChannels(channels: ParsedLiveChannel[]) {
  const groups = new Map<string, ParsedLiveChannel[]>();
  for (const channel of channels) {
    const key = channel.tvgId ? `id:${channel.tvgId.toLowerCase()}` : `name:${normalizeChannelName(channel.name) || channel.id}`;
    groups.set(key, [...(groups.get(key) || []), channel]);
  }
  return Array.from(groups.values()).map((items) => {
    const preferred = items.find((item) => item.tvgId) || items[0];
    const sources = items.map((item, index) => ({
      id: `${preferred.id}:${index}`,
      provider: item.sourceName,
      streamUrl: item.streamUrl,
      group: item.group,
    }));
    return {
      id: preferred.tvgId || normalizeChannelName(preferred.name) || preferred.id,
      name: preferred.name,
      group: preferred.group || items.find((item) => item.group)?.group,
      logoUrl: preferred.logoUrl || items.find((item) => item.logoUrl)?.logoUrl,
      streamUrl: sources[0]?.streamUrl,
      sources,
      sourceCount: sources.length,
    };
  });
}

async function liveChannels() {
  const results = await Promise.allSettled(playlistUrls().map(async (url) => {
    const res = await fetch(url, { cache: 'no-store' });
    if (!res.ok) throw new Error(`Live feed ${res.status}`);
    return parseM3u(await res.text(), playlistLabel(url));
  }));
  const channels = results.flatMap((result) => result.status === 'fulfilled' ? result.value : []);
  if (!channels.length) throw new Error('No live channels loaded');
  return mergeLiveChannels(channels);
}

async function sportsToday() {
  const date = new Date().toISOString().slice(0, 10);
  const key = process.env.THESPORTSDB_API_KEY || '123';
  const res = await fetch(`https://www.thesportsdb.com/api/v1/json/${key}/eventsday.php?d=${date}`, { cache: 'no-store' });
  if (!res.ok) return [];
  const body = await res.json();
  return (body.events || []).slice(0, 40).map((event: any) => ({
    id: String(event.idEvent || event.strEvent), league: event.strLeague || event.strSport || 'Sports', title: event.strEvent || 'Event',
    startTime: `${event.dateEvent || date}T${event.strTime || '00:00:00'}`, status: event.strStatus || 'scheduled', broadcaster: event.strTVStation || undefined,
  }));
}

function allowedArchiveLicense(meta: any) {
  const license = String(meta?.licenseurl || '').toLowerCase();
  const rights = String(meta?.rights || '').toLowerCase();
  return license.includes('creativecommons.org') || rights.includes('public domain') || rights.includes('creative commons');
}

async function archiveSources(title: string, year?: string) {
  const q = [`title:("${title.replace(/"/g, '')}")`, 'mediatype:(movies)'];
  if (year) q.push(`year:${year.slice(0, 4)}`);
  const search = await fetch(`https://archive.org/advancedsearch.php?q=${encodeURIComponent(q.join(' AND '))}&fl[]=identifier,title,year&rows=6&page=1&output=json`, { cache: 'no-store' });
  if (!search.ok) return [];
  const docs = (await search.json())?.response?.docs || [];
  const resolved: any[] = [];
  for (const doc of docs) {
    const identifier = String(doc.identifier || '');
    if (!identifier) continue;
    const metaRes = await fetch(`https://archive.org/metadata/${encodeURIComponent(identifier)}`, { cache: 'no-store' });
    if (!metaRes.ok) continue;
    const meta = await metaRes.json();
    if (!allowedArchiveLicense(meta.metadata)) continue;
    const files = (meta.files || []).filter((f: any) => {
      const name = String(f.name || '').toLowerCase();
      const format = String(f.format || '').toLowerCase();
      return (name.endsWith('.mp4') || format.includes('mpeg4') || format.includes('h.264')) && Number(f.size || 0) > 1_000_000;
    }).slice(0, 5);
    for (const file of files) {
      const quality = /2160|4k/.test(file.name) ? '2160p' : /1080/.test(file.name) ? '1080p' : /720/.test(file.name) ? '720p' : /480/.test(file.name) ? '480p' : undefined;
      resolved.push({
        id: `${identifier}:${file.name}`,
        provider: 'Internet Archive Open Media',
        url: `https://archive.org/download/${encodeURIComponent(identifier)}/${encodeURIComponent(file.name)}`,
        quality,
        direct: true,
        licenseLabel: meta.metadata.licenseurl || meta.metadata.rights || 'Open media',
      });
    }
  }
  return resolved.slice(0, 12);
}

async function sources(kind: string, id: string) {
  if (kind !== 'movie' && kind !== 'series') return [];
  const details = await tmdb(kind === 'movie' ? `/movie/${encodeURIComponent(id)}` : `/tv/${encodeURIComponent(id)}`);
  if (!details) return [];
  const title = details.title || details.name;
  const date = details.release_date || details.first_air_date;
  if (!title) return [];
  return archiveSources(title, date);
}

async function homeRows() {
  const [moviesRaw, showsRaw, live, sports] = await Promise.all([tmdb('/trending/movie/day'), tmdb('/trending/tv/day'), liveChannels(), sportsToday()]);
  const movies = moviesRaw ? mapTmdb(moviesRaw.results || [], 'movie') : [];
  const shows = showsRaw ? mapTmdb(showsRaw.results || [], 'series') : [];
  return { rows: [
    { title: 'Trending Movies', items: movies.slice(0, 12) },
    { title: 'Trending TV', items: shows.slice(0, 12) },
    { title: 'Live TV', items: live.slice(0, 24).map((x: any) => ({ id: x.id, kind: 'live', title: x.name, subtitle: `${x.group || 'Live channel'} • ${x.sourceCount} source${x.sourceCount === 1 ? '' : 's'}`, posterUrl: x.logoUrl, streamUrl: x.streamUrl, sources: x.sources })) },
    { title: 'Sports Today', items: sports.slice(0, 12).map((x: any) => ({ id: x.id, kind: 'sport', title: x.title, subtitle: x.league })) },
  ].filter((row) => row.items.length) };
}

export async function GET(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  try {
    const { path } = await context.params;
    const route = '/' + path.join('/');
    if (route === '/v1/home') return NextResponse.json(await homeRows());
    if (route === '/v1/catalog/movies/trending') { const data = await tmdb('/trending/movie/day'); return NextResponse.json(data ? mapTmdb(data.results || [], 'movie') : []); }
    if (route === '/v1/catalog/series/trending') { const data = await tmdb('/trending/tv/day'); return NextResponse.json(data ? mapTmdb(data.results || [], 'series') : []); }
    if (route === '/v1/search') {
      const q = request.nextUrl.searchParams.get('q')?.trim(); if (!q) return NextResponse.json([]);
      const data = await tmdb(`/search/multi?query=${encodeURIComponent(q)}&include_adult=false`); if (!data) return NextResponse.json([]);
      return NextResponse.json((data.results || []).filter((x: any) => x.media_type === 'movie' || x.media_type === 'tv').slice(0, 30).map((x: any) => ({
        id: String(x.id), kind: x.media_type === 'movie' ? 'movie' : 'series', title: x.title || x.name || 'Untitled',
        subtitle: x.release_date || x.first_air_date || undefined, posterUrl: tmdbImage(x.poster_path), backdropUrl: tmdbImage(x.backdrop_path, 'w1280'),
      })));
    }
    if (route === '/v1/live/channels' || route === '/v1/live/guide') return NextResponse.json(await liveChannels());
    if (route === '/v1/sports/today') return NextResponse.json(await sportsToday());
    const sourceMatch = route.match(/^\/v1\/sources\/([^/]+)\/([^/]+)$/);
    if (sourceMatch) return NextResponse.json(await sources(decodeURIComponent(sourceMatch[1]), decodeURIComponent(sourceMatch[2])));
    return NextResponse.json({ error: 'Not found' }, { status: 404 });
  } catch (error) {
    console.error('AstraWave API error', error);
    return NextResponse.json({ error: 'AstraWave service error' }, { status: 500 });
  }
}
