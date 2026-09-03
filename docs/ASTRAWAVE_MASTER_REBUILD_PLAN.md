# AstraWave Massive Rebuild Plan

## Product vision

Rebuild the current AstraWave client around the Nuvio media core while preserving and improving every AstraWave capability already designed or implemented. The target is not another IPTV player or another Stremio fork. The target is a premium entertainment operating system that combines:

- Nuvio-derived media/discovery/player core
- Tuvora-style onboarding and simplicity
- TiviMate/Xfinity-quality Live TV and EPG
- AstraWave sports, Free TV, cloud sync, music/podcasts, profiles, source aggregation, AI discovery, multiview, diagnostics, remote control, and premium household features

The visual and interaction benchmark is higher than the products used as references: AstraWave must be more polished than stock Nuvio, cleaner and more modern than TiviMate, and faster to understand than either. Nuvio and TiviMate are baselines to surpass, not visual targets to copy.

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
- DVR for authorized sources
- alerts
- AI assistant
- phone remote
- backup/restore
- device handoff
- diagnostics/admin health console
- feature flags / remote config
- universal watchlist
- casting
- playback intelligence
- intro/credits skipping where supported
- release calendar
- parental controls
- privacy/local-only mode
- accessibility mode
- voice search

## Target navigation

### Mobile / tablet
Home | Movies | TV | Live TV | Guide | Sports | Music & Podcasts | Discover | Search | My AstraWave

### TV
Collapsed left rail that expands on focus:
Home | Movies | TV | Live TV | Guide | Sports | Music & Podcasts | Search | My AstraWave

## Core architecture

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
- Coming Soon
- Watch Tonight
- Hidden Gems
- Family Night
- Short Movies
- Mood-based collections

TMDB is metadata/catalog only; playback comes from authorized configured sources.

### 3. Stremio-compatible addon layer
Users can install compatible addons that may contribute:

- catalogs
- metadata
- search results
- subtitles
- authorized playback sources

Addon results are merged into AstraWave discovery while retaining provider attribution and source ranking.

Users must be able to:
- reorder addon catalogs
- hide catalogs
- pin catalogs to Home
- group addon rows
- enable/disable addons per profile
- filter addon stream results
- view provider attribution
- remove or repair broken addons

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
- catch-up/archive support when provider exposes it

#### Combined
Optional merged channel/guide experience combining:
- AstraWave Free TV
- My IPTV

Duplicate channels should normalize by tvg-id, channel name, region, broadcaster identity, and logo/EPG metadata.

## Guide / EPG

Build a guide that exceeds TiviMate/Xfinity in polish, readability, and speed while preserving the rapid D-pad interaction expert IPTV users expect.

Requirements:
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
- start-over where catch-up/timeshift is available
- instant replay buffer where supported
- channel surf mode
- last-channel shortcut
- preview pane
- fast channel switching
- premium channel logo treatment
- clear current-time line and time-axis hierarchy
- smooth horizontal/vertical navigation with remembered position
- subtle live progress indicators
- compact and expanded program details
- quick actions without opening cluttered menus

## Sports Guide and Game Day

### Premium sports schedule visual standard
The sports experience is a flagship AstraWave surface and must look substantially more premium than a conventional IPTV schedule or generic list.

The visual target is the polish of a top-tier sports network/streaming app, with AstraWave's own identity. Requirements:
- cinematic league/team branding where permitted
- large date/day selector with Today / Tomorrow / upcoming shortcuts
- league tabs and personalized favorite-team filters
- featured-game hero for the most relevant live/upcoming matchup
- matchup cards with team marks, records, rankings, start time, live state, network and source status
- LIVE badges, possession/game-state indicators and score hierarchy where data exists
- game progress/status without overwhelming the card
- countdowns for starting-soon events
- clear Watch, Add to Multiview, Remind Me and Game Details actions
- broadcaster/source badges with health/availability state
- premium empty/loading/offline states instead of generic cards
- compact schedule density option and cinematic card option
- horizontal TV layout optimized for 10-foot viewing
- phone layout optimized for one-handed scanning
- smooth D-pad transitions and strong focus treatment
- favorite teams visually prioritized without hiding the rest of the schedule
- league color accents used sparingly; AstraWave design language remains dominant
- optional scoreboard rail for live games
- Game Day detail view with matchup, standings/records, injuries/lineups/news where available, venue, weather where relevant, broadcaster/source, related radio/podcasts, alerts and multiview
- sports mosaic launcher showing up to four live games with one-click audio switching

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
8. If multiple candidates exist, offer Auto Best or source chooser.
9. If not playable, still show schedule, teams, time, venue, broadcaster, standings, and event info.

Users can follow:
- teams
- leagues
- players/competitors
- individual events

Home rows should include:
- Sports Starting Soon
- Today’s Biggest Games
- Favorite Teams
- Live Sports Now
- Game Day

Game Day mode should include:
- countdown
- matchup
- team records/standings
- broadcaster/source
- Watch button
- lineup/news where available
- related radio/podcasts
- score alerts
- pregame reminders

### Multiview and sports mosaic
- 2-up, 3-up, and 4-up layouts
- audio focus switching
- quick source switching
- sports mosaic preview grid
- favorite-team quick launch
- low-latency mode where supported

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
- OPML import/export
- podcast episode playback
- video podcast listen/watch toggle
- internet radio URLs
- background audio
- queue
- mini-player
- favorites
- recently played
- Continue Listening
- playback speed
- sleep timer
- cross-device progress
- Firebase sync for playback/progress/subscriptions
- personal music library connectors later
- casting where supported

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
21. Coming Soon
22. New Podcast Episodes
23. Recently Added From Addons
24. Mood Picks
25. Short Watch

Rows should be dynamic and hidden when empty.

Home should adapt by time of day:
- morning news/weather
- daytime live TV
- afternoon sports
- evening movies/series
- late-night recommendations
- weekend family/sports modes

## Universal Watch and source availability

Every movie, show, episode, sports event, live channel, podcast, or audio item should use one consistent resolver.

Universal Watch must:
- inspect all configured eligible sources
- show source/provider attribution
- pre-test candidate health
- rank by quality, latency, codec, compatibility, and user preference
- offer Auto Best
- optionally Ask Every Time
- fail over automatically for live streams
- show why no playable source is available instead of dead buttons

## Universal Watchlist

One watchlist across:
- TMDB titles
- Stremio catalogs
- IPTV VOD where available
- personal media
- AstraWave free/on-demand content
- unreleased titles

Features:
- Coming Soon watchlist
- availability notifications
- source availability view
- watched/unwatched state
- ratings/reactions
- share to household/friends optionally

## Unified Search

Search across:
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
- addons
- personal media

Results must identify category/source clearly.

Voice search examples:
- “Show Broncos games”
- “Play local news”
- “Find a comedy under 90 minutes”
- “Resume my podcast”
- “What can I watch tonight?”

## AI assistant and intelligent discovery

AstraWave AI should answer against content actually available to the user.

Examples:
- “Find a funny movie under two hours”
- “What NBA games can I watch tonight?”
- “Something the whole family will like”
- “Show me mind-bending thrillers I haven’t seen”
- “Find a podcast about AI under 45 minutes”

AI should understand:
- profile history
- watchlist
- source availability
- sports favorites
- runtime
- genres
- ratings
- mood
- device capability

AI must not imply unavailable content is playable.

## Recommendation engine

Signals:
- watch history
- completion percentage
- ratings
- like/dislike
- not interested
- favorites
- household profile combinations
- time of day
- sports interests
- addon/catalog interaction

Controls:
- More like this
- Less like this
- Not interested
- Hide genre
- Hide actor
- Watched before
- reset recommendation profile

Household recommendation mode should combine profiles for Family Night.

## Catalog Builder and discovery tools

Users can build custom catalogs such as:
- 90s Thrillers
- Oscar Winners
- Kids Saturday
- Mind-Bending Movies
- True Stories
- Under 90 Minutes
- Family Night
- My Team Games
- Favorite Directors

Filters:
- genre
- year/decade
- runtime
- rating
- language
- actor/director
- studio/network
- franchise
- mood
- availability
- quality

## Release calendar and alerts

One calendar for:
- movie releases
- TV episodes
- sports events
- podcast episodes
- recordings

Alerts:
- new episode available
- watchlist title becomes playable
- favorite team starting soon
- source restored
- playlist refresh failure
- new podcast episode
- new addon catalog item
- upcoming recording

Central notification center should manage all alerts.

## Profiles and household

Profile types:
- Adult
- Kids
- Guest
- optional Local Only profile

Per-profile separation:
- history
- watchlist
- Continue Watching
- favorites
- sports teams
- podcast subscriptions
- music/radio favorites
- recommendations
- source preferences
- subtitle/audio preferences

Kids features:
- PIN
- age/content restrictions
- restricted live groups
- blocked addons
- bedtime schedule
- safe search

Privacy mode:
- local-only profile
- cloud sync opt-out
- telemetry opt-out
- delete/reset history

Friends/household activity should be opt-in only.

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
- recommendation preferences
- notifications
- device pairing metadata

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
- picture-in-picture
- background audio
- casting
- playback speed
- sleep timer
- progressive seeking
- advanced player stats
- pre-play source testing
- instant replay/timeshift where supported
- Start Over where supported
- Next Episode
- configurable binge countdown
- stop-after-X-episodes

### Advanced player stats
Show:
- codec
- container
- bitrate
- resolution
- FPS
- HDR/Dolby Vision status
- audio codec
- channels
- buffer health
- source latency
- throughput
- dropped frames
- direct play/transcode status where applicable

### Subtitle intelligence
- preferred languages
- forced subtitle preference
- hearing-impaired preference
- size/style presets
- subtitle offset
- remembered profile defaults
- auto-sync roadmap

### Intro / recap / credits
Where metadata/content source supports it:
- Skip Intro
- Skip Recap
- Skip Credits
- Next Episode

## Playback intelligence

Detect device capability:
- HDR10
- Dolby Vision
- HEVC
- AV1
- H.264
- Atmos
- DTS
- max resolution
- refresh rate

Automatically avoid incompatible candidates.

Bandwidth profiles:
- Home Wi-Fi
- Mobile Data
- Slow Connection
- Unlimited

Network quality monitor should help distinguish:
- bad source
- bad home network
- device decode problem

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
9. device capability
10. bandwidth profile

Never publish or auto-select a source that is not eligible for playback.

## Automatic failover

For Live TV and other compatible live sources:
- detect playback failure
- retry briefly
- switch to next healthy candidate
- preserve channel/program context
- show subtle “source switched” message
- avoid kicking user back to guide where possible

## DVR, recording and catch-up

Only for sources the user is authorized to record.

Features:
- record current event
- scheduled recording
- Series Pass
- team recording rules
- sports recording rules
- new episodes only
- all episodes
- keep latest N recordings
- storage target selection
- conflict handling
- recording manager
- start-over/catch-up integration
- commercial detection/skip where legally appropriate for user recordings

## Personal media

Roadmap:
- Plex
- Jellyfin
- Emby
- NAS
- WebDAV
- local device storage

Personal media should participate in:
- Universal Search
- Universal Watch
- Continue Watching
- watchlist
- AI recommendations
- intro/credits detection where available

## Authorized cloud/debrid

Support user-connected services only for content the user is authorized to access.

Features:
- account connection
- explicit source resolution
- health/quality ranking
- no hardcoded discovery of unauthorized copyrighted content
- provider-specific capabilities abstracted behind the central resolver

## Casting and device handoff

Support platform-appropriate casting where possible.

Device handoff:
- Continue on TV
- Continue on Phone
- Continue on Web
- transfer playback position
- transfer selected title/channel

## Phone remote / companion mode

Phone can become:
- keyboard
- TV remote
- channel browser
- guide browser
- playback controller
- source picker
- queue manager
- search interface

## QR TV onboarding

Use QR for:
- sign-in
- profile pairing
- IPTV source setup
- addon installation
- backup restore
- device pairing

Avoid entering long credentials with TV remotes.

## Backup, restore and migration

Backup:
- profiles
- source configuration
- addons
- watchlist
- settings
- channel mappings
- favorites
- podcast subscriptions
- themes/layouts

Features:
- automatic backup before updates
- cloud backup for eligible users
- manual encrypted export
- restore to new device
- import M3U/XMLTV
- import Stremio addon lists where compatible
- import Trakt lists/history where supported
- import OPML podcasts

## Diagnostics and operator health console

### User diagnostics
- playlist status
- source latency
- failing channels
- EPG coverage
- addon status
- last refresh
- Firebase sync state
- network quality
- player error reason

### AstraWave operator console
- Free TV rights status
- dead feeds
- rights-review queue
- sports-provider errors
- TMDB status
- Firebase health
- addon failures
- app version adoption
- crash summaries
- playback startup/failure metrics

## Feature flags and Remote Config

Use remote configuration to:
- enable/disable experimental modules
- stage rollouts
- test layouts
- gate beta features
- disable broken integrations without emergency APK release

## Crash reporting and telemetry

Privacy-respecting technical telemetry only:
- crash type
- app version
- playback failure reason
- startup time
- source startup latency
- guide load time

Requirements:
- user privacy controls
- local-only option

## Watch Party

For personal media or other sources that permit synchronized playback:
- room creation
- invite link/code
- synced play/pause/seek
- participant list
- optional chat/reactions later

## Ratings and reactions

Users can:
- like/dislike
- 1–5 star rate
- mark Not Interested
- mark Watched Before
- favorite
- recommend to household profile

These signals feed personalization.

## Mood and runtime discovery

Quick discovery filters:
- Funny
- Dark
- Mind-Bending
- Family
- Relaxing
- Background TV
- High Rated
- Under 90 Minutes
- Under 2 Hours
- One Episode Before Bed

## Stream filters

Allow user rules such as:
- prefer/exclude 4K
- prefer/exclude HDR
- prefer/exclude DV
- prefer/exclude HEVC
- language inclusion/exclusion
- quality floor/ceiling
- keyword filters
- bitrate limits

## Accessibility

Must support:
- large text
- high contrast
- screen-reader labels
- reduced motion
- strong focus indicators
- captions/subtitle controls
- voice search
- remote-friendly navigation

## Developer / Advanced Mode

Optional hidden advanced section:
- addon endpoints
- resolver output
- source candidates
- playlist diagnostics
- EPG mapping
- logs
- network stats

Normal users should never need to see this.

# Premium UI / UX design system

## Visual goal
AstraWave must be beautiful, clean, premium, and immediately understandable.

AstraWave is required to exceed the reference apps rather than imitate them:
- better visual hierarchy and content presentation than Nuvio
- faster, cleaner and more premium Live TV/Guide interaction than TiviMate
- simpler first-use experience than either
- richer but less cluttered sports presentation than conventional IPTV apps
- consistent design language across phone, tablet, Android TV, Fire TV and web

Reference qualities:
- Tuvora simplicity
- Nuvio content organization
- TiviMate Live TV interaction
- premium streaming-service polish
- premium sports-network schedule/game presentation
- unmistakable AstraWave identity

## Design system requirements

Define shared tokens for:
- spacing
- typography
- radii
- elevation
- opacity
- motion duration
- focus scale
- card sizes
- grid gutters
- artwork aspect ratios

Every screen must reuse components instead of inventing one-off styles.

## Visual hierarchy

Screens must have:
- obvious page title/primary action
- restrained secondary text
- strong artwork
- clean grouping
- sufficient whitespace
- limited competing accent elements
- consistent controls

Avoid:
- clutter
- tiny text
- excessive borders
- too many pills
- stretched artwork
- inconsistent spacing
- generic debug-like cards
- blank dead areas

## Artwork treatment

- consistent poster ratios
- consistent backdrop ratios
- graceful fallback artwork
- subtle gradients over heroes
- readable text over imagery
- cached/responsive image loading
- no low-resolution stretching

## Motion

Use subtle motion for:
- focus changes
- screen transitions
- row loading
- player overlays
- source switching
- notifications

Motion should feel premium, never distracting.

Reduced-motion accessibility must be supported.

## TV focus system

TV/Fire TV focus must be unmistakable:
- scale/elevation change
- clean outline/glow treatment
- no clipped focus rings
- predictable D-pad movement
- no focus traps
- remember focus on return

## Responsive layouts

Dedicated layouts for:
- phone portrait
- phone landscape
- tablet
- Android TV
- Fire TV
- 1080p TV
- 4K TV
- web desktop
- web tablet/mobile

Do not simply stretch phone layouts onto television screens.

## State design

Every module needs designed states for:
- loading
- empty
- error
- offline
- partial data
- unauthenticated
- no source available
- stale playlist
- EPG unavailable

No blank pages.

## Home design

Home should feel cinematic but fast:
- hero with restrained copy
- clean horizontal rows
- prominent Continue Watching
- live/sports cards visually distinct from VOD
- personalized sections
- no excessive row count on first viewport

## Title detail design

Title page should include:
- backdrop
- poster
- title/year/runtime/rating
- description
- Watch / Resume
- Universal Watch source availability
- trailer
- cast/crew
- seasons/episodes
- related titles
- watchlist
- rating/reaction

## Sports design

Sports pages should have:
- flagship-level sports schedule presentation, not a generic list
- league/team branding where allowed
- featured matchup hierarchy
- premium matchup cards
- clear live/upcoming/completed visual states
- score and game-state hierarchy
- broadcaster/source status
- Watch CTA
- Add to Multiview CTA
- reminders/follow controls
- date/league/favorite-team filtering
- scoreboard rail where appropriate
- cinematic Game Day detail pages
- responsive TV/phone layouts designed independently

## Music/podcast design

Audio UI should not feel like a movie page reused badly.

Include:
- artwork-led now playing
- queue
- playback speed
- sleep timer
- episode metadata
- compact mini-player

## Settings design

Settings should be organized into understandable groups:
- Account & Profiles
- Playback
- Live TV & Guide
- Sources
- Addons
- Music & Podcasts
- Sports
- Notifications
- Appearance
- Privacy
- Advanced

Use progressive disclosure rather than giant forms.

## Visual QA gate

Before release, visually inspect:
- common phone sizes
- tablets
- Android TV
- Fire TV
- 1080p TVs
- 4K TVs
- desktop web

Reject release for:
- clipped text
- broken focus
- stretched images
- inconsistent typography
- broken grids
- inaccessible contrast
- accidental debug data
- dead buttons
- ugly empty states
- sports schedule that looks like a generic data table or IPTV utility
- Guide/Live TV UX that is materially less polished or less usable than TiviMate
- discovery/home presentation that is materially less polished than Nuvio

# AstraWave+ product layer

## Free
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
- basic radio/podcasts
- Universal Search

## AstraWave+
Candidate premium software features:
- unlimited playlists
- merged guide
- advanced channel cleanup
- advanced failover
- cloud sync across devices
- multiple profiles
- household recommendations
- sports alerts
- multiview
- authorized DVR
- recording rules
- web remote
- advanced addons/extensions
- backups
- AI assistant
- themes/layout customization
- advanced diagnostics
- device handoff
- premium subtitle/playback controls
- watch party

Premium gates software/features only, not unauthorized copyrighted content.

# Build phases

## Phase 0 — Protect current beta
- Keep existing Firebase-ready beta branch unchanged.
- Build new work only on `feature/nuvio-core-rebuild`.
- Maintain downloadable fallback APK until new branch reaches feature parity.

Exit gate: old beta remains installable and green.

## Phase 1 — Establish Nuvio baseline
- Pin upstream Nuvio commit.
- Confirm GPL notices/attribution.
- Build upstream Android APK in CI.
- Document upstream structure.
- Identify extension points for Home, resolver, player, navigation, addons, and data stores.

Exit gate: clean Nuvio baseline APK builds reproducibly.

## Phase 2 — AstraWave design system + branding + navigation
- implement design tokens
- shared cards/buttons/rows/dialogs
- artwork system
- phone/tablet/TV responsive layouts
- AstraWave branding
- top-level navigation
- loading/error/empty-state components
- D-pad focus system
- benchmark and surpass Nuvio visual polish
- benchmark and surpass TiviMate Live TV/Guide usability and presentation
- establish premium sports-card/schedule components shared across Sports, Home and Game Day

Exit gate: app looks polished before feature modules are ported and passes the AstraWave-vs-Nuvio/TiviMate visual benchmark.

## Phase 3 — TMDB + native catalogs
- Wire TMDB configuration.
- Build default movie/TV catalogs.
- title pages
- cast/crew
- trailers/extras metadata where available
- release calendar foundation
- Coming Soon

Exit gate: useful premium-looking Movies/TV experience with no user source configured.

## Phase 4 — Stremio addon integration
- preserve Nuvio addon concepts
- addon management UI
- catalog row management
- metadata/search/subtitles
- eligible source candidates
- provider attribution
- stream filtering

Exit gate: compatible addons extend AstraWave safely.

## Phase 5 — Universal Search / Watchlist / Resolver
- unified search
- Universal Watch
- Universal Watchlist
- source availability view
- pre-play health tests
- Auto Best
- source chooser

Exit gate: one consistent source experience across the app.

## Phase 6 — My IPTV
- M3U
- Xtream APIs
- multiple sources
- normalization
- favorites/recent
- XMLTV
- refresh
- catch-up metadata support

Exit gate: user IPTV plays reliably and survives app restart.

## Phase 7 — AstraWave Free TV
- rights registry
- authorized/public feeds
- daily health workflow
- rights + health gating
- backups
- EPG mappings
- categories

Exit gate: only healthy rights-approved channels appear.

## Phase 8 — Combined Live TV + Guide
- merge user + Free TV
- deduplicate
- merged EPG
- grid guide
- mini-guide
- channel surf
- fast switching
- failover
- start-over/timeshift where supported
- premium visual hierarchy and TV focus behavior that exceeds TiviMate benchmark

Exit gate: premium TV experience on phone and TV that meets or exceeds the AstraWave-vs-TiviMate benchmark.

## Phase 9 — Sports Command Center
- schedule ingestion
- broadcaster normalization
- My IPTV + Free TV matching
- favorite teams
- Game Day
- score alerts
- sports home rows
- event pages
- premium date/league filtering
- featured matchup hero
- premium matchup cards with live/upcoming/completed states
- scoreboard rail where data allows
- Watch / Multiview / Reminder actions
- dedicated phone and 10-foot TV sports layouts

Exit gate: event-to-channel resolution works where authorized sources exist and the sports schedule passes the premium flagship visual QA gate.

## Phase 10 — Multiview
- 2/3/4 panes
- sports mosaic
- audio focus
- performance safeguards
- TV remote controls

Exit gate: stable multiview on supported devices.

## Phase 11 — Music & Podcasts
- RSS
- OPML
- video podcasts
- radio
- background playback
- queue
- mini-player
- speed
- sleep timer
- Continue Listening

Exit gate: full audio experience works independently of video playback.

## Phase 12 — Firebase profiles/cloud sync
- auth persistence
- profiles
- watchlist
- progress
- favorites
- settings
- podcast/music state
- notifications
- entitlements

Exit gate: synchronized state across devices.

## Phase 13 — Recommendations + AI
- ratings/reactions
- household recommendations
- mood discovery
- runtime discovery
- AI assistant grounded in available sources

Exit gate: recommendations are personalized and availability-aware.

## Phase 14 — Personal media + authorized cloud/debrid
- Plex
- Jellyfin
- Emby
- NAS/WebDAV
- central resolver integration
- authorized cloud/debrid connectors

Exit gate: personal/authorized sources behave like first-class AstraWave sources.

## Phase 15 — Casting / Remote / Handoff / QR
- casting
- phone remote
- QR login/setup
- device pairing
- Continue on TV/Phone/Web

Exit gate: cross-device workflows are smooth.

## Phase 16 — DVR / Recording / Catch-up
- recording engine for eligible sources
- Series Pass
- team rules
- recording manager
- storage targets
- catch-up/start-over
- commercial detection where appropriate

Exit gate: recording is safe, reliable, and rights-respecting.

## Phase 17 — Backup / Restore / Imports
- encrypted export
- cloud backup
- automatic pre-update backup
- restore
- import M3U/XMLTV
- OPML
- Trakt where supported
- addon-list import where compatible

Exit gate: user configuration is portable.

## Phase 18 — Admin / Diagnostics / Feature Flags
- user diagnostics
- operator health console
- crash reporting
- playback telemetry
- Remote Config
- feature flags
- rollout controls

Exit gate: production issues can be identified and mitigated quickly.

## Phase 19 — Premium onboarding / Tuvora-style setup
- first-run flow
- profile picker
- optional IPTV setup
- QR pairing
- source management hub
- nontechnical-friendly settings

Exit gate: a new user can reach useful content without documentation.

## Phase 20 — AstraWave+ entitlements
- Free vs Plus gates
- entitlement sync
- upgrade surfaces
- premium feature checks

Exit gate: software subscription logic works correctly.

## Phase 21 — Accessibility / Privacy / Parental controls
- accessibility QA
- privacy mode
- local-only profile
- Kids PIN
- content restrictions
- bedtime controls
- telemetry controls

Exit gate: family/privacy/accessibility requirements are complete.

## Phase 22 — Full QA
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
- Live TV
- guide
- sports
- multiview
- failover
- TMDB
- addon catalogs
- Universal Search
- Universal Watch
- Firebase sync
- podcasts/radio
- casting
- backup/restore
- profiles
- accessibility
- visual quality
- Nuvio comparison pass for Home/Movies/TV/Discover
- TiviMate comparison pass for Live TV/Guide/D-pad speed
- premium sports-schedule comparison pass on phone and TV

Exit gate: no known critical crashes, dead navigation, fake operational claims, broken focus, ugly screens, untested core flows, or benchmark regressions versus Nuvio/TiviMate reference quality.

## Phase 23 — Release
- production signing
- release notes
- GPL compliance/source package
- APK/AAB
- public download page
- privacy/terms
- monitoring
- rollback plan

# Priority tiers

## Core launch
- Nuvio-derived client
- premium UI system
- TMDB
- Stremio catalogs/addons
- My IPTV
- AstraWave Free TV
- Combined Guide
- Sports Guide
- Music & Podcasts
- Universal Search
- Universal Watch
- Universal Watchlist
- Firebase profiles/sync
- automatic failover
- QR onboarding
- strong TV UX

## AstraWave+ differentiators
- multiview
- AI assistant
- household recommendations
- unlimited playlists
- advanced guide cleanup
- advanced failover
- DVR/recording
- sports alerts
- phone remote
- backup/restore
- device handoff
- themes/layouts
- advanced diagnostics

## Post-launch power features
- watch party
- commercial detection
- advanced personal-media analysis
- intro/credits detection expansion
- smart recording automation
- advanced subtitle synchronization
- social/friend recommendations
- developer mode expansion

# Current implementation priorities

1. Keep current Firebase beta green.
2. Finish pinned Nuvio baseline build.
3. Bring Nuvio source under AstraWave-controlled rebuild structure.
4. Build AstraWave premium design system that surpasses Nuvio/TiviMate benchmarks.
5. Rebrand shell/navigation.
6. Wire TMDB native catalogs.
7. Preserve/expand Stremio catalogs/addons.
8. Build Universal Search/Watch/Watchlist.
9. Port existing M3U/Xtream/XMLTV/source-ranking logic.
10. Connect AstraWave Free TV registry and daily checker.
11. Build Combined Guide and automatic failover with premium TiviMate-beating TV UX.
12. Build Sports Command Center and flagship premium Game Day/schedule UI.
13. Add Multiview.
14. Add Music & Podcasts.
15. Port Firebase cloud state.
16. Add recommendations/AI.
17. Add personal/cloud/debrid connectors.
18. Add casting/remote/handoff/QR.
19. Add DVR/catch-up for eligible sources.
20. Add backup/imports.
21. Add diagnostics/admin console/feature flags.
22. Add AstraWave+ entitlements.
23. Finish accessibility/privacy/parental controls.
24. Full device and visual QA including Nuvio/TiviMate/sports benchmark comparisons.
25. Production release.

# Definition of done

AstraWave is not complete until:

- every listed top-level module exists
- current AstraWave feature set is preserved
- Nuvio-derived flows are fully rebranded/integrated
- UI is beautiful, clean, consistent, and responsive
- UI/UX is demonstrably more polished than stock Nuvio
- Live TV/Guide is at least as fast and materially more premium than TiviMate
- Sports schedule/Game Day looks and behaves like a flagship premium sports streaming product
- user M3U/Xtream works
- AstraWave Free TV is rights-gated and health-checked daily
- TMDB catalogs work by default
- compatible Stremio addons can add catalogs
- Universal Search works across all major content types
- Universal Watch resolves eligible sources correctly
- Universal Watchlist works across catalogs
- sports guide resolves against user IPTV and Free TV
- Game Day works
- multiview works on supported devices
- Music & Podcasts works with background playback
- Firebase sync works
- automatic failover works
- QR onboarding works
- TV navigation is D-pad friendly
- backup/restore works
- diagnostics exist
- privacy/parental/accessibility requirements are satisfied
- Android build is green
- real-device QA is complete
- visual QA is complete
- GPL obligations are satisfied
