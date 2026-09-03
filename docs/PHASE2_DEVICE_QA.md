# AstraWave Phase 2 Device QA

This document is the execution record for the Phase 2 exit gate in `docs/ASTRAWAVE_MASTER_REBUILD_PLAN.md`.

Phase 2 must not be marked complete in GitHub Issue #1 until every required device class below is checked and the visual benchmark is judged acceptable.

## Build under test

- Branch: `feature/nuvio-core-rebuild`
- Debug QA activity: `com.astrawave.app/.Phase2VisualQaActivity`
- Launch command: `adb shell am start -n com.astrawave.app/.Phase2VisualQaActivity`

Record the exact tested commit SHA for every device run. Do not reuse a result after a design-system or navigation-shell change unless the affected checks are rerun.

## Required device matrix

### Android phone

- [ ] Exact commit SHA recorded
- [ ] App launches without crash or blank destination
- [ ] Primary navigation is readable and unclipped
- [ ] Page/section typography hierarchy is clear
- [ ] Primary and secondary buttons render correctly
- [ ] Artwork cards preserve intended aspect ratio and spacing
- [ ] Loading, empty, unavailable, and error states remain intentional
- [ ] No horizontal or vertical clipping at normal font scale
- [ ] Overall polish meets or exceeds the Phase 2 reference benchmark

Tested commit: `________________`
Device / emulator: `________________`
Notes: `________________`

### Android tablet

- [ ] Exact commit SHA recorded
- [ ] App launches without crash or blank destination
- [ ] Rail/navigation layout uses available width cleanly
- [ ] Content density does not look phone-stretched
- [ ] Typography, gutters, cards, and artwork scale appropriately
- [ ] Loading, empty, unavailable, and error states remain intentional
- [ ] No clipping in portrait or landscape where supported
- [ ] Overall polish meets or exceeds the Phase 2 reference benchmark

Tested commit: `________________`
Device / emulator: `________________`
Notes: `________________`

### Android TV

- [ ] Exact commit SHA recorded
- [ ] App launches without crash or blank destination
- [ ] Collapsed rail expands predictably on focus
- [ ] Rail does not flicker or collapse while moving between rail items
- [ ] All expected QA controls are visited in `Phase2VisualQaActivity`
- [ ] QA screen reports `Traversal PASS candidate`
- [ ] No disabled-control focus violation is reported
- [ ] Visible focus ring matches focus telemetry
- [ ] Left/right/up/down movement is predictable with no dead focus path
- [ ] Selected/focused states are obvious from 10 feet
- [ ] Typography and artwork are legible from 10 feet
- [ ] No focused item is clipped by viewport edges
- [ ] Overall polish meets or exceeds the Phase 2 reference benchmark

Tested commit: `________________`
Device / emulator: `________________`
Notes: `________________`

### Fire TV

- [ ] Exact commit SHA recorded
- [ ] App launches without crash or blank destination
- [ ] Collapsed rail expands predictably on focus
- [ ] Rail does not flicker or collapse during rapid remote navigation
- [ ] All expected QA controls are visited in `Phase2VisualQaActivity`
- [ ] QA screen reports `Traversal PASS candidate`
- [ ] No disabled-control focus violation is reported
- [ ] Visible focus ring matches focus telemetry
- [ ] Directional movement is predictable with no dead focus path
- [ ] Select and Back behavior are consistent
- [ ] Selected/focused states are obvious from normal TV viewing distance
- [ ] Typography and artwork are legible from normal TV viewing distance
- [ ] No focused item is clipped by viewport edges
- [ ] Overall polish meets or exceeds the Phase 2 reference benchmark

Tested commit: `________________`
Device model: `________________`
Fire OS version: `________________`
Notes: `________________`

## Visual benchmark

Compare the AstraWave shell against the product-quality target in the master rebuild plan. Nuvio and TiviMate are baselines to surpass, not designs to copy.

All must be true:

- [ ] AstraWave has a distinct, coherent visual identity
- [ ] Navigation is easier to understand than the baseline references
- [ ] TV focus treatment is stronger and clearer than stock baseline behavior
- [ ] Phone/tablet layouts feel intentionally designed for their device class
- [ ] Loading/empty/error states never look unfinished
- [ ] Spacing, typography, artwork, elevation, motion, and controls feel consistent
- [ ] No obvious placeholder styling remains in the primary shell

## Phase 2 exit decision

Only check this after all four device sections and the visual benchmark are complete.

- [ ] Phone passed
- [ ] Tablet passed
- [ ] Android TV passed
- [ ] Fire TV passed
- [ ] Visual benchmark passed
- [ ] Exact tested head commit has green Android CI

**Phase 2 exit gate:** [ ] PASS

If any item fails, keep Phase 2 open in Issue #1, fix the defect on `feature/nuvio-core-rebuild`, rerun affected checks, and record the new tested commit.

## Source-policy guardrails

Phase 2 QA must not weaken source eligibility rules:

- AstraWave Free TV remains restricted to authorized/public feeds.
- My IPTV remains user-supplied M3U/Xtream/XMLTV configuration.
- Metadata/catalog presence never implies playback authorization.
