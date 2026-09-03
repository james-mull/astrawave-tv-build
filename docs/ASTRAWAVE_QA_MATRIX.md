# AstraWave v1.0 QA Matrix

This file defines Phase 22 exit gates. A phase is not complete because code exists; each gate must be verified on the rebuild branch.

## Device matrix
- Android phone portrait
- Android phone landscape
- Android tablet
- Android TV 1080p
- Android TV 4K
- Fire TV remote navigation
- Desktop web

## Core navigation
- Home loads without blank state
- Movies loads configured TMDB rows or explicit setup state
- TV loads configured TMDB rows or explicit setup state
- Live TV opens My IPTV / AstraWave Free TV management
- Guide opens combined channel/EPG state
- Sports opens schedule and real matched Watch candidates only
- Music & Podcasts opens RSS/radio library state
- Discover loads catalogs
- Search opens universal-search flow
- My AstraWave opens lists, favorites, watchlist, history and account

## Regression checks
- Login/session persistence
- No crash on More/account navigation
- No frozen navigation transitions
- No blank/misaligned inner pages
- Player handles missing or dead streams without app crash
- TMDB missing credential produces setup state, not crash
- Real-Debrid/user cloud integration never appears as built-in content discovery
- Guide has no fake channel counts or synthetic uptime values
- Sports has no fake event-to-channel mapping

## Live TV / Guide
- M3U source add/test/load
- Xtream source add/test/load
- XMLTV mapping
- Multiple source merge
- AstraWave Free TV merge
- Source priority/failover
- Dead stream handling
- D-pad Guide navigation
- Current-program rendering

## Personal library
- Watchlist
- Favorites
- Custom lists
- Continue Watching / Listening
- History
- Profile scoping
- Firebase sync when configured
- Local-only behavior when cloud is disabled

## TV UX
- All focusable controls visible
- Focus never becomes trapped
- Back navigation is predictable
- 10-foot text sizes remain readable
- Large focus indicators work with accessibility preference

## Multiview
- 2-up
- 3-up
- 4-up
- Audio focus switches to selected pane
- Invalid duplicate pane IDs rejected
- Session never exceeds layout capacity

## Kids / privacy / accessibility
- Kids profile hides disallowed external addons
- Live TV respects kids policy
- Local-only mode avoids cloud writes
- Captions preference retained
- Text scale/high-contrast/reduce-motion preferences retained

## Performance
- Cold launch does not block indefinitely on network calls
- Catalog rows show loading/error states
- Guide loading is asynchronous
- Sports loading is asynchronous
- Audio loading is asynchronous
- No main-thread network fetches in new rebuild screens

## Release artifacts
- Debug APK produced by CI
- Nuvio pinned baseline still builds
- Release APK/AAB pipeline succeeds
- Version metadata set
- GPL notices/source obligations documented
- Release notes generated

Phase 22 closes only after this matrix is executed and all critical failures are cleared.
