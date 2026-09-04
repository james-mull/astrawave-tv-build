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

type ListMembership = { title: string; category: string; reason: string };

const genreLists: Record<string, string> = {
  Action: 'Action', Comedy: 'Comedy', Horror: 'Horror', 'Science Fiction': 'Sci-Fi', Fantasy: 'Fantasy',
  Thriller: 'Thrillers', Crime: 'Crime', Romance: 'Romance', Drama: 'Drama', War: 'War', Western: 'Westerns',
  Animation: 'Animation', Family: 'Family', Documentary: 'Documentaries', Music: 'Music & Musicals',
};

const directorLists = new Set([
  'Christopher Nolan', 'Steven Spielberg', 'Martin Scorsese', 'Quentin Tarantino', 'Denis Villeneuve',
  'David Fincher', 'James Cameron', 'Greta Gerwig', 'Jordan Peele',
]);

const actorLists = new Set([
  'Tom Cruise', 'Denzel Washington', 'Leonardo DiCaprio', 'Keanu Reeves', 'Meryl Streep',
  'Tom Hanks', 'Cate Blanchett', 'Brad Pitt',
]);

const franchiseRules: Array<[RegExp, string]> = [
  [/harry potter|wizarding world/i, 'Harry Potter in Order'], [/star wars/i, 'Star Wars'],
  [/avengers|iron man|captain america|thor|marvel/i, 'Marvel Universe'], [/batman|superman|wonder woman|aquaman|dc/i, 'DC Worlds'],
  [/fast.*furious/i, 'Fast Saga'], [/mission.*impossible/i, 'Mission: Impossible'], [/john wick/i, 'John Wick'],
  [/conjuring|annabelle|the nun/i, 'Conjuring Universe'], [/insidious/i, 'Insidious'], [/jurassic/i, 'Jurassic'],
  [/lord of the rings|hobbit|middle-earth/i, 'Middle-earth'], [/james bond|007/i, 'James Bond'], [/rocky|creed/i, 'Rocky & Creed'],
  [/matrix/i, 'The Matrix'], [/alien|predator/i, 'Alien & Predator'], [/terminator/i, 'Terminator'],
  [/planet of the apes/i, 'Planet of the Apes'], [/bourne/i, 'Bourne'],
];

function addMembership(target: ListMembership[], title: string, category: string, reason: string) {
  if (!target.some((x) => x.title === title)) target.push({ title, category, reason });
}

function inferMemberships(movie: any): ListMembership[] {
  const memberships: ListMembership[] = [];
  for (const genre of movie.genres || []) {
    const mapped = genreLists[String(genre.name || '')];
    if (mapped) addMembership(memberships, mapped, 'Genre', `TMDB genre: ${genre.name}`);
  }

  const year = Number(String(movie.release_date || '').slice(0, 4));
  if (year >= 1950) {
    const decade = Math.floor(year / 10) * 10;
    const title = decade === 2020 ? '2020s So Far' : `${String(decade).slice(2)}s${decade < 2000 ? ' Classics' : ''}`;
    addMembership(memberships, title, 'By Decade', `Released in ${year}`);
  }

  const collectionName = String(movie.belongs_to_collection?.name || '');
  if (collectionName) {
    const match = franchiseRules.find(([pattern]) => pattern.test(collectionName) || pattern.test(String(movie.title || '')));
    addMembership(memberships, match?.[1] || collectionName, 'Franchises & Universes', `Part of ${collectionName}`);
  }

  const crew = movie.credits?.crew || [];
  crew.filter((x: any) => x.job === 'Director').forEach((person: any) => {
    if (directorLists.has(person.name)) addMembership(memberships, person.name, 'Filmmakers', `Directed by ${person.name}`);
  });

  const cast = (movie.credits?.cast || []).slice(0, 12);
  cast.forEach((person: any) => {
    if (actorLists.has(person.name)) addMembership(memberships, person.name, 'Actors', `Stars ${person.name}`);
  });

  const companies = (movie.production_companies || []).map((x: any) => String(x.name || ''));
  if (companies.some((x: string) => /pixar/i.test(x))) addMembership(memberships, 'Pixar', 'Studios, Awards & Specialty', 'Produced by Pixar');
  if (companies.some((x: string) => /dreamworks/i.test(x))) addMembership(memberships, 'DreamWorks', 'Studios, Awards & Specialty', 'Produced by DreamWorks');
  if (companies.some((x: string) => /a24/i.test(x))) addMembership(memberships, 'A24', 'Studios, Awards & Specialty', 'Produced/distributed by A24');
  if (companies.some((x: string) => /studio ghibli/i.test(x))) addMembership(memberships, 'Studio Ghibli', 'Studios, Awards & Specialty', 'Produced by Studio Ghibli');

  if ((movie.runtime || 0) > 0 && movie.runtime <= 120) addMembership(memberships, 'Under 2 Hours', 'Moods & Occasions', `${movie.runtime} minute runtime`);
  return memberships.slice(0, 18);
}

export async function GET(_: Request, context: { params: Promise<{ id: string }> }) {
  try {
    const { id } = await context.params;
    if (!/^\d+$/.test(id)) return NextResponse.json({ error: 'Invalid movie id' }, { status: 400 });
    const movie = await tmdb(`/movie/${encodeURIComponent(id)}?append_to_response=credits`);
    if (!movie) return NextResponse.json({ lists: [], collection: null });

    let collection: any = null;
    const collectionId = movie.belongs_to_collection?.id;
    if (collectionId) {
      const raw = await tmdb(`/collection/${encodeURIComponent(String(collectionId))}`);
      if (raw) {
        collection = {
          id: String(raw.id),
          name: raw.name,
          posterUrl: tmdbImage(raw.poster_path),
          backdropUrl: tmdbImage(raw.backdrop_path, 'w1280'),
          parts: (raw.parts || [])
            .map((part: any) => ({
              id: String(part.id), title: part.title || part.name || 'Untitled',
              releaseDate: part.release_date || undefined,
              posterUrl: tmdbImage(part.poster_path), backdropUrl: tmdbImage(part.backdrop_path, 'w780'),
            }))
            .sort((a: any, b: any) => String(a.releaseDate || '9999').localeCompare(String(b.releaseDate || '9999'))),
        };
      }
    }

    return NextResponse.json({
      movieId: String(movie.id),
      lists: inferMemberships(movie),
      collection,
    });
  } catch (error) {
    console.error('Movie context error', error);
    return NextResponse.json({ error: 'AstraWave movie context unavailable' }, { status: 500 });
  }
}
