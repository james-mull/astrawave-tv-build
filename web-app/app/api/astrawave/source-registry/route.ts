import { NextResponse } from 'next/server';

const stremio = [
  ['cinemeta','Cinemeta','https://v3-cinemeta.strem.io/manifest.json',true,'Catalog + metadata'],
  ['channels','Stremio Channels','https://v3-channels.strem.io/manifest.json',true,'Channel catalog'],
  ['watchhub','WatchHub','https://watchhub.strem.io/manifest.json',true,'Provider availability'],
  ['publicdomain','Public Domain Movies','https://caching.stremio.net/publicdomainmovies.now.sh/manifest.json',true,'Reviewed open-media catalog'],
  ['opensubtitles','OpenSubtitles','https://opensubtitles-v3.strem.io/manifest.json',true,'Subtitles'],
].map(([id,name,url,enabled,note])=>({id,name,url,kind:'stremio',enabledByDefault:enabled,reviewed:true,note}));

const cloudstream = [
  ['recloudstream-official','CloudStream Providers Repository','https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json',true,true,'Upstream repository; extensions still pass AstraWave eligibility checks.'],
  ['cloudstream-community-aggregator','CloudStream Community Aggregator','https://raw.githubusercontent.com/crxnkzziszxmbi3-sys/cloudstream-custom-repo/main/repo.json',false,false,'Opt-in only; individual plugins are not automatically trusted.'],
  ['hexated','Hexated Extensions','https://raw.githubusercontent.com/hexated/cloudstream-extensions-hexated/builds/repo.json',false,false,'Community repository; opt-in and health-checked.'],
  ['phisher98','Phisher98 Extensions','https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/builds/repo.json',false,false,'Community repository; opt-in and health-checked.'],
  ['cs-karma','CS-Karma Extensions','https://raw.githubusercontent.com/Kraptor123/Cs-Karma/builds/repo.json',false,false,'Community repository; opt-in and health-checked.'],
  ['adam-knight-mega','Adam Knight Mega Repo','https://raw.githubusercontent.com/admknight/CloudstreamExtensions/builds/repo.json',false,false,'Large community repository; plugins must be individually validated.'],
  ['codegeasse','Codegeasse CloudStream Repo','https://raw.githubusercontent.com/codegeasse1/codegeasse-cloudstream-repos/builds/repo.json',false,false,'Community repository; opt-in and health-checked.'],
].map(([id,name,url,enabled,reviewed,note])=>({id,name,url,kind:'cloudstream',enabledByDefault:enabled,reviewed,note}));

const live = [
  ['all-free','All Free Sources',null,'Virtual merged view'],
  ['astrawave-free','AstraWave Free TV','https://raw.githubusercontent.com/james-mull/astrawave-tv-build/feature/nuvio-core-rebuild/astrawave-free-tv/astrawave-free-tv.m3u','Reviewed AstraWave public lineup'],
  ['nexus-us','IPTV Nexus US','https://dearbulut.github.io/iptv/playlists/country/us.m3u','US public lineup'],
  ['public-tv','AstraWave Public TV','https://raw.githubusercontent.com/freecasthub/public-iptv/main/playlist.m3u','Public broadcaster lineup'],
  ['free-tv','Free-TV Public','https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8','Public/open channels'],
  ['iptv-org-us','IPTV.org Public','https://iptv-org.github.io/iptv/countries/us.m3u','US public channels'],
  ['iptv-org-sports','IPTV.org Sports','https://iptv-org.github.io/iptv/categories/sports.m3u','Sports category'],
  ['world-verified','World IPTV Verified','https://romaxa55.github.io/world_ip_tv/output/index.m3u','Worldwide public lineup'],
].map(([id,name,url,note])=>({id,name,url,kind:'live',enabledByDefault:true,reviewed:true,note}));

const providerCatalogs = ['Pluto TV','Plex','Tubi','Sling Freestream','Xumo Play','Samsung TV Plus'].map((name)=>({
  id:name.toLowerCase().replace(/[^a-z0-9]+/g,'-'),name,kind:'provider-catalog',enabledByDefault:true,reviewed:true,note:'Catalog/availability integration; direct playback only when an authorized compatible source is available.'
}));

export async function GET(){
  return NextResponse.json({
    stremio,
    cloudstream,
    live,
    providerCatalogs,
    policy:'Metadata/catalog integrations may be enabled by default. Stream-capable community extensions remain subject to explicit authorization and health checks.'
  });
}
