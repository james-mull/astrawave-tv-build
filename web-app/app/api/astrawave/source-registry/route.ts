import { NextResponse } from 'next/server';

const USA_TV_MANIFEST='https://848b3516657c-usatv.baby-beamup.club/manifest.json';
const NETFLIX_CATALOG_MANIFEST='https://7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club/bmZ4LGRucCxhbXAsYXRwLGhibSxwbXAscGNwLGhsdSxjcnUsbmZrLGN0cyxkcGUsc2hhLGlxaSxiYm86OlVTOjE3ODg3MzE0NjkxNzQ6MDowOlVT/manifest.json';

const stremio = [
  ['cinemeta','Cinemeta','https://v3-cinemeta.strem.io/manifest.json',true,true,'Catalog + metadata'],
  ['channels','Stremio Channels','https://v3-channels.strem.io/manifest.json',true,true,'Channel catalog'],
  ['watchhub','WatchHub','https://watchhub.strem.io/manifest.json',true,true,'Provider availability'],
  ['publicdomain','Public Domain Movies','https://caching.stremio.net/publicdomainmovies.now.sh/manifest.json',true,true,'Reviewed open-media catalog'],
  ['opensubtitles','OpenSubtitles','https://opensubtitles-v3.strem.io/manifest.json',true,true,'Subtitles'],
  ['customer-usatv','USA TV',USA_TV_MANIFEST,true,false,'Customer-supplied hardcoded Live/TV/Sports discovery manifest. Playback remains authorization and health gated.'],
  ['customer-netflix-catalog','Netflix Catalog',NETFLIX_CATALOG_MANIFEST,true,false,'Customer-supplied hardcoded movie/TV catalog manifest. Catalog metadata may load by default; playback requires a separately eligible authorized source.'],
].map(([id,name,url,enabled,reviewed,note])=>({id,name,url,kind:'stremio',enabledByDefault:enabled,reviewed,note}));

const cloudstream = [
  ['recloudstream-official','CloudStream Providers Repository','https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json',true,true,['Upstream providers'],'Upstream repository; extensions still pass AstraWave eligibility checks.'],
  ['self-similarity-mega','Mega Repository','https://raw.githubusercontent.com/self-similarity/MegaRepo/builds/repo.json',false,false,['Dailymotion','Invidious','Twitch','Repository aggregator'],'Hardcoded community repository. Opt-in only; extensions are not automatically trusted or executed.'],
  ['cloudstream-community-aggregator','CloudStream Community Aggregator','https://raw.githubusercontent.com/crxnkzziszxmbi3-sys/cloudstream-custom-repo/main/repo.json',false,false,['Community aggregator'],'Opt-in only; individual plugins are not automatically trusted.'],
  ['hexated','Hexated Extensions','https://raw.githubusercontent.com/hexated/cloudstream-extensions-hexated/builds/repo.json',false,false,['Community extensions'],'Community repository; opt-in and health-checked before eligible playback.'],
  ['phisher98','Phisher98 Extensions','https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/refs/heads/builds/repo.json',false,false,['AllWish','DoraBash','Animesalt','AnimeCloud','Jellyfin','Ringz','ShowFlix','StremioAddon','StremioX','XDMovies','Yflix','YTS'],'Hardcoded community repository. Opt-in only.'],
  ['dogior','doGior’s Had Enough','https://raw.githubusercontent.com/doGior/doGiorsHadEnough/refs/heads/builds/repo.json',false,false,['Arte','CB01','DaddyLive','IPTV','Nebula','Simkl','StreamingCommunity','Torrentio','TV'],'Hardcoded community repository. Opt-in only; stream-capable extensions remain authorization gated.'],
  ['cakestwix','CakesTwix','https://raw.githubusercontent.com/CakesTwix/cloudstream-extensions-uk/master/repo.json',false,false,['AnimeON','KlonTV','Teleportal','UAFlix','UASerial','UFDub'],'Hardcoded community repository. Opt-in only.'],
  ['saimuel','Saimuel Repo','https://raw.githubusercontent.com/saimuelbr/saimuelrepo/refs/heads/main/builds/repo.json',false,false,['MegaFlix','GoFlix','UltraCine','Streamberry','TopFilmes','Anroll','Doramas','FilmesOn'],'Hardcoded community repository. Opt-in only.'],
  ['netmirror','NetMirror Repo','https://raw.githubusercontent.com/Sushan64/NetMirror-Extension/refs/heads/builds/Netflix.json',false,false,['Hotstar','Disney+','Netflix','Prime Video'],'Hardcoded community repository. Provider branding does not imply authorization; opt-in only.'],
  ['king-xtream','King Xtream IPTV','https://pastebin.com/raw/Cd2g2tfz',false,false,['XtreamIPTV'],'Hardcoded Xtream-oriented repository. User credentials/authorization are required.'],
  ['cs-karma','CS-Karma Extensions','https://raw.githubusercontent.com/Kraptor123/cs-Karma/refs/heads/master/repo.json',false,false,['CinemaCity','DocumentaryArea','AnimeAV','BasketballReplays','F1FullRaces','Filmatek','Footballia','Gnulahd','Iwatchtheoffice','KissKH','Streamed','Supercartoons','TVGarden','WatchWrestling'],'Hardcoded community repository. Opt-in only.'],
  ['reflex','Reflex Repo','https://raw.githubusercontent.com/Reflex755/ReflexRepo/refs/heads/builds/repo.json',false,false,['DiviCast','LibraryOfLadev'],'Normalized from cloudstreamrepo:// form; opt-in only.'],
  ['luna712','Luna712','https://raw.githubusercontent.com/Luna712/Luna712-CloudStream-Extensions/28885d17ceb7f24782b732b6056085c14c1fd027/repo.json',false,false,['Internet Archive'],'Hardcoded community repository. Opt-in only.'],
  ['cnc-verse','CNC Verse Repository','https://raw.githubusercontent.com/NivinCNC/CNCVerse-Cloud-Stream-Extension/refs/heads/builds/CNC.json',false,false,['CastleTV','Cricify','DoFlix','MovieBox','HDO','StreamFlix','Watch32','GoldenAudiobook','Tamilian'],'Hardcoded community repository. Opt-in only.'],
  ['megix','Megix Repo','https://raw.githubusercontent.com/SaurabhKaperwan/CSX/builds/CS.json',false,false,['Bollyflix','VegaMovies','World4uFree','CineStream','Extractors','MoviesDrive','Moviesmod'],'Hardcoded community repository. Opt-in only.'],
  ['indostream','IndoStream Repo','https://raw.githubusercontent.com/TeKuma25/IndoStream/builds/repo.json',false,false,['Dutamovie','Funmovieslix','IndoTV','LayarKaca','Nekopoi','Neonime','Nimegami','Rebahin'],'Normalized from cloudstreamrepo:// form; opt-in only.'],
  ['cloudx','CloudX Repository','https://raw.githubusercontent.com/Asm0d3usX/CloudX/builds/repo.json',false,false,['Dutamovie','Filmlokal','Funmovieslix','Indomax','Moviebox','Ngefilm','Nomat','Pusatfilm','Pusatmovie','Savefilm','WGFilm21'],'Hardcoded community repository. Opt-in only.'],
  ['adam-knight-mega','Adam Knight Mega Repo','https://raw.githubusercontent.com/admknight/CloudstreamExtensions/builds/repo.json',false,false,['Community mega repository'],'Large community repository; plugins must be individually validated before eligible playback.'],
  ['codegeasse','Codegeasse CloudStream Repo','https://raw.githubusercontent.com/codegeasse1/codegeasse-cloudstream-repos/builds/repo.json',false,false,['Community extensions'],'Community repository; opt-in and health-checked before eligible playback.'],
].map(([id,name,url,enabled,reviewed,extensions,note])=>({id,name,url,kind:'cloudstream',enabledByDefault:enabled,reviewed,extensions,note}));

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
    policy:'Catalog metadata may be enabled by default. Customer-supplied and community stream-capable manifests/repositories remain unreviewed, opt-in for execution, and subject to explicit authorization plus health checks.'
  });
}
