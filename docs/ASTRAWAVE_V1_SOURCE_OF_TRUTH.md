# AstraWave v1.0 — Source of Truth

This document is the release contract for `feature/nuvio-core-rebuild`. It supplements `ASTRAWAVE_MASTER_REBUILD_PLAN.md`; where implementation is incomplete, this file defines the target behavior and release gates. Do not promote AstraWave to 1.0.0 merely because it compiles.

## 1. Product definition
AstraWave combines Nuvio-style Movies/TV discovery, TiviMate-quality Live TV/EPG, a premium Sports hub, Stremio-compatible source support, AstraWave Free TV, and later a responsive web companion. Android package: `com.astrawave.app`. Target Android phone, tablet, Android TV, and Fire TV first.

## 2. Visual and navigation contract
Use a near-black cinematic UI, restrained purple accent, large artwork, minimal borders, strong hierarchy, premium typography, smooth transitions, clean Home, dense readable Guide, and excellent TV D-pad focus. TV focus must be thin/bright, subtly elevated/scaled, deterministic, restorable after playback, and free of duplicate focus nodes. Phone primary navigation: Home | Movies | TV | Live | More. More: Guide, Sports, Search, Music & Podcasts, My AstraWave, Settings. TV uses a persistent rail for Home, Movies, TV Shows, Live TV, Guide, Sports, Music & Podcasts, Search, My AstraWave.

## 3. Home
Required shelves include Featured, Continue Watching, Trending Now, Popular Movies, Popular TV, New Movies, TV On Now, Upcoming Movies, Top Rated, Because You Watched, Recently Added, AstraWave Free TV highlights, Live Now, Sports Today, Sports Starting Soon, Favorite Teams, and addon catalog recommendations. No fake production catalogs.

## 4. Movies
Support trending, now playing, popular, upcoming, top rated, genres, decades, year, language/country, provider/network, and curated lists. Details: artwork, title/year/runtime/rating/certification/genres/overview/cast/director/trailers/recommendations/similar/watchlist/play/sources/subtitles/provider links.

## 5. TV
Support trending, on-air, popular, top rated, new episodes, genres, networks, and provider catalogs. Navigation is Show → Season → Episode. Episodes need artwork, description, air date, runtime, watched state, progress, source resolver, and subtitles.

## 6. Zero-configuration metadata
Users must never need to supply a TMDB key. Use AstraWave backend/TMDB proxy with Cinemeta fallback. If TMDB fails, real cached/fallback catalogs and search should degrade gracefully. No synthetic release catalogs.

## 7. Stremio-compatible addon system
Support manifest, catalogs, metadata, streams, subtitles, diagnostics, online/offline state, latency, malformed response, timeout, last success, capabilities, and catalog health. Built-in safe essentials may include Cinemeta, YouTube, WatchHub, OpenSubtitles, and Public Domain Movies. User-added compatible manifests are supported. A failing addon must not freeze the app.

## 8. VOD playback
Use Media3/ExoPlayer for authorized playable sources. Support play/pause, seek, subtitles, audio tracks, speed, resume, next episode, autoplay, progress, and history. If nothing playable is available, show `No playable source available`; never fake success.

## 9. AstraWave Free TV
New installs should have verified legitimate free/public TV where available. Candidate feeds require HTTPS, public/free evidence, authorization eligibility, health test, geo/status check, normalization, and approval before publish. Candidate examples include official/free broadcaster and FAST feeds such as ABC News Live, Al Jazeera, DW, France 24, NHK World, CNA, FOX Weather, AccuWeather NOW, PBS Kids, World Channel, Create, Red Bull TV, FIFA+, Motorsport.tv, SportsGrid, FUEL TV, EDGESport, and other reviewed public channels.

## 10. FAST ecosystem
Integrate discovery/handoff for legitimate services such as Pluto TV, Sling Freestream, Plex, Roku Channel, Xumo Play, Samsung TV Plus, LG Channels, and Tubi. Use direct third-party playback only when legitimately supported; otherwise hand off to the official provider. Never bypass DRM/provider controls.

## 11. Free IPTV discovery registry
Public playlist projects may be discovery inputs, including iptv-org, Free-TV/IPTV, reviewed FreeCastHub lists, and similar public registries. Never blindly ship all entries. Pipeline: Discover → parse → classify → authorization/public evidence → HTTPS check → health → geo → dedupe → metadata → EPG mapping → approval.

## 12. My IPTV
Support multiple M3U/M3U8 playlists, logos/groups/tvg-id/catch-up metadata, Xtream server/username/password/categories/live/VOD/series where legitimately provided, and one or more XMLTV EPG sources with automatic mapping. Credentials must use Android Keystore-backed protection, be excluded from backup, never logged, and never included in diagnostics.

## 13. Unified channel universe
Merge AstraWave Free TV, My IPTV, and authorized provider channels into one normalized universe. Deduplicate primarily by EPG/channel ID, canonical name, and provider metadata. Alternate streams become alternate sources, not duplicate Guide rows.

## 14. Source health and failover
Health states: Online, Degraded, Geo restricted, Unavailable, Unknown. Track HTTP status, latency, last check/success, provider, and cache state. Playback should try best source then healthy alternates with bounded requests, a 20-second initial-buffer watchdog, retry-all, and manual source selection.

## 15–16. Guide / EPG
Guide requires fixed channel rail, logos/numbers, Now/Next, horizontal timeline/current-time marker, variable-width programs, selected-program detail, favorites/groups/source filters, and filters for All/Favorites/Free TV/My IPTV/Sports/Movies/News/Kids/Local/provider groups. XMLTV matching must handle tvg-id, channel IDs, display-name aliases, normalized names, HD/FHD/4K suffixes, timezone offsets, source priority, multiple files, coverage metrics, configurable refresh (~15–120 minutes), manual refresh, timestamps, memory cache, disk cache, and stale-if-error.

## 17. Live TV UX
Support groups, search, favorites, last watched, Resume Live, logo, Now/Next, source health, immediate tune, and persistence of favorite/last channel/Guide position/source/focus.

## 18–20. Sports Command Center and Sports Guide
Sports must be visual, not spreadsheet-like. Include Today, Live Now, Starting Soon, Favorite Teams, leagues/sports/reminders/featured matchup/scores where available and broadcaster/channel matches. Cover NFL, NBA, MLB, NHL, soccer, tennis, golf, motorsport, and others where schedule data exists. Match events to channels using broadcaster, team names, league, sport, groups, aliases; rank multiple candidates rather than guessing. Sports Guide includes live/upcoming, team/league filters, favorite highlighting, channel match, Watch, and reminder.

## 21. Multiview
Support up to four real Media3 players with 2-up/3-up/4-up/maximize. Only selected pane has audio. Respect decoder/memory limits, fall back to fewer panes, and always release players.

## 22. Music, podcasts, radio
Support podcast RSS, OPML import, library, episodes, queues, artwork, resume, background playback, and authorized radio from Free TV/audio sources, My IPTV radio, and internet radio.

## 23. Universal Search
Search across Movies, TV, episodes, Stremio catalogs, Free TV, My IPTV, sports, podcasts, and personal media, grouped clearly.

## 24–26. Library, profiles, kids
Persist movies/TV, episode progress, favorite channels/teams, podcast progress, watchlists, and Continue Watching; sync across devices when cloud is available. Profiles support household avatars, individual history/recommendations/watchlists/favorites/teams and kids profiles. Kids mode supports certification filtering, strict unrated blocking, parental PIN, and restricted addon/source access.

## 27–28. Firebase and authentication
Firebase project: AstraWave. Package: `com.astrawave.app`. Use Firebase Auth + Firestore for profiles, favorites, watchlist, progress, settings, source metadata, favorite teams, entitlements, device handoff, and feature flags while staying on Spark where practical. Support guest/anonymous, email/password, account creation, password reset, delete account, and persistent login.

## 29–31. Personal media, cast/remote, DVR
Authorized user-connected media only. Adapters/roadmap: Plex, Jellyfin, Emby, local network media, cloud files. Integrate into search/details/Continue Watching/playback. Support Google Cast, phone↔TV handoff, remote commands, stable device IDs, pairing code and QR/deep links. DVR/catch-up/timeshift only where a provider explicitly supports it; never guess undocumented APIs.

## 32. Backup/restore
Export profiles, preferences, addon config, source metadata, favorites, and watchlist. Exclude IPTV passwords, tokens, and credentials. Include schema/version migrations.

## 33. Diagnostics
Show app version, build commit/type, signing state, Free TV integrity, source health, provider refresh history, IPTV channel count, EPG count/coverage, addon health, memory/disk cache, previous startup crash, and device class. Actions: recheck sources, Free TV sweep, clear cache, privacy-safe report. Never expose passwords/tokens/stream URLs.

## 34. Free TV registry integrity
Automated release validation must enforce unique channel IDs, unique stream URLs where appropriate, HTTPS-only default direct feeds, source attribution, public/authorized flags, and no malformed entries. Integrity failure blocks release.

## 35–36. Network safety and caching
Every remote subsystem uses HTTPS, connection/read/total timeout, response-size guard, controlled retry, and stale-if-error where appropriate. Applies to TMDB, Cinemeta, Stremio, IPTV, XMLTV, sports, and Free TV health. Persist safe XMLTV/public metadata/catalog cache only; do not persist plaintext credential-bearing IPTV URLs.

## 37–39. Accessibility, performance, crash safety
Require TalkBack labels, captions, scalable fonts, contrast, reduced motion, visible TV focus, and large targets. Target smooth navigation, virtualized Guide, lazy catalogs, artwork cache, bounded background work, no main-thread networking, and quick startup. Test 1k/5k/10k+ IPTV playlists. On startup detect incomplete prior startup, preserve diagnostics, avoid crash loops from corrupt cache, and provide safe reset.

## 40–41. Signing and CI
No signing credentials in Git. CI secrets provide keystore, alias, store password, key password. Build debug APK, signed release APK, and release AAB; verify signatures before promotion. Required build gate sequence: Repository validation → Compiler preflight → Unit tests → Free TV registry integrity → Debug lint → Release lint → Debug APK → Release APK → Release AAB → signature validation when configured → SHA-256 → artifact upload. Never call a build green unless the exact commit's CI is green.

## 42. GitHub rules
Canonical repo: `james-mull/astrawave-tv-build`. Working branch: `feature/nuvio-core-rebuild`. Do not modify protected beta branches. Before merge/promotion: inspect current branch, compare with v35 where source is available, preserve useful existing work, merge intentionally, run CI, fix exact compiler errors, repeat until green.

## 43–44. v35 handoff and build sequence
`AstraWave-v1-release-candidate-v35.zip` is the intended offline compiler-source handoff and must not be replaced by an older implementation. Compare GitHub ↔ v35 before intentional merge when the actual archive/source is available. Then run compiler preflight, unit tests, lintDebug, lintRelease, debug APK, release APK, release bundle; use an exact compiler-fix loop with minimum necessary changes until the exact commit is green.

## 45. Phone / real playback QA
Install debug APK and test onboarding, login, Home, catalogs, posters, search, movie/TV details, Stremio resolution, Live TV, Guide, Free TV, M3U, Xtream, XMLTV, Sports, Multiview, podcasts, settings, diagnostics. No crashes/freezes/blank major screens. Test at least 20–30 Free TV channels and classify success/dead/geo/slow/unstable. Verify broken-source → healthy-backup failover. Test VOD playable authorized source, episode, subtitles, no-source case, addon timeout, malformed addon, and no endless spinner.

## 46–48. Device QA
Android TV QA is mandatory for rail, shelves, detail pages, Guide, tuning, resolver, Sports, Multiview, Back behavior, focus restoration, resume; every action must be D-pad reachable. Repeat on Fire TV hardware/representative device, emphasizing decoder limits, memory, focus and remote behavior. Tablet must use adaptive layout, not a stretched phone UI.

## 49–51. Final Free TV / Guide / Sports QA
Before release health-check every built-in channel; remove dead URLs, mark geo restrictions, dedupe, verify logos/categories/EPG IDs. Validate Guide order/logos/Now-Next/time marker/timezone/favorites/groups/persistent position/refresh/large-list performance. Validate Sports dates/timezones/leagues/teams/channel matching/favorites/reminders/live detection/Sports Guide/Multiview launch.

## 52. Release blockers
Do not ship with compiler failure, release lint failure, startup crash, blank major screen, broken login persistence, unusable D-pad Guide, broken common playback paths, source secrets, HTTP default streams, Free TV integrity failure, or release signing failure.

## 53. Final promotion
Only after all blockers and mandatory device/playback QA are cleared may the release candidate be promoted to `AstraWave 1.0.0`. Generate signed APK, signed AAB, SHA-256, release notes, and source snapshot. Until then, all builds remain release candidates regardless of compilation success.
