# AstraWave Phase 2 / Phase 22 Device QA

This document is the execution record for the Phase 2 visual/device exit gate and the device-class portion of Phase 22 in `docs/ASTRAWAVE_MASTER_REBUILD_PLAN.md`.

Phase 2 and Phase 22 must not be marked complete in GitHub Issue #1 until every required device class below is checked, the integration smoke tests pass, and the visual benchmark is judged acceptable.

## Canonical build under test

Use this exact build for the current device qualification pass unless the branch changes afterward.

- Branch: `feature/nuvio-core-rebuild`
- Commit SHA: `28eddecd396c3f533b2ad4177a23055a97a42e70`
- CI run: `33757549876`
- CI result: `SUCCESS`
- Core invariant tests: `PASS`
- Debug APK build: `PASS`
- Artifact: `AstraWave-Android-debug`
- Artifact ID: `9894254349`
- Artifact digest: `sha256:d3d4ad6560c5db0a9d9ce69fe4910efb99bfddd3d413524d11cfbc09478ba61a`
- Debug QA activity: `com.astrawave.app/.Phase2VisualQaActivity`
- Modal QA activity: `com.astrawave.app/.Phase2ModalQaActivity`
- Launch command: `adb shell am start -n com.astrawave.app/.Phase2VisualQaActivity`

If the branch head changes after this commit, do not reuse a result for an affected surface. Rebuild, record the new SHA, and rerun the affected checks.

## Evidence requirements

For every device run, record:

- exact commit SHA
- device/model or emulator profile
- OS/API version
- orientation/resolution where relevant
- PASS/FAIL for every required item
- screenshot or screen-recording reference for visual/focus failures
- reproduction steps for any defect
- whether the defect blocks Phase 2, Phase 22, or both

A device section is not passed by launch-only testing.

## Required device matrix

### Android phone

- [ ] Exact commit SHA recorded
- [ ] App installs and launches without crash or blank destination
- [ ] Primary navigation is readable and unclipped
- [ ] Page/section typography hierarchy is clear
- [ ] Primary and secondary buttons render correctly
- [ ] Artwork cards preserve intended aspect ratio and spacing
- [ ] Loading, empty, unavailable, offline, partial-data and error states remain intentional
- [ ] No horizontal or vertical clipping at normal font scale
- [ ] Large-text/accessibility scaling remains usable
- [ ] Back navigation is predictable
- [ ] Rotation or supported orientation changes do not corrupt layout/state
- [ ] Overall polish meets or exceeds the Phase 2 reference benchmark

Tested commit: `________________`
Device / emulator: `________________`
Android version / API: `________________`
Resolution / orientation: `________________`
Result: `PASS / FAIL`
Evidence / notes: `________________`

### Android tablet

- [ ] Exact commit SHA recorded
- [ ] App installs and launches without crash or blank destination
- [ ] Rail/navigation layout uses available width cleanly
- [ ] Content density does not look phone-stretched
- [ ] Typography, gutters, cards, and artwork scale appropriately
- [ ] Loading, empty, unavailable, offline, partial-data and error states remain intentional
- [ ] No clipping in portrait or landscape where supported
- [ ] Large-text/accessibility scaling remains usable
- [ ] Back navigation and state restoration are predictable
- [ ] Overall polish meets or exceeds the Phase 2 reference benchmark

Tested commit: `________________`
Device / emulator: `________________`
Android version / API: `________________`
Resolution / orientation: `________________`
Result: `PASS / FAIL`
Evidence / notes: `________________`

### Android TV

- [ ] Exact commit SHA recorded
- [ ] App installs and launches without crash or blank destination
- [ ] Collapsed rail expands predictably on focus
- [ ] Rail does not flicker or collapse while moving between rail items
- [ ] All expected QA controls are visited in `Phase2VisualQaActivity`
- [ ] QA screen reports `Traversal PASS candidate`
- [ ] No disabled-control focus violation is reported
- [ ] Visible focus ring matches focus telemetry
- [ ] Left/right/up/down movement is predictable with no dead focus path
- [ ] Focus returns sensibly after Back/navigation changes
- [ ] Dialog/modal focus is trapped correctly and restored on close
- [ ] Selected/focused states are obvious from 10 feet
- [ ] Typography and artwork are legible from 10 feet
- [ ] No focused item is clipped by viewport edges
- [ ] Loading/error/empty states remain navigable by D-pad
- [ ] Overall polish meets or exceeds the Phase 2 reference benchmark

Tested commit: `________________`
Device / emulator: `________________`
Android TV version / API: `________________`
Resolution: `________________`
Result: `PASS / FAIL`
Evidence / notes: `________________`

### Fire TV

- [ ] Exact commit SHA recorded
- [ ] App installs and launches without crash or blank destination
- [ ] Collapsed rail expands predictably on focus
- [ ] Rail does not flicker or collapse during rapid remote navigation
- [ ] All expected QA controls are visited in `Phase2VisualQaActivity`
- [ ] QA screen reports `Traversal PASS candidate`
- [ ] No disabled-control focus violation is reported
- [ ] Visible focus ring matches focus telemetry
- [ ] Directional movement is predictable with no dead focus path
- [ ] Focus returns sensibly after Back/navigation changes
- [ ] Dialog/modal focus is trapped correctly and restored on close
- [ ] Select and Back behavior are consistent
- [ ] Selected/focused states are obvious from normal TV viewing distance
- [ ] Typography and artwork are legible from normal TV viewing distance
- [ ] No focused item is clipped by viewport edges
- [ ] Loading/error/empty states remain navigable by remote
- [ ] Overall polish meets or exceeds the Phase 2 reference benchmark

Tested commit: `________________`
Device model: `________________`
Fire OS version: `________________`
Resolution: `________________`
Result: `PASS / FAIL`
Evidence / notes: `________________`

## Phase 22 integration smoke matrix

Run these on at least one phone/tablet device and one TV-class device where the feature applies. Use authorized/configured sources only.

### Core navigation and stability
- [ ] Fresh install reaches onboarding without crash
- [ ] Returning launch does not lose required persisted state
- [ ] Every top-level destination opens without blank screen
- [ ] Rapid navigation does not freeze or force-close the app
- [ ] Back navigation does not trap the user

### TMDB / discovery
- [ ] Built-in movie catalogs load
- [ ] Built-in TV catalogs load
- [ ] Search returns metadata results
- [ ] Title details load metadata, cast/crew and available trailer metadata where present
- [ ] Missing TMDB configuration produces an intentional error state, not a blank page

### Stremio-compatible addons
- [ ] Enabled addon catalogs appear with provider attribution
- [ ] Disabled/profile-ineligible addons stay hidden
- [ ] Addon search results are identifiable by source/provider
- [ ] Unauthorized or unusable playback candidates are not exposed as playable

### My IPTV / Free TV / Guide
- [ ] User M3U source loads and survives restart
- [ ] Xtream source loads where valid credentials are configured
- [ ] XMLTV data maps to channels where IDs/names match
- [ ] Combined view deduplicates obvious duplicates without losing healthy alternatives
- [ ] Dead/unhealthy candidates are not labeled playable
- [ ] AstraWave Free TV shows only rights-approved/eligible feeds
- [ ] Guide grid is readable and D-pad navigable on TV
- [ ] Mini-guide/channel navigation does not break playback context

### Sports / Multiview
- [ ] Sports schedule loads metadata even when no playable source exists
- [ ] Watch is shown only when a healthy authorized matching source exists
- [ ] Dead matched channels do not inflate watchable counts
- [ ] Multiview only receives healthy eligible candidates
- [ ] 2-up/3-up/4-up layouts remain stable on a supported device
- [ ] Audio-focus switching behaves predictably

### Music / podcasts / radio
- [ ] RSS subscription loads episodes
- [ ] Failed/degraded feed remains visible with diagnostic state
- [ ] Radio URL playback path works for a valid configured station
- [ ] Queue / Continue Listening state behaves predictably
- [ ] Background/mini-player behavior does not interfere with video navigation

### Firebase / profile state
- [ ] Auth state persists across relaunch where configured
- [ ] Profiles remain separated
- [ ] Watchlist/favorites/progress sync without obvious overwrite regression
- [ ] Newer remote watchlist/favorite state can replace stale local state
- [ ] Local-only profile prevents cloud sync/analytics paths

### Personal media / resolver
- [ ] Enabled personal-media connection appears in source selection
- [ ] Blank/unusable URLs are rejected by central stream ranking
- [ ] Unauthorized candidates are filtered
- [ ] Duplicate provider/URL candidates are deduplicated
- [ ] Auto-best selects only an eligible candidate

### Device pairing / handoff
- [ ] Expired or malformed pairing payload is rejected
- [ ] Remote command requires a connected remote-capable device
- [ ] Playback handoff rejects stale/invalid transfers
- [ ] Valid handoff preserves media identity and playback position

### DVR / catch-up
- [ ] DVR controls appear only for a source advertising authorized capability
- [ ] Invalid/past recording requests are rejected
- [ ] Source maximum duration is enforced
- [ ] Timeshift/catch-up is hidden when unsupported

### Backup / restore
- [ ] Current-schema backup imports
- [ ] Newer unsupported schema is rejected
- [ ] Corrupt playback progress is rejected
- [ ] Credentials/secrets are not exported in portable backup data

### Entitlements / privacy / accessibility
- [ ] Expired Plus trial loses premium-only access
- [ ] Active Plus access exposes only intended software features
- [ ] Local-only mode disables cloud sync and analytics
- [ ] Large text/high contrast/reduced motion remain usable
- [ ] Kids restrictions block external addons/live groups as configured

## Visual benchmark

Compare the AstraWave shell against the product-quality target in the master rebuild plan. Nuvio and TiviMate are baselines to surpass, not designs to copy.

All must be true:

- [ ] AstraWave has a distinct, coherent visual identity
- [ ] Navigation is easier to understand than the baseline references
- [ ] TV focus treatment is stronger and clearer than stock baseline behavior
- [ ] Phone/tablet layouts feel intentionally designed for their device class
- [ ] Loading/empty/error/offline states never look unfinished
- [ ] Spacing, typography, artwork, elevation, motion, and controls feel consistent
- [ ] No obvious placeholder/debug styling remains in the primary shell
- [ ] Live TV / Guide does not feel materially less polished or usable than TiviMate
- [ ] Home / Movies / TV / Discover does not feel materially less polished than Nuvio
- [ ] Sports schedule looks like a premium sports product rather than a generic IPTV/data table

## Phase 2 exit decision

Only check this after all four device sections and the visual benchmark are complete.

- [ ] Phone passed
- [ ] Tablet passed
- [ ] Android TV passed
- [ ] Fire TV passed
- [ ] Visual benchmark passed
- [x] Exact canonical head commit has green Android CI (`28eddecd396c3f533b2ad4177a23055a97a42e70`, run `33757549876`)

**Phase 2 exit gate:** [ ] PASS

## Phase 22 device/integration exit decision

Only check this after the full device matrix, integration smoke matrix, and benchmark checks are complete.

- [ ] Phone integration pass
- [ ] Tablet integration pass
- [ ] Android TV integration pass
- [ ] Fire TV integration pass
- [ ] D-pad/focus pass
- [ ] Core service/source integration pass
- [ ] No critical crashes or blank destinations
- [ ] Visual benchmark pass
- [x] Core invariant tests pass in CI
- [x] Debug APK builds and uploads in CI

**Phase 22 device/integration gate:** [ ] PASS

If any item fails, keep Phase 2/22 open in Issue #1, fix the defect on `feature/nuvio-core-rebuild`, rerun affected checks, and record the new tested commit.

## Source-policy guardrails

Phase 2/22 QA must not weaken source eligibility rules:

- AstraWave Free TV remains restricted to authorized/public feeds.
- My IPTV remains user-supplied M3U/Xtream/XMLTV configuration.
- Metadata/catalog presence never implies playback authorization.
- Health/availability does not override authorization/rights eligibility.
- No release gate may be passed using fake operational data or unauthorized playback sources.
