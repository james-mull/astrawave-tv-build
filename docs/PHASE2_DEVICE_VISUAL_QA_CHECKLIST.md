# Phase 2 — Device Visual QA Checklist

This checklist is the evidence companion to `docs/ASTRAWAVE_MASTER_REBUILD_PLAN.md` and GitHub Issue #1. Phase 2 must not be marked complete until every required device class passes on the exact commit being closed.

## Required build identity

Record the exact `BuildConfig.GIT_SHA` shown by the debug QA activities. Evidence from an older commit does not carry forward.

Commit under review: `<sha>`

## Required device classes

Complete every section independently on:

- [ ] Android phone
- [ ] Android tablet
- [ ] Android TV
- [ ] Fire TV

For TV-class devices, use the real remote and test Up, Down, Left, Right, Select, and Back. Touch or emulator-only traversal is not sufficient.

## Main shell and navigation

- [ ] AstraWave branding, typography, spacing, radii, elevation, and artwork treatment are consistent.
- [ ] Top-level navigation matches the master rebuild plan.
- [ ] Selected and focused destinations are unmistakable.
- [ ] No clipped labels, focus rings, cards, or controls.
- [ ] D-pad movement is predictable with no focus traps or dead ends.
- [ ] Returning from a destination does not produce a broken or invisible focus state.

## Shared controls

- [ ] Primary and secondary buttons share the canonical AstraWave focus treatment.
- [ ] Disabled controls cannot receive actionable focus.
- [ ] Focus scale/elevation does not clip neighboring content.
- [ ] Text remains readable at normal viewing distance.
- [ ] Reduced-motion behavior does not break focus visibility or usability.

## Focus-aware modal QA

Run `.Phase2ModalQaActivity` on the same device and exact commit before final device verification.

- [ ] Confirm receives initial focus.
- [ ] Left/right traversal reaches Confirm and Cancel.
- [ ] Back dismisses correctly.
- [ ] Focus returns to the launcher control after dismissal/confirmation.
- [ ] Focus ring remains visible and unclipped.
- [ ] Modal QA is recorded for the same device class and exact commit.

## Artwork

- [ ] Poster, backdrop, and square artwork keep correct aspect ratios.
- [ ] Slow/failed remote artwork falls back gracefully instead of leaving blank cards.
- [ ] No stretched or visibly low-resolution artwork.
- [ ] Text remains readable over artwork/gradients.

## Operational states

Verify all canonical states are intentional, readable, and action-safe:

- [ ] Loading
- [ ] Empty
- [ ] Error/retry
- [ ] Offline
- [ ] Partial data
- [ ] Unauthenticated
- [ ] No eligible/playable source
- [ ] Stale playlist/source
- [ ] EPG unavailable

No state may degrade into a blank page or imply that unavailable content is playable.

## Guide / Live TV benchmark

Compare the AstraWave shell and current Guide/Live TV presentation against the Phase 2 TiviMate usability/polish target.

- [ ] Clear hierarchy for channel, program, source, and status.
- [ ] Focus/readability are strong at TV viewing distance.
- [ ] Navigation feels fast and predictable.
- [ ] Empty/error/unavailable states look designed rather than diagnostic/debug-like.
- [ ] Current presentation is not materially less polished or less usable than the benchmark.

## Sports benchmark

Compare the current sports presentation against the master plan's premium sports requirement.

- [ ] Featured game hierarchy is obvious.
- [ ] Live/upcoming/unavailable states are visually distinct.
- [ ] Broadcaster/source status is clear.
- [ ] Watch/Multiview actions are only exposed when eligible.
- [ ] Schedule presentation does not look like a generic IPTV/data table.

## Nuvio discovery/home benchmark

- [ ] Content hierarchy and artwork presentation are at least as polished as the Nuvio baseline.
- [ ] AstraWave identity is visually distinct rather than a lightly re-skinned Nuvio surface.
- [ ] Layout does not feel stretched between phone/tablet/TV classes.

## Final per-device evidence

For each device class record:

- exact commit SHA
- physical device/model
- Android/Fire OS version
- display resolution / TV mode where relevant
- main traversal result
- modal traversal result
- visual benchmark result
- any defects found and commit that fixed them

Only after all four device classes pass on the same exact commit may Phase 2 be checked complete in Issue #1.
