# AstraWave v1.0 Release Readiness

Status: NOT READY TO PUBLISH

This file is the current go/no-go source of truth for AstraWave v1.0. Do not tag, publish, or distribute the production release until every blocker below is cleared.

## Verified green

- Android application compiles cleanly on the current rebuild branch.
- Android core invariant tests pass.
- Android debug and release lint gates pass.
- AstraWave Free TV registry integrity check passes.
- Release APK and AAB generation succeeds.
- SHA-256 release checksums are generated and have been independently verified against the generated artifacts.
- Web production build passes.
- Web mobile primary navigation is Home / Live / Sports / Movies / My.
- Android phone primary navigation is Home / Live / Sports / Movies / My with no duplicate top navigation strip.
- Home VOD routes through the premium watch-first detail flow while episode resume preserves episode context.
- Configurable AstraWave / Ultra MAX-style Discover is the visible Android discovery destination.
- Public Trakt Trending / Popular / Anticipated rows are active and metadata-enriched.
- Guide phone Replay targets the most recent ended program rather than the currently airing program.
- Web playback has ranked automatic source failover.
- Web playback supports native HLS where available and HLS.js fallback elsewhere.
- Browser-only playback failure does not globally mark a backend source dead.
- Android release metadata is now versionName 1.0.0 and versionCode 36.

## Release blockers

### 1. Production Android signing is not configured

The latest inspected release-candidate metadata reported:

- signed=false
- promotion_eligible=false

The release workflow already supports secure CI-only signing through these GitHub Actions secrets:

- ASTRAWAVE_RELEASE_KEYSTORE_B64
- ASTRAWAVE_RELEASE_STORE_PASSWORD
- ASTRAWAVE_RELEASE_KEY_ALIAS
- ASTRAWAVE_RELEASE_KEY_PASSWORD

A production release must not be promoted until those credentials are configured and a new RC reports signed=true and promotion_eligible=true, with APK and AAB signature validation passing.

### 2. Final device / playback QA is still required

Mandatory final manual or device-backed checks:

- Android phone portrait
- Android phone landscape
- Android tablet
- Android TV 1080p
- Android TV 4K
- Fire TV remote / D-pad behavior
- Desktop web
- Mobile web

For each applicable surface verify:

- launch and first-run onboarding
- profile picker and profile switching
- Home
- Movies
- TV Shows
- Discover
- Search
- watch-first title details
- movie Play Best
- series Continue / Start / episode navigation
- Live TV
- source switching and automatic failover
- Guide now/next
- Guide Replay only when authorized catch-up is supported
- Sports schedule and resolved Watch action
- Multiview
- Music / podcasts / radio
- My AstraWave
- Source Manager
- back navigation
- no critical blank screen, freeze, or crash

TV / Fire TV additionally require visible focus state, predictable Back behavior, and remote-only usability for Guide and Sports.

### 3. Final web checkpoint has not been created

Create exactly one final Vercel checkpoint preview only after Android signing and final device/playback QA are green. Do not create intermediate checkpoint previews.

## Release decision rule

AstraWave v1.0 is RELEASE READY only when all of the following are true at the same time:

1. Latest Android build is green.
2. Latest Release Candidate is green.
3. Latest web build is green.
4. RC metadata reports signed=true and promotion_eligible=true.
5. APK and AAB signatures validate successfully.
6. Device/playback QA matrix has no critical blocker.
7. Final Vercel checkpoint passes web QA.
8. Required notices / licensing obligations are included in the release package.

Until then, status remains NOT READY TO PUBLISH.
