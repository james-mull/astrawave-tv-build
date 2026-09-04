# AstraWave 2.0 Master Roadmap

This document extends the existing AstraWave master rebuild plan. It is additive and does not remove any requirement from `ASTRAWAVE_MASTER_REBUILD_PLAN.md` or `ASTRAWAVE_V1_SOURCE_OF_TRUTH.md`.

## Product goal

Build AstraWave as a unified entertainment operating system rather than a collection of disconnected pages. Movies, TV, Live, Guide, Sports, Radio, Podcasts, Stremio-compatible addons, CloudStream-compatible repositories, personal media and user IPTV should all use one normalized source fabric and one playback/resolver model.

## 1. Source Fabric

Normalize all integrations into common objects:

- Catalog
- Title / Series / Season / Episode
- Live Channel
- EPG Program
- Sports Event
- Podcast / Episode
- Radio Station
- Playback Candidate
- Source Health

Every source must expose attribution, health, eligibility, latency and playback mode. Protected/provider-only sources must never be presented as direct native streams unless an authorized direct media endpoint exists.

## 2. VOD catalogs and discovery

Movies and TV must never render as empty pages. Required catalog stack:

1. AstraWave backend cache / last-good catalog
2. Cinemeta / Stremio-compatible metadata
3. TVmaze fallback for TV discovery
4. reviewed open/public VOD fallback
5. built-in emergency catalog

Required shelves:

- Featured
- Continue Watching
- Trending Movies
- Popular Movies
- New Releases
- Top Rated
- In Theaters
- Upcoming
- Trending TV
- Popular TV
- Airing Today
- New Episodes
- Genres
- Collections / Franchises
- Because You Watched
- Watch Tonight
- Hidden Gems
- Family Night
- Short Watch
- Free & Open Movies
- Recently Added From Addons
- Watchlist

Each shelf needs a See All page, loading state, cached fallback and real title details. Playback is shown only when a real authorized candidate resolves.

## 3. Automatic Stremio-compatible addon layer

Support reviewed starter addons automatically and user-installed manifests optionally.

Capabilities:

- manifest discovery
- catalog import
- metadata import
- season / episode import
- stream resolution
- subtitle import
- addon health and latency
- per-profile enable/disable
- catalog pin/hide/reorder
- automatic last-good cache
- disable repeatedly failing addons without deleting user configuration

Starter addons must be reviewed and eligible for automatic installation. Community/user addons remain opt-in.

## 4. CloudStream-compatible repository layer

Add a reviewed AstraWave CloudStream-compatible repository bridge with:

- repository metadata import
- extension discovery
- enable/disable
- update checking
- provider health diagnostics
- language/category filters
- source priority
- repository trust state

Reviewed AstraWave repositories may be enabled by default. Community repositories require user opt-in. Do not automatically install unreviewed extensions.

## 5. Universal VOD Resolver

Every movie or episode uses one resolver that queries eligible sources in parallel:

- enabled Stremio-compatible stream addons
- reviewed CloudStream-compatible providers
- AstraWave Free/Open VOD
- personal media
- user IPTV VOD
- authorized debrid/cloud integrations

Normalize and rank by:

1. authorization / eligibility
2. health
3. resolution
4. HDR / codec
5. bitrate
6. direct-play compatibility
7. language
8. latency
9. user preference
10. historical reliability

UI actions:

- Play Best
- Choose Source
- Retry Sources
- Explain No Source

Never show a fake Play action.

## 6. TiviMate-class Live TV and EPG

Guide is a flagship feature and must use a dense grid, not cards.

Required:

- sticky channel/logo column
- horizontal 30-minute time ruler
- current-time marker
- Now / Tonight / Tomorrow / date selector
- Favorites
- Recent Channels
- source/group filters
- search
- sports filter
- movies filter
- kids filter
- current/next program
- program details
- channel preview
- last-channel shortcut
- instant channel switching
- D-pad position memory
- XMLTV merge and aliasing
- EPG coverage diagnostics
- stale-if-error caching
- start-over/catch-up only where provider supports it

Free source filters must be interactive:

- All
- AstraWave Free TV
- Pluto TV
- Plex
- Tubi
- Sling Freestream
- Xumo Play
- Samsung TV Plus
- My IPTV
- Favorites
- Sports

Source filters change the actual visible lineup. Native Watch only appears for eligible direct streams or user-authorized IPTV.

## 7. Free and premium-channel strategy

AstraWave may continuously discover candidate channels from public registries and official broadcaster/FAST sources, but default publication requires rights/public-distribution evidence and health checks.

Discovery pipeline:

Discover -> classify -> verify evidence -> health probe -> dedupe -> EPG map -> publish.

Premium subscription channels enter AstraWave only through user-authorized sources such as M3U, Xtream, provider APIs, TV Everywhere, tuner devices or other legitimate account integrations. Do not ship unauthorized premium cable/sports playlists.

## 8. Sports Command Center and Scoreboard

Sports becomes a first-class flagship surface.

Required sports data per event where available:

- league
- home / away competitors
- team marks
- records / rankings
- scheduled time
- live status
- period / quarter / inning / set
- live score
- final score
- venue
- broadcaster/network
- source availability
- favorite-team state

Sports pages:

- Live Now
- Starting Soon
- Today
- Tonight
- Tomorrow
- Favorite Teams
- Scoreboard rail
- League tabs
- Game Day detail

Sports cards must show score hierarchy clearly. Live games show a LIVE badge and current score. Completed games show FINAL. Upcoming games show start time/countdown.

Sports Guide resolution:

1. fetch event / score data
2. identify broadcaster/network
3. search user-authorized IPTV
4. search AstraWave eligible free channels
5. rank healthy candidates
6. show Watch / Choose Source when playable
7. keep score/event details visible even when no stream is available

Home must include Live Sports Now, Sports Starting Soon and Today's Biggest Games when data exists.

## 9. Sports Multiview

- 2-up / 3-up / 4-up
- active audio selector
- one-tap maximize
- D-pad pane switching
- low-memory/decoder safeguards
- favorite-team quick launch
- score overlay option

## 10. Radio

Radio is a first-class section:

- Local
- News
- Talk
- Sports
- Rock
- Country
- Hip-Hop
- Electronic
- Classical
- World
- Favorites
- Recently Played
- Search by country/city/genre
- background playback
- lock-screen controls
- sleep timer

Radio plays natively from direct eligible stream URLs.

## 11. Podcasts

Podcast experience:

- Trending
- News
- Comedy
- True Crime
- Sports
- Technology
- Business
- Science
- History
- Kids
- Video Podcasts
- Followed Shows
- Latest Episodes
- Continue Listening

Podcast -> Episodes -> Episode Details -> Play. Episode playback comes from publisher RSS enclosure URLs and remains inside AstraWave.

## 12. Universal Search

Search across:

- movies
- TV
- episodes
- channels
- guide programs
- sports events / teams
- radio
- podcasts
- personal media
- addon catalogs

Every result identifies category and source.

## 13. Premium UI overhaul for every page

Visual direction:

- near-black cinematic background
- restrained AstraWave purple accents
- large artwork/backdrops
- clear hierarchy
- minimal explanatory copy
- richer cards and badges
- polished empty/loading/error states
- obvious focus/selected states
- consistent native player dock
- TV-safe 10-foot typography
- mobile one-handed navigation

Page-specific enhancements:

### Home
Hero, Continue Watching, Live Now, Sports Now, Trending, Free TV, Podcasts, Radio, Watchlist, addon catalogs.

### Movies
Featured hero, genre chips, multiple shelves, See All pages, source availability badges, Play Best / Choose Source.

### TV
Featured hero, Airing Today, New Episodes, Popular, genres, seasons/episodes, progress badges.

### Live
Source/group chips, favorites, recent channels, Now/Next, logos, health state, mini guide.

### Guide
Full EPG grid with sticky channel column, time ruler, current-time marker, source filters and program detail drawer.

### Sports
Scoreboard rail, live score cards, league tabs, favorite teams, Game Day detail, Watch / Multiview actions.

### Radio
Genre/local filters, station artwork, favorites, Now Playing dock.

### Podcasts
Category shelves, show detail, episode list, progress, playback speed, Continue Listening.

### Search
Grouped results with filters and source badges.

### My AstraWave / More
Profiles, Watchlist, My IPTV, Addons, CloudStream repositories, Stremio addons, personal media, diagnostics, settings.

## 14. Source intelligence and failover

Score every candidate by eligibility, latency, quality, codec compatibility, bitrate and historical success.

For live playback:

- bounded retry
- automatic alternate-source failover
- preserve channel/program context
- subtle source-switched notification
- never endless-spinner

## 15. Daily maintenance

Daily backend jobs should:

- test free TV feeds
- refresh rights evidence
- refresh EPG mappings
- test Stremio manifests
- test CloudStream repositories/extensions
- test radio stations
- validate podcast feeds
- cache catalog snapshots
- detect dead URLs
- update source health scores

## 16. Offline / degraded mode

Every major surface follows:

network -> fresh cache -> stale cache -> built-in fallback.

A temporary source outage must never blank Home, Movies, TV, Live, Radio or Podcasts.

## 17. Build waves

### Wave 1 — Foundation repair
- permanent non-empty catalogs
- clickable Live/Guide source filters
- TiviMate-style EPG
- sports score/status support
- enhanced page layouts and loading/error states

### Wave 2 — Source Fabric
- normalized source/catalog/channel/playback models
- health/ranking service

### Wave 3 — Stremio engine
- manifest/catalog/meta/stream/subtitle support
- reviewed starter addon pack

### Wave 4 — CloudStream bridge
- reviewed repository support
- extension health/update system

### Wave 5 — Universal VOD Resolver
- Play Best / Choose Source
- authorized debrid/personal/IPTV/addon candidates

### Wave 6 — Live + Sports expansion
- verified public channel discovery pipeline
- XMLTV/EPG enrichment
- sports-to-channel matching
- live scores

### Wave 7 — Premium UX
- cinematic page redesign
- profiles/watchlists
- universal search
- multiview
- accessibility and TV focus polish

### Wave 8 — Release QA
- Android phone/tablet
- Android TV
- Fire TV
- 1k-10k channel playlist tests
- real playback/failover tests
- signed release candidate only after all blockers pass

## Release rule

AstraWave 2.0 is not complete because screens exist or code compiles. Promotion requires real catalogs, real playback where eligible, score/EPG data, source failover, responsive UI, TV focus behavior, and mandatory device QA.