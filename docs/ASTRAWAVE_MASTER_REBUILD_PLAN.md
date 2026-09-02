# AstraWave Master Rebuild Plan

## Goal

Rebuild the current AstraWave client around the Nuvio media core while preserving every AstraWave feature already designed or implemented. The resulting product should combine:

- Nuvio-derived media/discovery/player core
- Tuvora-style onboarding and simplicity
- TiviMate-quality Live TV and EPG
- AstraWave sports, Free TV, cloud sync, music/podcasts, profiles, and source aggregation
- a premium, modern, visually consistent UI across phone, tablet, TV, Fire TV, and web
- a best-in-class intelligence layer that unifies discovery, playback, sports, audio, IPTV, addons, personal media, and household features

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
- QR TV setup/login
- multiview
- authorized DVR/catch-up roadmap
- alerts
- AI assistant
- backup/restore
- device handoff
- phone-as-remote
- diagnostics dashboard
- automatic playlist cleanup
- parental controls
- privacy/local-only mode

## Best-in-class differentiators

### Universal Watch
Every playable item gets one consistent primary action. AstraWave checks all eligible configured sources, pre-validates them, ranks candidates, and opens the best healthy option when Auto Best is enabled. Ask Every Time remains available.

Candidate sources can include:
- My IPTV
- AstraWave Free TV
- compatible addons
- personal media
- authorized cloud/debrid
- other supported eligible providers

Ranking factors:
1. authorization/eligibility
2. user preference
3. health
4. latency
5. quality
6. codec/device compatibility
7. bitrate/data preference
8. direct-play support
9. backup availability

### Universal Search
One search surface across Movies, Shows, Episodes, People, Collections, Live Channels, EPG, Sports, Teams, Music, Podcasts, Video Podcasts, Radio, personal media, addon catalogs, and My Stuff.

### Adaptive Smart Home
Home changes intelligently using time of day, day of week, profile, recent activity, unfinished media, upcoming sports, favorite teams, live programming, and household context.

Examples:
- Morning: news, weather, podcasts, radio
- Afternoon: Live TV, sports starting soon, Continue Listening
- Evening: Watch Tonight, new episodes, movies, live sports
- Weekend: Family Night, sports, binge suggestions, longer movies

### AI Entertainment Assistant
Natural-language assistant grounded in content the user can actually access.

Examples:
- “Find me a funny movie under two hours.”
- “What NBA games can I watch tonight?”
- “Show me something the whole family will like.”
- “Find a 90s thriller I haven’t watched.”
- “What live news is available right now?”

### Household Recommendation Engine
Profiles remain separate while shared modes such as Family Night combine selected profiles, respect age restrictions, avoid recently watched titles, and rank content by likely group satisfaction.

### Automatic Stream Failover
For eligible live streams, detect failures and automatically switch to a healthy backup where possible while preserving playback context.

### Pre-play Source Validation
Before presenting a source as playable, verify reachability, protocol compatibility, short-lived health state, and latency where practical.

### Playback Intelligence
Auto-select resolution, HDR/SDR, codec, bitrate, audio format, audio language, subtitles, and data usage based on device capabilities, network quality, and profile preferences.

### Universal Continue Watching / Listening
Unified progress system for movies, episodes, eligible personal media, podcasts, video podcasts, and long-form audio.

### Catalog Builder
Users can build manual or smart catalogs such as 90s Thrillers, Oscar Winners, Mind-Bending Movies, Kids Saturday, Family Night, My Team Games, 90-Minute Movies, Hidden Gems, and Unwatched Favorites.

### Advanced Stremio Catalog Control
Addon catalogs can be hidden, reordered, pinned to Home, grouped, locally renamed, searched, and restricted by profile.

### Optional Trakt-style History / Scrobbling
Support external watch history, lists, ratings, watched state, and progress without replacing AstraWave’s native Firebase/local history.

### Advanced Subtitles
Preferred languages, automatic selection, delay, size, position, style, background opacity, and remembered settings per profile.

### Picture-in-Picture and Background Playback
Picture-in-Picture for eligible video/live content and persistent background audio/mini-player for music, radio, and podcasts where the platform permits.

### Device Handoff
Continue on TV, Continue on Phone, and Continue on Web with title and playback position transfer.

### Phone as Remote
Phone can act as TV keyboard, search interface, channel browser, queue controller, source manager, and remote.

### Backup / Restore
Back up profiles, playlists, addon setup, channel mappings, favorites, settings, and layouts, then restore to another supported device.

### Diagnostics Dashboard
Power-user dashboard for playlist health, failing channels, EPG coverage, addon health, source latency, API status, Firebase sync, Free TV last check, and last successful refresh.

### Parental Controls
Kids profiles, PIN protection, age restrictions, Live TV group restrictions, settings lock, and optional quiet/bedtime restrictions.

### Privacy Mode
Optional local-only profiles with no cloud history or cloud progress sync.

## Premium UI / UX Design System

Beautiful, clean UI is a release requirement. Functionality is not considered complete if it works but looks unfinished, cluttered, inconsistent, or confusing.

### Visual identity
- Premium dark-first design
- AstraWave-owned visual identity rather than stock Nuvio styling
- cinematic artwork with controlled gradients and overlays
- restrained accent usage
- high contrast without harsh visual noise
- modern typography with clear hierarchy
- rounded, consistent component geometry
- intentional whitespace
- minimal borders
- subtle depth/elevation where useful
- polished icons with one coherent icon family
- no developer-looking raw URLs, IDs, debug labels, or technical jargon in normal user flows

### Design tokens
Create shared tokens for:
- spacing
- corner radius
- typography sizes/weights
- elevation
- focus scale
- opacity
- animation duration/easing
- card aspect ratios
- artwork sizes
- grid gaps
- safe areas
- content widths

No screen should invent its own unrelated spacing or card system.

### Typography
Use a small, consistent type scale:
- Display / hero
- Page title
- Section title
- Card title
- Body
- Metadata
- Caption

Priorities:
- readable from TV distance
- clean on mobile
- no oversized blocks of text
- metadata visually subordinate to titles/actions

### Artwork treatment
- high-quality poster/backdrop/logo hierarchy
- consistent fallback artwork
- skeleton placeholders during load
- smart cropping
- gradient scrims to preserve readability
- avoid stretched or low-resolution art
- cache frequently used artwork
- logos where they improve scanning, especially channels and sports teams

### Home UI
Home should feel cinematic but not crowded.

Requirements:
- one strong hero region
- concise hero metadata
- obvious primary Watch action
- horizontal content rows with consistent spacing
- row titles aligned consistently
- dynamic rows hidden when empty
- Continue Watching includes visible progress
- live cards identify Live state and current program
- sports cards clearly show teams, time/status, broadcaster availability, and Watch state
- avoid overwhelming users with too many rows before personalization

### Card system
Standardized card families:
- movie poster
- TV poster
- landscape feature
- live channel
- EPG program
- sports event
- team
- podcast
- podcast episode
- radio station
- music/album/playlist
- source/provider

Cards should share consistent focus, press, loading, unavailable, and selected states.

### Motion and interaction
Motion should make the app feel premium without slowing it down.

Use:
- subtle focus scaling on TV
- smooth row scrolling
- short crossfades
- hero transitions
- mini-player expansion
- source-switch transitions
- loading skeletons instead of blank screens

Avoid:
- excessive bouncing
- long transitions
- distracting autoplay motion
- animations that delay navigation

### TV / Fire TV UX
TV must be designed separately rather than merely stretching the phone UI.

Requirements:
- D-pad-first navigation
- highly visible focus state
- predictable focus movement
- focus never disappears
- no unreachable controls
- large readable text
- generous TV-safe spacing
- collapsed navigation rail that expands on focus
- fast Back behavior
- minimal dialogs requiring typing
- QR setup for credentials when possible
- mini-guide while watching
- channel surfing optimized for remote use
- focus restoration after returning from player/details

### Phone UI
- bottom navigation for primary destinations where appropriate
- thumb-friendly controls
- compact metadata
- swipe/scroll behavior that feels native
- Picture-in-Picture support where eligible
- mini-player that does not obstruct navigation
- one-handed source/settings flows where practical

### Tablet UI
- adaptive navigation rail
- larger grids
- master/detail layouts where valuable
- no phone-width content floating awkwardly in a large screen

### Web UI
- responsive desktop/tablet/mobile breakpoints
- keyboard navigation
- hover and focus states
- media rows that take advantage of wider screens
- consistent visual design with Android rather than a separate-looking product

### Live TV / Guide UI
Target TiviMate/Xfinity-level usability.

Requirements:
- clean channel column
- clear current-time indicator
- strong current-program state
- readable program blocks
- Now/Next information
- favorite/category filters
- channel logos without clutter
- fast vertical/horizontal navigation
- mini-guide overlay
- full-screen player with unobtrusive controls
- quick return to previous channel
- source/failover state accessible but not constantly visible

### Sports UI
Sports should have its own visual language while staying within AstraWave’s design system.

Event cards should show:
- league
- team logos/names
- time or live status
- score when available
- broadcaster/source availability
- Watch button when resolvable

Game Day pages should provide a polished command-center layout rather than a generic metadata page.

### Music & Podcasts UI
- large cover art where appropriate
- persistent mini-player
- queue access
- simple Listen/Watch toggle for video podcasts
- waveform/progress only where useful
- clean episode lists
- easy subscription/favorite controls
- background playback state visible throughout app

### Source setup UI
Hide complexity behind simple choices.

Top-level source setup:
- AstraWave Free TV — already available
- Add My IPTV
- Add Addon
- Connect Personal Media
- Connect Cloud/Authorized Service

Advanced settings remain available but should not dominate onboarding.

### Loading / Empty / Error states
Every major screen must have intentional:
- loading skeleton
- empty state
- offline state
- provider unavailable state
- no playable source state
- retry action
- configuration-required state

Never show a blank page because an API returned nothing.

### Accessibility
- high-contrast focus states
- scalable text where practical
- screen-reader labels
- meaningful content descriptions
- minimum touch targets
- do not rely on color alone for state
- subtitle accessibility options

### Performance perception
UI must feel immediate even when data is still loading.

Targets:
- show cached Home data instantly when available
- progressive image loading
- skeletons instead of spinners for content grids
- prefetch likely details/screens
- lazy-load long rows
- cache EPG and metadata appropriately
- avoid blocking navigation on source health checks

### Customization
Optional user customization without compromising design consistency:
- row ordering
- hidden Home rows
- compact/comfortable content density
- preferred start page
- favorite channel groups
- theme accent options later
- TV grid density

### Visual QA gate
Before release, every top-level page must be visually reviewed on:
- phone portrait
- phone landscape
- tablet
- 1080p TV
- 4K TV
- Fire TV remote navigation
- desktop web

No page passes QA with:
- clipped text
- overlapping controls
- inconsistent margins
- weak focus indicators
- raw debug data
- stretched artwork
- blank sections
- unreadable metadata
- inconsistent card sizing
- broken responsive layouts

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
- Personalized recommendations
- Watch Tonight
- Family Night
- Hidden Gems

TMDB is metadata/catalog only; playback comes from eligible configured sources.

### 3. Stremio-compatible addon layer
Users can install compatible addons that may contribute catalogs, metadata, search results, subtitles, and eligible playback sources. Results retain provider attribution and pass through central source ranking.

### 4. Live TV source model
Three modes:

#### AstraWave Free TV
AstraWave-supplied curated channels from authorized/public feeds only.

Each channel record includes channel ID, name, stream/backup URLs, logo, category, country/region, language, EPG ID, rights status/evidence, rights review date, health date, latency, quality, and active state.

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
User-managed M3U/Xtream sources, multiple accounts/playlists, refresh controls, favorites, grouping, and XMLTV.

#### Combined
Merged optional experience combining AstraWave Free TV and My IPTV with deduplication by tvg-id, name, region, and broadcaster identity.

## Guide / EPG

Build a TiviMate/Xfinity-style guide with Now, Tonight, Tomorrow, date picker, favorites, sports, movies, kids, search, recent channels, mini-guide, groups, fast D-pad navigation, and merged XMLTV.

## Sports Guide

Coverage roadmap includes NFL, NBA, MLB, NHL, NCAA, MLS, Premier League, Champions League, major soccer, UFC, boxing, golf, tennis, F1, NASCAR, IndyCar, and appropriate sports-entertainment events.

Event resolution flow:
1. Fetch schedule metadata.
2. Identify broadcaster/network.
3. Search My IPTV.
4. Search AstraWave Free TV.
5. Health-check candidates.
6. Rank eligible candidates.
7. Show Watch when playable.
8. Otherwise show accurate event/broadcaster information without pretending playback exists.

Add Game Day mode, favorite-team pages, alerts, and multiview roadmap.

## Music & Podcasts

Tabs:
- For You
- Music
- Podcasts
- Video Podcasts
- Radio
- Library
- Search

Capabilities include RSS, video podcast Listen/Watch, radio, background playback, queue, mini-player, favorites, recently played, Continue Listening, Firebase sync, and personal music connectors later.

## Home experience

Premium streaming-style Home:
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

Rows are dynamic and hidden when empty.

## Profiles and household

Adult, Kids, and Guest profiles with separate history, watchlist, progress, favorites, teams, subscriptions, audio favorites, and recommendations.

Kids controls include PIN, age/content restrictions, restricted Live TV groups, and protected settings.

## Firebase cloud layer

Sync account/auth, profiles, watchlists, playback progress, favorite teams, settings, source metadata, subscriptions, music/radio favorites, backup metadata, and AstraWave+ entitlements. Do not route media streams through Firebase.

## Player

Nuvio/Media3 player remains the base with Auto Best, Ask Every Time, resolution/HDR/codec/bitrate preferences, audio/subtitles, source switcher, previous/next Live TV channel, mini-guide, failover, PiP, progress, and device handoff.

## Search

Unified search across all media, live, sports, audio, people, collections, sources, EPG, and My Stuff.

## Source ranking

Central resolver ranks by authorization/eligibility, user preference, health, latency, quality, direct-play compatibility, bitrate/codec preference, and backup availability.

Never publish or auto-select an ineligible source.

## Onboarding

First launch:
1. Sign In
2. Create Account
3. Continue Without Account
4. Select/Create Profile
5. Optional Add IPTV
6. Optional Add Addons/Personal Media
7. Enter Home

Free TV and TMDB catalogs make the app useful immediately.

## TV setup

QR sign-in, QR source setup, phone/web configuration, D-pad-first UI, large focus targets, and minimal remote typing.

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

AstraWave+ candidates:
- unlimited playlists
- merged guide
- advanced channel cleanup
- advanced failover
- cloud sync across devices
- multiple profiles
- household recommendations
- sports alerts
- multiview
- authorized DVR/catch-up features
- web/phone remote
- advanced addons/extensions
- backups
- AI assistant
- advanced customization

## Build phases

### Phase 0 — Protect current beta
Keep existing Firebase-ready beta branch unchanged and maintain fallback APK.

### Phase 1 — Establish Nuvio baseline
Pin upstream Nuvio, preserve GPL notices, build reproducibly, document extension points.

### Phase 2 — AstraWave design system + branding + navigation
- establish visual tokens and component library
- rebrand shell/assets where license-compliant
- build premium Home/card/hero/nav system
- implement phone/tablet/TV/web adaptive layouts
- implement TV focus system
- add loading/empty/error states

Exit gate: branded app launches, all destinations work, and UI passes first visual QA on phone and TV.

### Phase 3 — TMDB + native catalogs
Wire default catalogs, enriched detail screens, search, recommendations, and visually polished metadata pages.

### Phase 4 — Stremio addon integration
Preserve/expand addon management, catalog controls, subtitles, attribution, and resolver integration.

### Phase 5 — My IPTV
M3U, Xtream, multiple sources, cleanup, favorites, recent channels, XMLTV, refresh, diagnostics.

### Phase 6 — AstraWave Free TV
Rights registry, authorized seeds, daily health checking, backup selection, EPG mapping, polished Free TV browsing.

### Phase 7 — Combined Live TV + Guide
Merge/dedupe sources, merged EPG, full grid guide, mini-guide, Now/Next, filters, automatic failover, instant-ish switching optimization.

### Phase 8 — Sports Guide + Game Day
Schedules, broadcaster normalization, channel matching, favorites, alerts, Game Day UI, multiview foundation.

### Phase 9 — Music & Podcasts
RSS, video podcasts, radio, background playback, queue, mini-player, Continue Listening, polished audio UI.

### Phase 10 — Firebase profiles/cloud sync
Profile picker, persistence, watchlists/progress/favorites/settings, privacy/local-only mode, entitlement loading, device handoff state.

### Phase 11 — Personal media + authorized cloud/debrid
Plex, Jellyfin, Emby, NAS/WebDAV roadmap, authorized cloud/debrid connectors, resolver integration.

### Phase 12 — Intelligence layer
Universal Watch, Universal Search, Smart Home, AI assistant, household recommendations, Catalog Builder, pre-play validation, playback intelligence.

### Phase 13 — Connected-device experience
QR setup, phone remote, backup/restore, handoff, TV credential management, optional web remote.

### Phase 14 — AstraWave+ entitlements
Free/premium gates for software features only; never sell unauthorized media access.

### Phase 15 — Full functional + visual QA
Test phone, tablet, Android TV, Fire TV, web, fresh/upgrade install, auth, navigation, Live TV, guide, sports, failover, TMDB, addons, Firebase, audio, profiles, Universal Watch/Search, AI surfaces, PiP, handoff, remote, accessibility, and responsive design.

Exit gate: no critical crashes, dead navigation, fake operational claims, blank screens, broken focus, inconsistent layouts, or untested core flows.

### Phase 16 — Release
Production signing, release notes, GPL source/compliance package, APK/AAB, download page, privacy/terms, monitoring, and release visual review.

## Current implementation priorities

1. Finish green pinned Nuvio baseline.
2. Import/adapt Nuvio into AstraWave-controlled structure.
3. Build AstraWave design system and premium shell.
4. Wire TMDB default catalogs.
5. Preserve/expand Stremio catalogs/addons.
6. Port M3U/Xtream/XMLTV/source ranking.
7. Connect Free TV registry/daily checker.
8. Build combined guide/failover.
9. Port sports resolver and Game Day.
10. Add Music & Podcasts.
11. Port Firebase state/profiles.
12. Add personal/cloud/debrid connectors.
13. Add Universal Watch/Search and intelligence layer.
14. Add TV QR, phone remote, backup/restore, handoff.
15. Add entitlements.
16. Full functional and visual QA.
17. Production release.

## Definition of done

AstraWave is not complete until:
- every listed top-level module exists
- current AstraWave feature set is preserved
- Nuvio-derived flows are rebranded/integrated
- UI is beautiful, clean, consistent, responsive, and visually QA’d on every target form factor
- user M3U/Xtream works
- AstraWave Free TV is rights-gated and health-checked daily
- TMDB catalogs work by default
- compatible Stremio addons can add/manage catalogs
- sports guide resolves against both My IPTV and Free TV
- Music & Podcasts works with background playback
- Universal Watch and Universal Search work across eligible source types
- automatic failover and pre-play health checks work
- Firebase sync works
- profiles/parental/privacy controls work
- TV navigation is D-pad friendly with reliable focus
- phone remote/QR/handoff core flows work where enabled
- loading, empty, offline, and error states are intentional
- no blank pages, debug-looking UI, stretched art, or fake operational claims remain
- accessibility baseline is met
- Android build is green
- real-device QA is complete
- GPL obligations are satisfied
