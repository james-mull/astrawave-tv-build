# AstraWave Nuvio Core Rebuild

This branch rebases the AstraWave client architecture around Nuvio while preserving the existing AstraWave feature set.

## Upstream baseline

- Upstream: https://github.com/NuvioMedia/NuvioMobile
- Branch: `cmp-rewrite`
- Pinned baseline commit: `9b09045f7af32073c8073893f7e324f135c8060a`
- License: GPL-3.0
- Android upstream build: `./gradlew :androidApp:assembleFullDebug`

AstraWave distributions derived from Nuvio must preserve required GPL notices and make the corresponding covered source available under GPL-3.0.

## Product direction

Use Nuvio for the media-client foundation and rebuild the presentation around AstraWave branding and a simplified Tuvora-style workflow.

### Nuvio-derived core

- Kotlin Multiplatform / Compose Multiplatform client foundation
- Android / Android TV navigation and focus behavior
- Movie and series discovery
- Search
- Title detail screens
- Playback foundation
- Collections / watchlist
- Playback progress
- Add-on / extension architecture
- Subtitle and metadata presentation

### AstraWave features that must be preserved

- Multiple M3U playlists
- Xtream Codes integration
- Combined Live TV catalog
- XMLTV EPG
- TiviMate-style grid guide
- Channel favorites / recent channels / groups
- Source health checks, ranking, failover and cleanup
- Sports schedules, leagues and favorite teams
- Sports event -> broadcaster -> authorized available channel matching
- Public-domain / Creative Commons / authorized free VOD discovery
- User-authorized cloud / debrid workflows
- Plex / Jellyfin / Emby / personal media
- Music, podcasts, video podcasts and radio
- Profiles, Kids and Guest modes
- Firebase Auth and Firestore cloud sync
- Watchlists, playback progress, favorites and settings sync
- AstraWave+ entitlements
- QR TV login / source setup
- Premium personalized Home rows
- Web app
- Public acquisition / SEO website
- Future multiview, authorized DVR, alerts, AI assistant, themes and backups

## UX target

`Nuvio engine + AstraWave feature set + Tuvora-style simplicity + TiviMate-quality Live TV / EPG.`

The existing `feature/android-unified-app` branch remains the working fallback beta until this branch reaches parity and passes device QA.

## Migration phases

1. Build pinned Nuvio upstream unchanged in CI.
2. Create AstraWave branding/theme/navigation overlay.
3. Port Firebase profile/cloud-sync contracts.
4. Port Live TV M3U/Xtream/XMLTV pipeline.
5. Port sports aggregation and favorite-team flows.
6. Add personal media, authorized source resolver, podcasts/music/radio.
7. Replace stock Home with AstraWave personalized rows.
8. Add Tuvora-style onboarding and QR TV setup.
9. Device QA on phone, tablet, Android TV and Fire TV.
10. Make this branch the release line only after feature parity is verified.
