export type MediaKind = 'movie' | 'series' | 'episode' | 'live' | 'sport' | 'song' | 'podcast';

export type CatalogItem = {
  id: string;
  kind: MediaKind;
  title: string;
  subtitle?: string;
  posterUrl?: string;
  backdropUrl?: string;
  streamUrl?: string;
  sources?: LiveStreamSource[];
  progressPercent?: number;
};

export type LiveStreamSource = {
  id: string;
  provider: string;
  streamUrl: string;
  group?: string;
};

export type SourceCandidate = {
  id: string;
  provider: string;
  url?: string;
  quality?: string;
  codec?: string;
  hdr?: string;
  bitrateKbps?: number;
  latencyMs?: number;
  uptimePercent?: number;
  direct?: boolean;
  licenseLabel?: string;
};

export type LiveChannel = {
  id: string;
  name: string;
  group?: string;
  logoUrl?: string;
  streamUrl?: string;
  sources?: LiveStreamSource[];
  now?: string;
  next?: string;
  sourceCount: number;
};

export type SportsEvent = {
  id: string;
  league: string;
  title: string;
  startTime: string;
  status: string;
  broadcaster?: string;
  channelId?: string;
};

const base = process.env.NEXT_PUBLIC_ASTRA_API_BASE?.replace(/\/$/, '') || '/api/astrawave';

async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(`${base}${path}`, { cache: 'no-store' });
  if (!response.ok) throw new Error(`AstraWave API ${response.status}`);
  return response.json() as Promise<T>;
}

export const AstraWaveApi = {
  home: () => getJson<{ rows: { title: string; items: CatalogItem[] }[] }>('/v1/home'),
  trendingMovies: () => getJson<CatalogItem[]>('/v1/catalog/movies/trending'),
  trendingShows: () => getJson<CatalogItem[]>('/v1/catalog/series/trending'),
  sources: (kind: string, id: string) => getJson<SourceCandidate[]>(`/v1/sources/${encodeURIComponent(kind)}/${encodeURIComponent(id)}`),
  liveChannels: () => getJson<LiveChannel[]>('/v1/live/channels'),
  guide: () => getJson<LiveChannel[]>('/v1/live/guide'),
  sportsToday: () => getJson<SportsEvent[]>('/v1/sports/today'),
  search: (query: string) => getJson<CatalogItem[]>(`/v1/search?q=${encodeURIComponent(query)}`),
};
