import { NextRequest, NextResponse } from 'next/server';

type RailDef = { title: string; kind: 'movie' | 'series'; path: string; source?: string };

const tmdbImage = (path?: string | null, size = 'w500') => path ? `https://image.tmdb.org/t/p/${size}${path}` : undefined;

async function tmdb(path: string) {
  const token = process.env.TMDB_BEARER_TOKEN;
  if (!token) return null;
  const response = await fetch(`https://api.themoviedb.org/3${path}`, {
    headers: { Authorization: `Bearer ${token}`, accept: 'application/json' },
    next: { revalidate: 900 },
  });
  if (!response.ok) throw new Error(`TMDB ${response.status}`);
  return response.json();
}

async function trakt(path: string) {
  const clientId = process.env.TRAKT_CLIENT_ID?.trim();
  if (!clientId) return null;
  const response = await fetch(`https://api.trakt.tv${path}`, {
    headers: {
      'Content-Type': 'application/json',
      'trakt-api-version': '2',
      'trakt-api-key': clientId,
      'User-Agent': 'AstraWave/1.0',
    },
    next: { revalidate: 900 },
  });
  if (!response.ok) throw new Error(`Trakt ${response.status}`);
  return response.json();
}

function mapItems(items: any[], kind: 'movie' | 'series') {
  return (items || []).slice(0, 30).map((item) => ({
    id: String(item.id),
    kind,
    title: item.title || item.name || 'Untitled',
    subtitle: item.release_date || item.first_air_date || undefined,
    posterUrl: tmdbImage(item.poster_path),
    backdropUrl: tmdbImage(item.backdrop_path, 'w1280'),
    overview: item.overview || undefined,
    score: Number(item.vote_average || 0),
    popularity: Number(item.popularity || 0),
    genreIds: Array.isArray(item.genre_ids) ? item.genre_ids : [],
  }));
}

function mapTraktItems(rows: any[], kind: 'movie' | 'series') {
  return (rows || []).slice(0, 30).flatMap((row) => {
    const media = kind === 'movie' ? (row.movie || row) : (row.show || row);
    if (!media?.title) return [];
    const tmdbId = media.ids?.tmdb;
    const traktId = media.ids?.trakt;
    const id = tmdbId || traktId;
    if (!id) return [];
    return [{
      id: String(id),
      kind,
      title: String(media.title),
      subtitle: media.year ? String(media.year) : undefined,
      score: Number(row.score || 0) || undefined,
      popularity: Number(row.list_count || row.watchers || row.plays || 0) || undefined,
      sourceId: traktId ? `trakt:${traktId}` : undefined,
      externalIds: {
        trakt: traktId || undefined,
        tmdb: tmdbId || undefined,
        imdb: media.ids?.imdb || undefined,
        slug: media.ids?.slug || undefined,
      },
    }];
  });
}

const movieRails: RailDef[] = [
  { title: 'Trending Movies', kind: 'movie', path: '/trending/movie/day?language=en-US' },
  { title: 'Popular Movies', kind: 'movie', path: '/movie/popular?language=en-US' },
  { title: 'New Releases', kind: 'movie', path: '/movie/now_playing?language=en-US&region=US' },
  { title: 'Top Rated Movies', kind: 'movie', path: '/movie/top_rated?language=en-US' },
  { title: 'Coming Soon', kind: 'movie', path: '/movie/upcoming?language=en-US&region=US' },
  { title: 'Action', kind: 'movie', path: '/discover/movie?with_genres=28&sort_by=popularity.desc&include_adult=false&language=en-US' },
  { title: 'Comedy', kind: 'movie', path: '/discover/movie?with_genres=35&sort_by=popularity.desc&include_adult=false&language=en-US' },
  { title: 'Thrillers', kind: 'movie', path: '/discover/movie?with_genres=53&sort_by=popularity.desc&include_adult=false&language=en-US' },
  { title: 'Horror', kind: 'movie', path: '/discover/movie?with_genres=27&sort_by=popularity.desc&include_adult=false&language=en-US' },
  { title: 'Sci-Fi', kind: 'movie', path: '/discover/movie?with_genres=878&sort_by=popularity.desc&include_adult=false&language=en-US' },
  { title: 'Animation', kind: 'movie', path: '/discover/movie?with_genres=16&sort_by=popularity.desc&include_adult=false&language=en-US' },
  { title: 'Family Night', kind: 'movie', path: '/discover/movie?with_genres=10751&sort_by=popularity.desc&include_adult=false&language=en-US' },
  { title: 'Documentaries', kind: 'movie', path: '/discover/movie?with_genres=99&sort_by=vote_average.desc&vote_count.gte=100&include_adult=false&language=en-US' },
];

const tvRails: RailDef[] = [
  { title: 'Trending TV', kind: 'series', path: '/trending/tv/day?language=en-US' },
  { title: 'Popular TV', kind: 'series', path: '/tv/popular?language=en-US' },
  { title: 'Airing Today', kind: 'series', path: '/tv/airing_today?language=en-US' },
  { title: 'New Episodes', kind: 'series', path: '/tv/on_the_air?language=en-US' },
  { title: 'Top Rated TV', kind: 'series', path: '/tv/top_rated?language=en-US' },
  { title: 'Drama', kind: 'series', path: '/discover/tv?with_genres=18&sort_by=popularity.desc&language=en-US' },
  { title: 'Comedy', kind: 'series', path: '/discover/tv?with_genres=35&sort_by=popularity.desc&language=en-US' },
  { title: 'Crime', kind: 'series', path: '/discover/tv?with_genres=80&sort_by=popularity.desc&language=en-US' },
  { title: 'Sci-Fi & Fantasy', kind: 'series', path: '/discover/tv?with_genres=10765&sort_by=popularity.desc&language=en-US' },
  { title: 'Animation', kind: 'series', path: '/discover/tv?with_genres=16&sort_by=popularity.desc&language=en-US' },
  { title: 'Documentary', kind: 'series', path: '/discover/tv?with_genres=99&sort_by=vote_average.desc&vote_count.gte=50&language=en-US' },
  { title: 'Kids', kind: 'series', path: '/discover/tv?with_genres=10762&sort_by=popularity.desc&language=en-US' },
];

const traktMovieRails = [
  ['Trakt • Trending Now', '/movies/trending?limit=30'],
  ['Trakt • Most Anticipated', '/movies/anticipated?limit=30'],
  ['Trakt • Popular Community Picks', '/movies/popular?limit=30'],
] as const;

const traktTvRails = [
  ['Trakt • Trending Shows', '/shows/trending?limit=30'],
  ['Trakt • Most Anticipated Shows', '/shows/anticipated?limit=30'],
  ['Trakt • Popular Community Shows', '/shows/popular?limit=30'],
] as const;

export async function GET(request: NextRequest) {
  const requested = request.nextUrl.searchParams.get('kind') === 'series' ? 'series' : 'movie';
  const definitions = requested === 'series' ? tvRails : movieRails;
  const tmdbConfigured = Boolean(process.env.TMDB_BEARER_TOKEN);
  const traktConfigured = Boolean(process.env.TRAKT_CLIENT_ID);
  if (!tmdbConfigured && !traktConfigured) {
    return NextResponse.json({ configured: false, source: 'TMDB + Trakt', rails: [] });
  }

  const tmdbSettled = tmdbConfigured
    ? await Promise.allSettled(definitions.map(async (rail) => {
        const body = await tmdb(rail.path);
        return { title: rail.title, source: 'TMDB', items: mapItems(body?.results || [], rail.kind) };
      }))
    : [];

  const traktDefs = requested === 'series' ? traktTvRails : traktMovieRails;
  const traktSettled = traktConfigured
    ? await Promise.allSettled(traktDefs.map(async ([title, path]) => {
        const body = await trakt(path);
        return {
          title,
          source: 'Trakt',
          items: mapTraktItems(body || [], requested),
        };
      }))
    : [];

  const rails = [
    ...tmdbSettled.flatMap((result) => result.status === 'fulfilled' && result.value.items.length ? [result.value] : []),
    ...traktSettled.flatMap((result) => result.status === 'fulfilled' && result.value.items.length ? [result.value] : []),
  ];

  return NextResponse.json({
    configured: rails.length > 0,
    source: [tmdbConfigured ? 'TMDB' : null, traktConfigured ? 'Trakt' : null].filter(Boolean).join(' + '),
    rails,
  });
}
