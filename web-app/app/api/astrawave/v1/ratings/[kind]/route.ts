import { NextRequest, NextResponse } from 'next/server';

async function tmdb(path: string) {
  const token = process.env.TMDB_BEARER_TOKEN;
  if (!token) return null;
  const res = await fetch(`https://api.themoviedb.org/3${path}`, {
    headers: { Authorization: `Bearer ${token}`, accept: 'application/json' },
    cache: 'no-store',
  });
  if (!res.ok) return null;
  return res.json();
}

async function resolveTmdbId(kind: 'movie' | 'series', id: string): Promise<string | null> {
  if (/^\d+$/.test(id)) return id;
  if (!/^tt\d+$/i.test(id)) return null;
  const found = await tmdb(`/find/${encodeURIComponent(id)}?external_source=imdb_id`);
  const results = kind === 'movie' ? found?.movie_results : found?.tv_results;
  return results?.[0]?.id ? String(results[0].id) : null;
}

async function resolveRating(kind: 'movie' | 'series', requestedId: string) {
  const tmdbId = await resolveTmdbId(kind, requestedId);
  if (!tmdbId) return { id: requestedId, rating: null, source: 'tmdb', resolved: false };

  if (kind === 'movie') {
    const data = await tmdb(`/movie/${encodeURIComponent(tmdbId)}/release_dates`);
    const us = data?.results?.find((x: any) => x.iso_3166_1 === 'US');
    const certifications = (us?.release_dates || [])
      .map((x: any) => String(x.certification || '').trim())
      .filter(Boolean);
    const rating = certifications.find((x: string) => ['G', 'PG', 'PG-13', 'R', 'NC-17'].includes(x))
      || certifications[0]
      || null;
    return { id: requestedId, rating, source: 'tmdb', resolved: true, tmdbId };
  }

  const data = await tmdb(`/tv/${encodeURIComponent(tmdbId)}/content_ratings`);
  const us = data?.results?.find((x: any) => x.iso_3166_1 === 'US');
  return { id: requestedId, rating: us?.rating || null, source: 'tmdb', resolved: true, tmdbId };
}

export async function GET(request: NextRequest, context: { params: Promise<{ kind: string }> }) {
  const { kind: rawKind } = await context.params;
  const kind = rawKind === 'movie' ? 'movie' : rawKind === 'series' ? 'series' : null;
  if (!kind) return NextResponse.json({ error: 'Unsupported media kind' }, { status: 400 });

  const ids = Array.from(new Set(
    (request.nextUrl.searchParams.get('ids') || '')
      .split(',')
      .map((value) => value.trim())
      .filter(Boolean),
  )).slice(0, 40);
  if (!ids.length) return NextResponse.json([]);

  const results = await Promise.all(ids.map((id) => resolveRating(kind, id)));
  return NextResponse.json(results);
}
