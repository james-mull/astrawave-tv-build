export type MediaKind = 'movie' | 'series' | 'episode' | 'live' | 'sport' | 'song' | 'podcast';

export type LiveStreamSource = {
  id: string;
  provider: string;
  sourceId?: string;
  streamUrl: string;
  group?: string;
};

export type CatalogItem = {
  id: string;
  kind: MediaKind;
  title: string;
  subtitle?: string;
  posterUrl?: string;
  backdropUrl?: string;
  overview?: string;
  score?: number;
  popularity?: number;
  genreIds?: number[];
  streamUrl?: string;
  sources?: LiveStreamSource[];
  progressPercent?: number;
};

export type CatalogRail = {
  title: string;
  source?: string;
  addonId?: string;
  manifestUrl?: string;
  items: CatalogItem[];
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

export type GuideProgram = { title: string; start: number; stop: number; category?: string };

export type LiveChannel = {
  id: string;
  tvgId?: string;
  name: string;
  group?: string;
  logoUrl?: string;
  streamUrl?: string;
  sources?: LiveStreamSource[];
  now?: string;
  next?: string;
  programs?: GuideProgram[];
  sourceCount: number;
};

export type LiveData = {
  activeSource: string;
  sourceOptions: { id: string; name: string }[];
  channels: LiveChannel[];
  stats: { channels: number; epgLinked: number; epgGeneratedAt?: string | null; epgScheduledChannels?: number };
  failures?: { source: string; error: string }[];
};

export type SportsEvent = {
  id: string;
  league: string;
  sport?: string;
  title: string;
  startTime: string;
  status: string;
  broadcaster?: string;
  homeTeam?: string;
  awayTeam?: string;
  badge?: string;
  date?: string;
};

export type RegistryEntry = {
  id: string;
  name: string;
  url?: string | null;
  kind: string;
  enabledByDefault: boolean;
  reviewed: boolean;
  note: string;
};

export type SourceRegistry = {
  stremio: RegistryEntry[];
  cloudstream: RegistryEntry[];
  live: RegistryEntry[];
  providerCatalogs: RegistryEntry[];
  policy: string;
};

export type TitlePerson = { id: string; name: string; role?: string; profileUrl?: string };
export type TitleTrailer = { key: string; name: string; type: string; official: boolean; url: string };
export type TitleDetails = {
  id: string;
  kind: 'movie' | 'series';
  title: string;
  overview: string;
  tagline?: string;
  posterUrl?: string;
  backdropUrl?: string;
  releaseDate?: string;
  runtimeMinutes?: number | null;
  rating?: string | null;
  score: number;
  voteCount: number;
  genres: string[];
  status?: string;
  seasons?: number;
  episodes?: number;
  homepage?: string;
  cast: TitlePerson[];
  crew: TitlePerson[];
  trailers: TitleTrailer[];
  related: CatalogItem[];
};

const base = process.env.NEXT_PUBLIC_ASTRA_API_BASE?.replace(/\/$/, '') || '/api/astrawave';

async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(`${base}${path}`, { cache: 'no-store' });
  if (!response.ok) throw new Error(`AstraWave API ${response.status}`);
  return response.json() as Promise<T>;
}

export const AstraWaveApi = {
  home: () => getJson<{ rows: CatalogRail[] }>('/v1/home'),
  catalogRails: (kind: 'movie' | 'series') => getJson<{ configured: boolean; source: string; rails: CatalogRail[] }>(`/catalog-rails?kind=${kind}`),
  addonRails: () => getJson<{ reviewedOnly: boolean; manifests: string[]; addons: any[]; rails: CatalogRail[] }>('/addon-rails'),
  sourceRegistry: () => getJson<SourceRegistry>('/source-registry'),
  liveData: (source = 'all-free') => getJson<LiveData>(`/live-data?source=${encodeURIComponent(source)}`),
  sportsData: () => getJson<{ dates: string[]; events: SportsEvent[]; leagues: string[]; count: number }>('/sports-data'),
  titleDetails: (kind: 'movie' | 'series', id: string) => getJson<{ configured: boolean; details?: TitleDetails }>(`/title/${kind}/${encodeURIComponent(id)}`),
  trendingMovies: () => getJson<CatalogItem[]>('/v1/catalog/movies/trending'),
  trendingShows: () => getJson<CatalogItem[]>('/v1/catalog/series/trending'),
  sources: (kind: string, id: string) => getJson<SourceCandidate[]>(`/v1/sources/${encodeURIComponent(kind)}/${encodeURIComponent(id)}`),
  liveChannels: async () => (await getJson<LiveData>('/live-data?source=all-free')).channels,
  guide: () => getJson<LiveData>('/live-data?source=all-free'),
  sportsToday: async () => (await getJson<{ events: SportsEvent[] }>('/sports-data')).events,
  audioTrending: async () => {
    const response = await fetch('https://de1.api.radio-browser.info/json/stations/topvote/36?hidebroken=true', { cache: 'no-store' });
    if (!response.ok) throw new Error(`Radio directory ${response.status}`);
    const stations = await response.json() as any[];
    return stations.slice(0, 36).filter((station) => station.url_resolved || station.url).map((station) => ({
      id: String(station.stationuuid || station.changeuuid || station.name),
      kind: 'podcast' as const,
      title: String(station.name || 'Public radio'),
      subtitle: [station.country, station.tags?.split(',')?.[0]].filter(Boolean).join(' • '),
      posterUrl: station.favicon || undefined,
      streamUrl: station.url_resolved || station.url,
    }));
  },
  search: (query: string) => getJson<CatalogItem[]>(`/v1/search?q=${encodeURIComponent(query)}`),
};