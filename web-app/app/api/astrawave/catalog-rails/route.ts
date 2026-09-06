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

export async function GET(request: NextRequest) {
  const requested = request.nextUrl.searchParams.get('kind') === 'series' ? 'series' : 'movie';
  const definitions = requested === 'series' ? tvRails : movieRails;
  const configured = Boolean(process.env.TMDB_BEARER_TOKEN);
  if (!configured) return NextResponse.json({ configured: false, source: 'TMDB', rails: [] });

  const settled = await Promise.allSettled(definitions.map(async (rail) => {
    const body = await tmdb(rail.path);
    return { title: rail.title, source: 'TMDB', items: mapItems(body?.results || [], rail.kind) };
  }));

  return NextResponse.json({
    configured: true,
    source: 'TMDB',
    rails: settled.flatMap((result) => result.status === 'fulfilled' && result.value.items.length ? [result.value] : []),
  });
}
