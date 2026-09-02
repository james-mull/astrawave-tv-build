# AstraWave Master Rebuild Plan

## Goal

Rebuild the current AstraWave client around the Nuvio media core while preserving every AstraWave feature already designed or implemented. The resulting product should combine:

- Nuvio-derived media/discovery/player core
- Tuvora-style onboarding and simplicity
- TiviMate-quality Live TV and EPG
- AstraWave sports, Free TV, cloud sync, music/podcasts, profiles, and source aggregation

The current working Firebase-ready AstraWave beta remains intact on the existing branch while this rebuild is developed on `feature/nuvio-core-rebuild`.

## Non-negotiable feature preservation

The rebuild must retain or improve all of these AstraWave capabilities:

- Home
- Movies
- TV Shows
- Live TV
- Guide / EPG
- Sports Guide
- Music & Podcasts
- Discover
- Search
- My AstraWave
- Firebase accounts/cloud sync
- M3U
- Xtream Codes
- Multiple playlists
- Combined channel view
- AstraWave Free TV
- XMLTV
- source health checks
- failover/ranking
- sports-to-channel matching
- Stremio-compatible addons/catalogs
- TMDB catalog/metadata backbone
- personal media: Plex/Jellyfin/Emby/NAS/WebDAV roadmap
- authorized debrid/cloud source support
- profiles / kids / guest
- watchlists
- playback progress / Continue Watching
- favorite teams
- radio
- RSS podcasts
- video podcasts
- background audio / mini-player
- AstraWave+ entitlements
- QR TV setup/login roadmap
- future multiview, DVR for authorized sources, alerts, AI assistant

## Target navigation

### Mobile / tablet
Home | Movies | TV | Live TV | Guide | Sports | Music & Podcasts | Discover | Search | My AstraWave

### TV
Collapsed left rail that expands on focus:
Home | Movies | TV | Live TV | Guide | Sports | Music & Podcasts | Search | My AstraWave

## Architecture

### 1. Nuvio core
Use the pinned Nuvio upstream baseline for:

- Compose Multiplatform UI foundation
- movie/TV discovery flows
- title details
- search
- Media3 player
- playback history/progress integration points
- collections/watchlists
- addon/catalog concepts
- TV focus/navigation behavior

### 2. AstraWave catalog layer
TMDB is the default catalog/metadata backbone.

Built-in catalog rows:
- Trending Movies
- Popular Movies
- New Releases
- Top Rated
- Trending TV
- Airing Today
- New Episodes
- Genres
- Collections/franchises
- Cast/crew-driven discovery
- Personalized recommendations later

TMDB is metadata/catalog only; playback comes from authorized configured sources.

### 3. Stremio-compatible addon layer
Users can install compatible addons that may contribute:

- catalogs
- metadata
- search results
- subtitles
- authorized playback sources

Addon results are merged into AstraWave discovery while retaining provider attribution and source ranking.

### 4. Live TV source model
Three modes:

#### AstraWave Free TV
AstraWave-supplied curated channels from authorized/public feeds only.

Each channel record should include:
- channel id
- name
- stream URL
- backup URLs
- logo
- category
- country/region
- language
- EPG id
- rights status
- rights evidence URL / source
- last rights review
- last health check
- latency
- quality
- active/inactive state

Daily automation:
- test reachability
- measure latency
- follow redirects
- detect URL changes
- disable dead feeds
- restore recovered feeds
- choose healthy authorized backup
- refresh EPG mappings
- flag rights evidence requiring review
- regenerate published Free TV playlist

#### My IPTV
User-managed sources:
- M3U URL
- Xtream server + username + password
- multiple playlists/accounts
- per-source refresh
- favorites
- channel grouping
- user XMLTV support

#### Combined
Optional merged channel/guide experience combining:
- AstraWave Free TV
- My IPTV

Duplicate channels should normalize by tvg-id, channel name, region, and broadcaster identity.

## Guide / EPG

Build a TiviMate/Xfinity-style guide with:

- Now
- Tonight
- Tomorrow
- date picker
- favorites filter
- sports filter
- movies filter
- kids filter
- search
- recently watched
- mini-guide while playing
- channel groups
- fast D-pad navigation
- XMLTV merge across user and AstraWave sources

## Sports Guide

### Coverage roadmap
- NFL
- NBA
- MLB
- NHL
- NCAA football/basketball
- MLS
- Premier League
- Champions League
- major soccer leagues
- UFC
- boxing
- golf
- tennis
- F1
- NASCAR
- IndyCar
- WWE / sports-entertainment events where appropriate

### Event resolution flow
1. Fetch schedule/event metadata.
2. Identify expected broadcaster/network.
3. Search My IPTV for matching channels.
4. Search AstraWave Free TV for an authorized matching channel.
5. Health-check candidate streams.
6. Rank candidates by authorization, quality, latency, and source preference.
7. If playable, show Watch.
8. If not playable, still show schedule, teams, time, venue, broadcaster, and event info.

Users can follow:
- teams
- leagues
- players/competitors later
- individual events

Home rows should include:
- Sports Starting Soon
- Today’s Biggest Games
- Favorite Teams
- Live Sports Now

## Music & Podcasts

First-class section with tabs:

- For You
- Music
- Podcasts
- Video Podcasts
- Radio
- Library
- Search

Capabilities:
- RSS podcast subscriptions
- podcast episode playback
- video podcast listen/watch toggle
- internet radio URLs
- background audio
- queue
- mini-player
- favorites
- recently played
- Continue Listening
- Firebase sync for playback/progress/subscriptions
- personal music library connectors later

## Home experience

Premium streaming-style home, more polished than stock Nuvio:

1. Hero
2. Continue Watching
3. Continue Listening
4. Live Now
5. Sports Starting Soon
6. Today’s Biggest Games
7. Recently Watched Channels
8. Favorite Teams
9. Trending Movies
10. Trending TV
11. New Episodes
12. AstraWave Free TV
13. Music
14. Podcasts
15. Watch Tonight
16. Because You Watched
17. My Watchlist
18. Family Night
19. Hidden Gems
20. Movies on Live TV Tonight

Rows should be dynamic and hidden when empty.

## Profiles and household

Profile types:
- Adult
- Kids
- Guest

Per-profile separation:
- history
- watchlist
- Continue Watching
- favorites
- sports teams
- podcast subscriptions
- music/radio favorites
- recommendations

Kids roadmap:
- PIN
- age/content restrictions
- restricted live groups

## Firebase cloud layer

Keep Firebase as AstraWave cloud backend.

Sync:
- account/auth
- profiles
- watchlists
- playback progress
- favorite teams
- settings
- source configuration metadata
- podcast subscriptions
- music/radio favorites
- AstraWave+ entitlements

Do not put media streams through Firebase.

## Player

Nuvio/Media3 player remains the base.

Add AstraWave options:
- Auto Best
- Ask Every Time
- prefer resolution
- prefer HDR
- codec preference
- bitrate/data saver
- audio track
- subtitles
- source switcher
- Live TV previous/next channel
- mini-guide
- failover on dead live source
- continue playback progress

## Search

Unified search across:
- movies
- shows
- episodes
- people
- collections
- channels
- EPG programs
- sports events
- teams
- music
- podcasts
- radio
- My Stuff

Results should identify source/category clearly.

## Source ranking

Central resolver ranks candidates using:

1. authorization/rights eligibility
2. user preference
3. source health
4. latency
5. quality/resolution
6. direct-play compatibility
7. bitrate/codec preference
8. backup/failover availability

Never publish or auto-select a source that is not eligible for playback.

## UI direction

### Visual target
- Tuvora simplicity
- Nuvio content density where useful
- TiviMate-quality Live TV interaction
- AstraWave branding
- premium dark design
- large artwork
- clean typography
- obvious focus states
- minimal setup friction

### Onboarding
First launch:
1. Sign In
2. Create Account
3. Continue Without Account
4. Select/Create Profile
5. Optional Add IPTV
6. Enter Home

Free TV and TMDB catalogs should make the app useful even if the user skips source setup.

## TV setup

Roadmap:
- QR sign-in
- QR source setup
- configure IPTV from phone/web
- D-pad-first UI
- large focus targets
- avoid typing long Xtream credentials with remote

## AstraWave+ product layer

Free:
- core UI
- TMDB catalogs
- AstraWave Free TV
- guide metadata
- sports metadata
- one user IPTV source initially
- personal media basics
- one profile
- basic watchlist/progress
- basic addons

AstraWave+ candidate features:
- unlimited playlists
- merged guide
- advanced channel cleanup
- advanced failover
- cloud sync across devices
- multiple profiles
- advanced household recommendations
- sports alerts
- multiview
- authorized DVR roadmap
- web remote
- advanced addons/extensions
- backups
- AI assistant
- themes/layout customization

## Build phases

### Phase 0 — Protect current beta
- Keep existing Firebase-ready beta branch unchanged.
- Build new work only on `feature/nuvio-core-rebuild`.
- Maintain downloadable fallback APK until new branch reaches feature parity.

Exit gate: old beta remains installable and green.

### Phase 1 — Establish Nuvio baseline
- Pin upstream Nuvio commit.
- Confirm GPL notices/attribution.
- Build upstream Android APK in CI.
- Document upstream structure.
- Identify extension points for Home, source resolver, player, navigation, and data stores.

Exit gate: clean Nuvio baseline APK builds reproducibly.

### Phase 2 — AstraWave branding + navigation shell
- Rebrand package/app names/assets where license-compliant.
- Implement AstraWave navigation.
- Add responsive phone/tablet/TV layouts.
- Replace stock home shell with AstraWave row system.

Exit gate: branded app launches and all top-level destinations navigate correctly.

### Phase 3 — TMDB + native catalogs
- Wire AstraWave TMDB configuration.
- Build default movie/TV catalogs.
- Title pages use enriched metadata.
- Search uses TMDB + local/addon catalogs.

Exit gate: useful Movies/TV experience with no user source configured.

### Phase 4 — Stremio addon integration
- Preserve Nuvio addon concepts.
- Add addon management UI.
- Merge addon catalogs/search/subtitles/source candidates.
- Add provider attribution and source ranking.

Exit gate: installed compatible addons can extend catalogs without breaking native catalogs.

### Phase 5 — My IPTV
- M3U ingestion
- Xtream APIs
- multiple sources
- channel normalization
- favorites/recent
- XMLTV
- source refresh

Exit gate: user IPTV plays reliably and survives app restart.

### Phase 6 — AstraWave Free TV
- Build rights registry.
- Add authorized/public channel seeds.
- Daily health workflow.
- rights + health gating.
- backup selection.
- Free TV category UI.

Exit gate: only healthy rights-approved channels appear.

### Phase 7 — Combined Live TV + Guide
- Merge user + Free TV sources.
- deduplicate channels.
- merge EPG.
- build grid guide.
- mini-guide.
- now/next.
- filters.

Exit gate: smooth TV/phone guide with working playback and failover.

### Phase 8 — Sports Guide
- schedule ingestion
- broadcaster normalization
- channel matching
- My IPTV + Free TV resolver
- favorite teams
- home sports rows
- event detail screens

Exit gate: sports event can resolve to a healthy matching authorized/user channel where available.

### Phase 9 — Music & Podcasts
- RSS
- video podcasts
- radio
- background playback
- queue
- mini-player
- Continue Listening
- Firebase sync

Exit gate: audio experience works independently of movie/TV playback.

### Phase 10 — Firebase profiles/cloud sync
- move profile/watchlist/progress/favorites/settings bindings into Nuvio-derived client
- account persistence
- profile picker
- entitlement loading

Exit gate: same user sees synchronized state across compatible AstraWave clients.

### Phase 11 — Personal media + authorized cloud/debrid
- Plex
- Jellyfin
- Emby
- NAS/WebDAV roadmap
- authorized cloud/debrid connectors
- central resolver integration

Exit gate: personal/authorized sources appear as normal AstraWave candidates.

### Phase 12 — Premium UX / Tuvora-style setup
- streamlined first-run experience
- QR TV setup
- source management hub
- richer Home personalization
- cleaner settings
- error/empty/loading states

Exit gate: nontechnical user can install, reach content, and add IPTV without documentation.

### Phase 13 — AstraWave+ entitlements
- free vs premium feature gates
- entitlement sync
- upgrade surfaces
- no playback source is sold as copyrighted content

Exit gate: subscription logic gates software features, not unauthorized media.

### Phase 14 — Full QA
Test:
- Android phone
- Android tablet
- Android TV
- Fire TV
- web where applicable
- fresh install
- upgrade install
- login persistence
- navigation stability
- Live TV playback
- guide performance
- sports matching
- source failover
- TMDB
- addon catalogs
- Firebase sync
- podcasts/radio
- profile separation

Exit gate: no known critical crashes, dead navigation, fake operational claims, or untested core flows.

### Phase 15 — Release
- production signing
- release notes
- source-code/GPL compliance package
- production APK/AAB
- public download page
- privacy/terms
- monitoring

## Current implementation priorities

Order of work from the current branch:

1. Finish green pinned Nuvio baseline build.
2. Import/adapt Nuvio source into AstraWave-controlled branch/repo structure.
3. Rebrand shell and navigation.
4. Wire TMDB default catalogs.
5. Preserve/expand Stremio catalog/addon support.
6. Port existing AstraWave M3U/Xtream/XMLTV/source-ranking logic.
7. Connect AstraWave Free TV registry + daily checker.
8. Build combined guide.
9. Port sports resolver.
10. Add Music & Podcasts.
11. Port Firebase cloud state.
12. Add personal/cloud/debrid connectors.
13. Finish TV UX/QR onboarding.
14. Add entitlements.
15. Full device QA and production release.

## Definition of done

AstraWave is not complete until:

- every listed top-level module exists
- current AstraWave feature set is preserved
- Nuvio-derived flows are fully rebranded/integrated
- user M3U/Xtream works
- AstraWave Free TV is rights-gated and health-checked daily
- TMDB catalogs work by default
- compatible Stremio addons can add catalogs
- sports guide resolves against both user IPTV and Free TV
- Music & Podcasts works with background playback
- Firebase sync works
- TV navigation is D-pad friendly
- Android build is green
- real-device QA is complete
- GPL obligations are satisfied
