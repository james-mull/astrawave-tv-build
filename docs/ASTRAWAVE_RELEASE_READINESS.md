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
- Android release metadata is versionName 1.0.0 and versionCode 36.
- The final-version Android Build #631 completed successfully.
- The final-version Release Candidate #370 completed successfully through compiler preflight, unit tests, Free TV integrity, debug/release lint, debug APK, release APK/AAB, checksum generation and artifact upload.
- The latest inspected v1.0 RC artifact contains APK, AAB, lint reports, SHA256SUMS.txt and RELEASE_METADATA.txt.
- A dedicated manual-only production release workflow now exists and hard-fails unless all release-signing secrets are supplied and both APK/AAB signature checks succeed.
- Navigation contract regression tests pin the phone, tablet and TV destination contracts to prevent late navigation regressions.

## Release blockers

### 1. Production Android signing is not configured

The latest inspected v1.0 release-candidate metadata reports:

- signed=false
- promotion_eligible=false
- APK output: app-release-unsigned.apk

This is acceptable for a test/QA release candidate, but it is not a publishable production package.

Production promotion must use `.github/workflows/build-production-release.yml`, which requires all of these GitHub Actions secrets:

- ASTRAWAVE_RELEASE_KEYSTORE_B64
- ASTRAWAVE_RELEASE_STORE_PASSWORD
- ASTRAWAVE_RELEASE_KEY_ALIAS
- ASTRAWAVE_RELEASE_KEY_PASSWORD

The production workflow refuses to continue when any signing secret is absent. It also requires versionName 1.0.0 / versionCode 36, builds the signed APK and AAB, verifies the APK with apksigner, verifies the AAB with jarsigner, verifies SHA-256 checksums, and only then emits metadata with:

- signed=true
- promotion_eligible=true

Do not use a normal RC artifact for public distribution.

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

Create exactly one final Vercel checkpoint preview only after production Android signing and final device/playback QA are green. Do not create intermediate checkpoint previews.

### 4. Final distribution notices still require release-package confirmation

Before publication, verify the release bundle includes every required third-party notice/source-distribution item, including Nuvio/GPL obligations applicable to the distributed derivative and any other required attribution or source notices.

## Release decision rule

AstraWave v1.0 is RELEASE READY only when all of the following are true at the same time:

1. Latest Android build is green.
2. Latest Release Candidate is green.
3. Latest web build is green.
4. Navigation regression tests are green.
5. Production Release workflow completes successfully.
6. Production metadata reports signed=true and promotion_eligible=true.
7. APK and AAB signatures validate successfully.
8. Device/playback QA matrix has no critical blocker.
9. Final Vercel checkpoint passes web QA.
10. Required notices / licensing obligations are confirmed in the release package.

Until then, status remains NOT READY TO PUBLISH.
