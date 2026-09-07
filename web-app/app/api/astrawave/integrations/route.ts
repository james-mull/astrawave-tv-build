import { NextResponse } from 'next/server';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

type IntegrationState = {
  id: string;
  name: string;
  category: 'catalog' | 'audio' | 'tracking' | 'live' | 'personal-media' | 'device';
  configured: boolean;
  required: boolean;
  fallback?: string;
  note: string;
};

export async function GET() {
  const integrations: IntegrationState[] = [
    {
      id: 'tmdb',
      name: 'TMDB',
      category: 'catalog',
      configured: Boolean(process.env.TMDB_BEARER_TOKEN),
      required: false,
      fallback: 'Browser-configured TMDB token / existing catalog sources',
      note: 'Primary movie/TV metadata, artwork and discovery rails.',
    },
    {
      id: 'trakt',
      name: 'Trakt',
      category: 'tracking',
      configured: Boolean(process.env.TRAKT_CLIENT_ID),
      required: false,
      fallback: 'TMDB rails remain available',
      note: 'Adds community Trending, Most Anticipated and Popular discovery rails. User OAuth sync is a separate optional phase.',
    },
    {
      id: 'podcast-index',
      name: 'Podcast Index',
      category: 'audio',
      configured: Boolean(process.env.PODCASTINDEX_API_KEY && process.env.PODCASTINDEX_API_SECRET),
      required: false,
      fallback: 'Apple Podcasts directory',
      note: 'Broad podcast discovery and Podcasting 2.0-aware catalog metadata.',
    },
    {
      id: 'radio-browser',
      name: 'Radio Browser',
      category: 'audio',
      configured: true,
      required: false,
      note: 'Worldwide internet-radio directory; no AstraWave secret required.',
    },
    {
      id: 'apple-audio-directory',
      name: 'Apple audio directories',
      category: 'audio',
      configured: true,
      required: false,
      note: 'Podcast directory fallback and legal music preview/catalog discovery. Full commercial playback requires a licensed/user-authorized provider.',
    },
    {
      id: 'sportsdb',
      name: 'TheSportsDB',
      category: 'live',
      configured: Boolean(process.env.THESPORTSDB_API_KEY),
      required: false,
      note: 'Sports schedule/enrichment provider when configured.',
    },
    {
      id: 'customer-live',
      name: 'Customer Live TV sources',
      category: 'live',
      configured: Boolean(process.env.ASTRAWAVE_LIVE_M3U_URL || process.env.ASTRAWAVE_LIVE_M3U_URLS),
      required: false,
      fallback: 'Reviewed/public AstraWave Free TV sources',
      note: 'Server-level optional M3U overrides; customer device M3U/Xtream sources remain device/profile scoped.',
    },
    {
      id: 'jellyfin',
      name: 'Jellyfin',
      category: 'personal-media',
      configured: true,
      required: false,
      note: 'Customer-authorized personal server support is already available on-device. Official SDK migration can be layered in without changing user ownership boundaries.',
    },
  ];

  return NextResponse.json({
    configuredCount: integrations.count ? undefined : undefined,
    active: integrations.filter(item => item.configured).length,
    total: integrations.length,
    integrations,
  });
}
