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

type RelatedMovie = {
  id: string;
  title: string;
  releaseDate?: string;
  posterUrl?: string;
  backdropUrl?: string;
};

type ListMembership = {
  id: string;
  title: string;
  category: string;
  reason: string;
  query?: string;
  genre?: string;
};

function mapMovie(item: any): RelatedMovie | null {
  if (!item?.id) return null;
  const title = item.title || item.name;
  if (!title) return null;
  return {
    id: String(item.id),
    title,
    releaseDate: item.release_date || undefined,
    posterUrl: tmdbImage(item.poster_path),
    backdropUrl: tmdbImage(item.backdrop_path, 'w1280'),
  };
}

function uniqueMovies(items: any[], currentId?: string, limit = 18): RelatedMovie[] {
  const seen = new Set<string>();
  const out: RelatedMovie[] = [];
  for (const raw of items || []) {
    const movie = mapMovie(raw);
    if (!movie || movie.id === currentId || seen.has(movie.id)) continue;
    seen.add(movie.id);
    out.push(movie);
    if (out.length >= limit) break;
  }
  return out;
}

function slug(value: string) {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
}

function addList(target: ListMembership[], item: ListMembership) {
  if (!target.some((x) => x.id === item.id)) target.push(item);
}

function inferMemberships(movie: any, director?: any, leadActor?: any): ListMembership[] {
  const lists: ListMembership[] = [];
  const genreMap: Record<string, string> = {
    Action: 'Action', Adventure: 'Action', Animation: 'Animation', Comedy: 'Comedy', Crime: 'Crime',
    Documentary: 'Documentary', Drama: 'Drama', Family: 'Family', Fantasy: 'Fantasy', Horror: 'Horror',
    Romance: 'Romance', 'Science Fiction': 'Sci-Fi', Thriller: 'Thriller', War: 'War', Western: 'Western',
    Music: 'Music', Mystery: 'Thriller',
  };

  for (const genre of (movie.genres || []).slice(0, 4)) {
    const raw = String(genre.name || '');
    if (!raw) continue;
    const display = genreMap[raw] || raw;
    addList(lists, {
      id: `genre-${slug(display)}`,
      title: display,
      category: 'Genre',
      reason: `TMDB classifies this movie as ${raw}.`,
      genre: display,
    });
  }

  const year = Number(String(movie.release_date || '').slice(0, 4));
  if (year >= 1950) {
    const decade = Math.floor(year / 10) * 10;
    addList(lists, {
      id: `decade-${decade}`,
      title: `${decade}s`,
      category: 'By Decade',
      reason: `Released in ${year}.`,
      query: `${decade}s movies`,
    });
  }

  if (Number(movie.runtime || 0) > 0 && Number(movie.runtime) <= 100) {
    addList(lists, {
      id: 'under-100-minutes', title: 'Under 100 Minutes', category: 'Moods & Occasions',
      reason: `Runtime is ${movie.runtime} minutes.`, query: 'great movies under 100 minutes',
    });
  }
  if (Number(movie.vote_average || 0) >= 7.5 && Number(movie.vote_count || 0) >= 500) {
    addList(lists, {
      id: 'highly-rated', title: 'Highly Rated', category: 'Trending & Fresh',
      reason: 'Strong audience rating with a substantial vote count.', query: movie.title,
    });
  }
  if (director?.name) {
    addList(lists, {
      id: `director-${director.id}`, title: director.name, category: 'Filmmakers',
      reason: `Directed by ${director.name}.`, query: director.name,
    });
  }
  if (leadActor?.name) {
    addList(lists, {
      id: `actor-${leadActor.id}`, title: leadActor.name, category: 'Actors',
      reason: `${leadActor.name} is among the top-billed cast.`, query: leadActor.name,
    });
  }
  const studio = (movie.production_companies || [])[0];
  if (studio?.name) {
    addList(lists, {
      id: `studio-${studio.id}`, title: studio.name, category: 'Studios',
      reason: `Produced by ${studio.name}.`, query: studio.name,
    });
  }
  if (movie.belongs_to_collection?.name) {
    addList(lists, {
      id: `collection-${movie.belongs_to_collection.id}`,
      title: movie.belongs_to_collection.name,
      category: 'Franchises & Universes',
      reason: 'TMDB identifies this movie as part of an official collection.',
      query: movie.belongs_to_collection.name,
    });
  }
  return lists.slice(0, 14);
}

const universeMatchers = [
  { name: 'Marvel Cinematic Universe', terms: ['avengers', 'iron man', 'captain america', 'thor', 'guardians of the galaxy', 'black panther', 'ant-man', 'doctor strange', 'spider-man'] },
  { name: 'DC Worlds', terms: ['batman', 'superman', 'wonder woman', 'aquaman', 'justice league', 'suicide squad'] },
  { name: 'Star Wars Universe', terms: ['star wars', 'rogue one', 'solo: a star wars story'] },
  { name: 'Wizarding World', terms: ['harry potter', 'fantastic beasts'] },
  { name: 'Fast Saga', terms: ['fast & furious', 'fast and furious', 'fast five', 'f9', 'fast x', 'hobbs & shaw'] },
  { name: 'Jurassic Universe', terms: ['jurassic park', 'jurassic world'] },
  { name: 'Rocky & Creed', terms: ['rocky', 'creed'] },
  { name: 'Middle-earth', terms: ['lord of the rings', 'the hobbit'] },
  { name: 'Conjuring Universe', terms: ['the conjuring', 'annabelle', 'the nun'] },
  { name: 'Alien & Predator', terms: ['alien', 'predator', 'prometheus'] },
];

async function editorialUniverse(movie: any) {
  const haystack = `${movie.title || ''} ${movie.belongs_to_collection?.name || ''}`.toLowerCase();
  const match = universeMatchers.find((u) => u.terms.some((term) => haystack.includes(term)));
  if (!match) return null;
  const searches = await Promise.all(match.terms.slice(0, 5).map(async (term) => {
    try {
      const data = await tmdb(`/search/movie?query=${encodeURIComponent(term)}&include_adult=false&language=en-US&page=1`);
      return data?.results || [];
    } catch {
      return [];
    }
  }));
  const movies = uniqueMovies(searches.flat(), String(movie.id), 20)
    .sort((a, b) => (a.releaseDate || '').localeCompare(b.releaseDate || ''));
  return movies.length ? { name: match.name, editorial: true, parts: movies } : null;
}

export async function GET(_: Request, context: { params: Promise<{ id: string }> }) {
  try {
    const { id } = await context.params;
    if (!/^\d+$/.test(id)) return NextResponse.json({ lists: [], collection: null, related: {} });

    const movie = await tmdb(`/movie/${encodeURIComponent(id)}?language=en-US&append_to_response=credits,recommendations,similar`);
    if (!movie) return NextResponse.json({ lists: [], collection: null, related: {} });

    const director = (movie.credits?.crew || []).find((p: any) => p.job === 'Director');
    const leadActor = (movie.credits?.cast || [])[0];

    const [directorCredits, actorCredits, collectionData, universe] = await Promise.all([
      director?.id ? tmdb(`/person/${director.id}/movie_credits?language=en-US`).catch(() => null) : Promise.resolve(null),
      leadActor?.id ? tmdb(`/person/${leadActor.id}/movie_credits?language=en-US`).catch(() => null) : Promise.resolve(null),
      movie.belongs_to_collection?.id ? tmdb(`/collection/${movie.belongs_to_collection.id}?language=en-US`).catch(() => null) : Promise.resolve(null),
      editorialUniverse(movie),
    ]);

    const collection = collectionData ? {
      id: String(collectionData.id),
      name: collectionData.name || movie.belongs_to_collection?.name || 'Movie Collection',
      posterUrl: tmdbImage(collectionData.poster_path),
      backdropUrl: tmdbImage(collectionData.backdrop_path, 'w1280'),
      parts: uniqueMovies(collectionData.parts || [], undefined, 40)
        .sort((a, b) => (a.releaseDate || '').localeCompare(b.releaseDate || '')),
    } : null;

    const recommendations = uniqueMovies([...(movie.recommendations?.results || []), ...(movie.similar?.results || [])], id, 18);
    const byDirector = uniqueMovies((directorCredits?.crew || []).filter((x: any) => x.job === 'Director'), id, 18)
      .sort((a, b) => (b.releaseDate || '').localeCompare(a.releaseDate || ''));
    const withActor = uniqueMovies(actorCredits?.cast || [], id, 18)
      .sort((a, b) => (b.releaseDate || '').localeCompare(a.releaseDate || ''));

    return NextResponse.json({
      movieId: String(movie.id),
      lists: inferMemberships(movie, director, leadActor),
      collection,
      universe,
      related: {
        similar: recommendations,
        director: director ? { id: String(director.id), name: director.name, movies: byDirector } : null,
        actor: leadActor ? { id: String(leadActor.id), name: leadActor.name, movies: withActor } : null,
      },
    });
  } catch (error) {
    console.error('Movie context error', error);
    return NextResponse.json({ lists: [], collection: null, related: {} }, { status: 200 });
  }
}
