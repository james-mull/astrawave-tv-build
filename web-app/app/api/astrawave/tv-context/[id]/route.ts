import { NextResponse } from 'next/server';

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

type ListMembership = { title: string; category: string; reason: string; query?: string; genre?: string };

const genreLists: Record<string, string> = {
  Action: 'Action & Adventure', Adventure: 'Action & Adventure', Comedy: 'Comedy', Drama: 'Popular Drama',
  Crime: 'Crime & Mystery', Mystery: 'Crime & Mystery', 'Sci-Fi & Fantasy': 'Sci-Fi Right Now',
  SciFi: 'Sci-Fi Right Now', Fantasy: 'Fantasy Right Now', Horror: 'Horror Right Now', Family: 'Family TV Right Now',
  Animation: 'Animation Right Now', Romance: 'Romance Right Now', Documentary: 'Documentary Series',
};

const universeRules: Array<[RegExp, string, string]> = [
  [/star wars|mandalorian|ahsoka|andor|obi-wan/i, 'Star Wars Universe', 'Star Wars'],
  [/marvel|daredevil|loki|wanda|hawkeye|moon knight|secret invasion|agents of s\.h\.i\.e\.l\.d/i, 'Marvel TV Universe', 'Marvel'],
  [/dc|arrow|flash|supergirl|legends of tomorrow|gotham|peacemaker|titans|doom patrol/i, 'DC TV Universe', 'DC'],
  [/game of thrones|house of the dragon|knight of the seven kingdoms/i, 'Game of Thrones Universe', 'Game of Thrones'],
  [/walking dead|fear the walking dead|daryl dixon|dead city|world beyond/i, 'The Walking Dead Universe', 'Walking Dead'],
  [/yellowstone|1883|1923/i, 'Yellowstone Universe', 'Yellowstone'],
  [/star trek|picard|discovery|strange new worlds|lower decks/i, 'Star Trek Universe', 'Star Trek'],
  [/law & order|organized crime|special victims unit/i, 'Law & Order Universe', 'Law & Order'],
  [/chicago fire|chicago pd|chicago med/i, 'One Chicago Universe', 'Chicago'],
  [/ncis/i, 'NCIS Universe', 'NCIS'],
];

function addMembership(target: ListMembership[], title: string, category: string, reason: string, query?: string, genre?: string) {
  if (!target.some((x) => x.title === title)) target.push({ title, category, reason, query, genre });
}

function mapShow(item: any) {
  return {
    id: String(item.id),
    title: item.name || item.original_name || 'Untitled',
    firstAirDate: item.first_air_date || undefined,
    posterUrl: tmdbImage(item.poster_path),
    backdropUrl: tmdbImage(item.backdrop_path, 'w780'),
  };
}

async function inferLists(show: any): Promise<ListMembership[]> {
  const memberships: ListMembership[] = [];
  for (const genre of show.genres || []) {
    const raw = String(genre.name || '');
    const mapped = genreLists[raw];
    if (mapped) addMembership(memberships, mapped, 'Genre Charts', `TMDB genre: ${raw}`, undefined, raw.replace('Science Fiction & Fantasy', 'Sci-Fi'));
  }

  const year = Number(String(show.first_air_date || '').slice(0, 4));
  if (year >= 1950) {
    const decade = Math.floor(year / 10) * 10;
    addMembership(memberships, `${decade}s TV`, 'By Era', `Premiered in ${year}`, `${decade}s TV`);
  }

  const network = (show.networks || [])[0]?.name;
  if (network) addMembership(memberships, `${network} Shows`, 'Networks & Studios', `Aired on ${network}`, network);

  const creators = show.created_by || [];
  creators.slice(0, 2).forEach((person: any) => addMembership(memberships, person.name, 'Creators', `Created by ${person.name}`, person.name));

  const cast = (show.credits?.cast || []).slice(0, 6);
  cast.slice(0, 3).forEach((person: any) => addMembership(memberships, person.name, 'Cast', `Stars ${person.name}`, person.name));

  const universe = universeRules.find(([pattern]) => pattern.test(String(show.name || '')) || pattern.test(String(show.original_name || '')));
  if (universe) addMembership(memberships, universe[1], 'Connected Universes', `Part of the ${universe[1]}`, universe[2]);
  return memberships.slice(0, 18);
}

export async function GET(_: Request, context: { params: Promise<{ id: string }> }) {
  try {
    const { id } = await context.params;
    if (!/^\d+$/.test(id)) return NextResponse.json({ error: 'Invalid TV id' }, { status: 400 });

    const show = await tmdb(`/tv/${encodeURIComponent(id)}?append_to_response=credits,recommendations,similar&language=en-US`);
    if (!show) return NextResponse.json({ lists: [], related: {}, universe: null, networks: [] });

    const similarRaw = [ ...(show.recommendations?.results || []), ...(show.similar?.results || []) ];
    const seen = new Set<string>();
    const similar = similarRaw.filter((item: any) => {
      const key = String(item.id || '');
      if (!key || key === id || seen.has(key)) return false;
      seen.add(key); return true;
    }).slice(0, 18).map(mapShow);

    const creator = (show.created_by || [])[0];
    let creatorShows: any[] = [];
    if (creator?.id) {
      const credits = await tmdb(`/person/${encodeURIComponent(String(creator.id))}/combined_credits?language=en-US`);
      creatorShows = (credits?.crew || [])
        .filter((x: any) => x.media_type === 'tv' && String(x.id) !== id)
        .sort((a: any, b: any) => Number(b.popularity || 0) - Number(a.popularity || 0))
        .filter((x: any, index: number, array: any[]) => array.findIndex((y: any) => y.id === x.id) === index)
        .slice(0, 18).map(mapShow);
    }

    const lead = (show.credits?.cast || [])[0];
    let actorShows: any[] = [];
    if (lead?.id) {
      const credits = await tmdb(`/person/${encodeURIComponent(String(lead.id))}/combined_credits?language=en-US`);
      actorShows = (credits?.cast || [])
        .filter((x: any) => x.media_type === 'tv' && String(x.id) !== id)
        .sort((a: any, b: any) => Number(b.popularity || 0) - Number(a.popularity || 0))
        .filter((x: any, index: number, array: any[]) => array.findIndex((y: any) => y.id === x.id) === index)
        .slice(0, 18).map(mapShow);
    }

    const universeRule = universeRules.find(([pattern]) => pattern.test(String(show.name || '')) || pattern.test(String(show.original_name || '')));
    let universe: any = null;
    if (universeRule) {
      const data = await tmdb(`/search/tv?query=${encodeURIComponent(universeRule[2])}&include_adult=false&language=en-US&page=1`);
      const parts = (data?.results || []).filter((x: any) => String(x.id) !== id).slice(0, 20).map(mapShow);
      universe = { name: universeRule[1], query: universeRule[2], parts };
    }

    const networks = (show.networks || []).map((n: any) => ({ id: String(n.id), name: n.name, logoUrl: tmdbImage(n.logo_path, 'w300') }));

    return NextResponse.json({
      showId: String(show.id),
      lists: await inferLists(show),
      networks,
      universe,
      related: {
        similar,
        creator: creator ? { name: creator.name, shows: creatorShows } : null,
        actor: lead ? { name: lead.name, shows: actorShows } : null,
      },
    });
  } catch (error) {
    console.error('TV context error', error);
    return NextResponse.json({ error: 'AstraWave TV context unavailable' }, { status: 500 });
  }
}
