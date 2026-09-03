# AstraWave v1.0 Production Release Gate

Phase 23 is complete only when every item below is verified.

## Build
- Android rebuild branch compiles cleanly.
- Pinned Nuvio baseline remains reproducible.
- Release APK and AAB are generated.
- Release signing/configuration is supplied through secure CI configuration, never committed.
- Version name/code are set for v1.0.

## Product
- All primary destinations are functional and non-placeholder.
- Universal Watch/Search/Watchlist routes to normalized sources.
- Custom lists, favorites, history and account work per profile.
- Live TV supports AstraWave Free TV plus user M3U/Xtream/XMLTV.
- Guide uses real combined channel/EPG data.
- Sports Watch actions appear only for resolved channels.
- Music/Podcasts/Radio works without blocking navigation.
- Firebase features degrade cleanly when Firebase is not configured.
- Personal-media and device-integration features expose setup/unsupported states cleanly.

## Reliability
- No known critical crash.
- No known navigation freeze.
- No blank core destination.
- No fake operational data.
- Dead streams and failed API calls produce recoverable states.
- Player failures return control to the user.

## TV / Fire TV
- D-pad focus paths verified.
- Back behavior verified.
- Focus indicators visible at 1080p and 4K.
- Guide and Sports are usable from a remote without touch input.

## Privacy / Safety
- Secrets are excluded from backup exports.
- Local-only/privacy settings are respected.
- Kids-profile restrictions are enforced before exposing restricted sources.
- Analytics remains opt-in unless policy changes explicitly.

## Legal / Distribution
- Nuvio GPLv3 attribution and source-distribution obligations are documented and satisfied for distributed derivative binaries.
- Third-party service attribution/terms are reviewed.
- AstraWave Free TV publication remains restricted to approved public/authorized feeds.

## Release package
- APK
- AAB
- Checksums
- Release notes
- QA report
- Known-issues list, if non-critical issues remain
- Source/notice package required by licenses

Do not tag or publish AstraWave v1.0 until all critical Phase 22 QA items and every mandatory item in this release gate pass.
