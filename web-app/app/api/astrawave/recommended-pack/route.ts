import { NextResponse } from 'next/server';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

const recommended = {
  stremio: [
    { id:'cinemeta', name:'Cinemeta', role:'Movie/TV catalog + metadata' },
    { id:'channels', name:'Stremio Channels', role:'Channel catalog' },
    { id:'watchhub', name:'WatchHub', role:'Provider availability' },
    { id:'publicdomain', name:'Public Domain Movies', role:'Reviewed open-media catalog' },
    { id:'opensubtitles', name:'OpenSubtitles', role:'Subtitles' },
  ],
  cloudstream: [
    { id:'recloudstream-official', name:'CloudStream Providers Repository', role:'Upstream reviewed repository; extensions still pass AstraWave eligibility checks' },
  ],
  live: [
    { id:'all-free', name:'All Free Sources' },
    { id:'astrawave-free', name:'AstraWave Free TV' },
    { id:'nexus-us', name:'IPTV Nexus US' },
    { id:'public-tv', name:'AstraWave Public TV' },
    { id:'free-tv', name:'Free-TV Public' },
    { id:'iptv-org-us', name:'IPTV.org Public' },
    { id:'iptv-org-sports', name:'IPTV.org Sports' },
    { id:'world-verified', name:'World IPTV Verified' },
  ],
  audio: [
    { id:'radio-browser', name:'Radio Browser', role:'Worldwide radio; no account required' },
    { id:'podcast-index', name:'Podcast Index', role:'Primary broad podcast directory when server credentials are configured', configured:Boolean(process.env.PODCASTINDEX_API_KEY && process.env.PODCASTINDEX_API_SECRET) },
    { id:'apple-audio-directory', name:'Apple audio directories', role:'Podcast fallback + legal music discovery/previews' },
  ],
  discovery: [
    { id:'tmdb', name:'TMDB', configured:Boolean(process.env.TMDB_BEARER_TOKEN), role:'Primary movie/TV metadata and artwork' },
    { id:'trakt', name:'Trakt', configured:Boolean(process.env.TRAKT_CLIENT_ID), role:'Community Trending / Most Anticipated / Popular rails' },
  ],
  providerCatalogs: ['Pluto TV','Plex','Tubi','Sling Freestream','Xumo Play','Samsung TV Plus'],
};

const advanced = {
  policy: 'Community Stremio/CloudStream repositories remain discoverable but opt-in. Stream-capable providers are never silently authorized by the Recommended Pack.',
  categories: ['Community Stremio repositories','Community CloudStream repositories','Customer M3U/Xtream','Customer personal-media servers','Optional debrid accounts','Future licensed music providers'],
};

export async function GET(){
  return NextResponse.json({
    name:'AstraWave Recommended Pack',
    version:1,
    zeroConfig:true,
    recommended,
    advanced,
  });
}
