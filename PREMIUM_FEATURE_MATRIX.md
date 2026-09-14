# AstraWave Premium Feature Matrix

This file is the execution checklist for the premium roadmap requested on September 13, 2026. A feature is only marked COMPLETE when the user-facing behavior is wired and the relevant Android/Web build is verified. PARTIAL means real supporting code exists but the full cross-platform experience is not yet complete.

## Playback and source intelligence

| Feature | Status | Current implementation / next gap |
|---|---|---|
| Universal Play Best | PARTIAL | VOD/live/sports source aggregation, ranking and failover exist; continue expanding provider parity and diagnostics. |
| Source presets | IMPLEMENTED | AUTO, Best Quality, Fastest Start, Data Saver, HDR Preferred, Surround Preferred, Debrid Only, Direct Only are stored per profile and affect source ranking. |
| Source health memory | IMPLEMENTED | SourceFusionRepository + predictive ranking remembers success/failure/latency and demotes degraded sources. |
| Source quality score | PARTIAL | Health/predictive score exists; enrich VOD labels with codec/HDR/audio/size consistently across all connectors. |
| Provider badges | PARTIAL | Quality/provider labels exist in several resolver paths; standard badge UI still needs full cross-platform rollout. |
| AutoNext / pre-resolve | PARTIAL | Preference and series continuation foundations exist; player-side pre-resolution and seamless transition still need completion. |
| Intro / recap / credits | PARTIAL | Per-profile policy exists; timing detection/metadata execution still needs completion. |
| Subtitle system | PARTIAL | Subtitle/language preference model exists; provider retrieval, forced/HI ranking and styling need full player integration. |
| Audio intelligence | PARTIAL | Language/surround preferences exist; track-selection integration and badges need completion. |
| Offline authorized downloads | PARTIAL | DownloadTravelStore and UI exist; provider-specific eligible download transports remain capability-gated. |

## Profiles, sync and personalization

| Feature | Status | Current implementation / next gap |
|---|---|---|
| Per-profile playback policy | IMPLEMENTED | PlaybackPreferenceStore is profile-scoped. |
| Kids profiles / PIN / maturity | PARTIAL | Kids mode/profile routing exists; deeper maturity/PIN policy still needs full enforcement pass. |
| Unified Continue Watching | PARTIAL | Local/cloud progress and home rails exist; merge live catch-up/sports replay/personal media into one normalized rail. |
| Personalized Home | PARTIAL | Continue, watchlist, intelligence rows and sports exist; improve ranking diversity and addon-aware personalization. |
| Cloud backup | PARTIAL | Library/config restore exists; extend safe non-secret preference sync and conflict handling. |
| Trakt sync | NOT STARTED | Add authorized OAuth, import/writeback and conflict rules. |
| Simkl sync | NOT STARTED | Add authorized OAuth, import/writeback and conflict rules. |
| Device handoff / household | PARTIAL | Pairing/session/handoff models exist; production network handoff transport remains capability-gated. |
| Watch party | NOT STARTED | Requires synchronized playback/session transport. |

## Live TV and Guide

| Feature | Status | Current implementation / next gap |
|---|---|---|
| Real XMLTV EPG | IMPLEMENTED | M3U XMLTV, Xtream XMLTV, public fallback, provider precedence and conservative channel matching exist. |
| 3-hour timeline guide | IMPLEMENTED | Android TV has proportional multi-hour timeline with synchronized horizontal scrolling and coverage counts. |
| 24h / 7-day guide navigation | PARTIAL | Full schedules are retained; UI horizon/day controls still need rollout. |
| Current-time line | PARTIAL | Current programme highlighting exists; true vertical NOW indicator/gap geometry needs final polish. |
| EPG repair center | PARTIAL | Manual overrides exist; dedicated unmatched-channel repair UI still needed. |
| Favorites / recent / custom channel groups | PARTIAL | Favorites/recent/group filters exist in web; native parity and reorder/hide tools need completion. |
| Catch-up TV | PARTIAL | Data models/catch-up hooks exist in guide architecture; provider capability detection and restart UI need completion. |
| DVR / series recording | PARTIAL | DVR models, local gateway and recordings UI exist; full provider/storage transport remains capability-gated. |
| Preview player | PARTIAL | Live TV selection workspace exists; muted focus-preview playback still needs implementation. |
| TV quick panel | NOT STARTED | Long-press/menu overlay for sources/audio/subtitles/favorite/record/multiview. |
| Channel-number remote entry | NOT STARTED | Add number-buffer routing on TV. |

## Sports

| Feature | Status | Current implementation / next gap |
|---|---|---|
| Game Day dashboard | IMPLEMENTED | Sports ranking, broadcaster matching and watch-ready state exist. |
| Sports source fallback | IMPLEMENTED | Cloud events fall back to authorized local Live TV channel matching. |
| Sports multiview | PARTIAL | 2/3/4/6-pane multiview exists; sports-specific launch flow and polish continue. |
| Favorite teams | IMPLEMENTED | SportsPreferenceStore exists and influences Home ranking/reminders. |
| Sports reminders | IMPLEMENTED | On-device reminder flow exists where notification permission is available. |
| Replay / condensed / highlights layer | PARTIAL | Provider-handoff architecture exists; event-page replay hierarchy needs completion. |
| Standings / deeper sports hub | NOT STARTED | Add league-specific standings/stat modules. |

## Mobile UI

| Feature | Status | Current implementation / next gap |
|---|---|---|
| Touch-first bottom navigation | IMPLEMENTED | Home / Search / Live / VOD / My. |
| Device-specific density | IMPLEMENTED | Phone/tablet/TV typography, sizing, spacing, focus and shapes diverge intentionally. |
| Sources / episodes bottom sheets | PARTIAL | Web player is sheet-like; native title detail still needs modal/bottom-sheet conversion for touch. |
| Mobile player gestures | NOT STARTED | Brightness/volume swipes, double-tap seek, aspect gesture and lock controls. |
| Mobile source picker | PARTIAL | Source selection exists; compact bottom-sheet UX still needs native pass. |

## Android TV UI

| Feature | Status | Current implementation / next gap |
|---|---|---|
| Cinematic Home | IMPLEMENTED | Hero-first, poster rails and reduced repetitive rows. |
| TiviMate-style guide density | PARTIAL | Real timeline exists; preview pane, NOW line and longer horizon remain. |
| Viewella-style D-pad focus | PARTIAL | Shared focus system and tighter focus scaling exist; full physical-remote QA still needed. |
| Cinematic VOD detail | IMPLEMENTED | Native themed backdrop hero with dominant Play Best/Continue. |
| Live TV workspace | IMPLEMENTED | Group rail + channel list + large selected-channel pane. |
| Muted focus preview | NOT STARTED | Add delayed preview after channel focus. |

## Web/Desktop

| Feature | Status | Current implementation / next gap |
|---|---|---|
| Responsive v5 layout | IMPLEMENTED | Desktop and phone intentionally diverge. |
| Mobile bottom navigation | IMPLEMENTED | Matches native phone hierarchy. |
| Sheet-like mobile player | IMPLEMENTED | Sticky actions, drag affordance, full-width source choices and safe-area handling. |
| Desktop keyboard shortcuts | NOT STARTED | Add /, G, L, S, Space and arrow navigation. |
| Command palette | NOT STARTED | Universal navigation/search/control palette. |
| Desktop mini-player | PARTIAL | Floating player exists; make it movable/resizable and retain playback across navigation. |

## Catalogs, discovery and search

| Feature | Status | Current implementation / next gap |
|---|---|---|
| Stremio addon catalogs | PARTIAL | Aggregator/rows exist; expand native Home/Movies/TV placement and ordering controls. |
| Catalog enable/disable/reorder | PARTIAL | Addon enablement exists; per-surface catalog ordering still needed. |
| Catalog deduplication | PARTIAL | Multiple dedupe paths exist; unify cross-addon canonical media identity. |
| Global search | PARTIAL | Cross-domain search exists; improve grouped result presentation for episodes/people/addon catalogs/personal media. |
| Smart artwork | PARTIAL | Device-specific artwork component/registry exists; improve logo/backdrop/poster selection and low-quality rejection. |
| Trailer-in-title behavior | IMPLEMENTED | Trailer actions live in title context rather than generic rails. |
| Premium title context | IMPLEMENTED | Lists, collections, universes, networks, cast/related content and source detail layers exist. |
| Voice search | NOT STARTED | Add Android TV voice intent / microphone path. |
| Astra Assistant | NOT STARTED | Natural-language discovery/control layer requires a dedicated intent execution surface. |

## Accessibility and performance

| Feature | Status | Current implementation / next gap |
|---|---|---|
| Reduced motion | IMPLEMENTED | Theme supports reduced-motion timing. |
| UI scale | IMPLEMENTED | Per-profile scale preference added; full consumption across all layouts still needs final pass. |
| High-contrast focus | IMPLEMENTED | TV focus ring and accessible contrast foundation exist. |
| Artwork/list prefetch | PARTIAL | Caching/lazy loading exists; expand prewarming for next rails/episodes/sources. |
| Focus/scroll restoration | PARTIAL | Several surfaces retain local state; central route restoration still needs completion. |
| Consumer diagnostics | PARTIAL | Source/EPG/health diagnostics exist; consolidate into simple status cards and repair actions. |
| Crash/failover UX | PARTIAL | Source failover exists; make all terminal failures actionable and consistent. |

## Execution rule

Do not mark a row COMPLETE until the feature is user-facing, wired to real data/behavior, and the applicable CI build passes. Keep legal boundaries: AstraWave may aggregate reviewed/free sources and user-authorized providers/addons/debrid, but it must not ship a curated piracy directory or unauthorized scraping path.
