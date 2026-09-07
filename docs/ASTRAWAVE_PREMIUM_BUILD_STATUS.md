# AstraWave Premium Build Status

This file is an implementation ledger for premium execution paths. It is intentionally factual: features are only marked implemented when code has a real execution path; provider/customer authorization remains required for media access.

## Implemented foundations
- Unified VOD Play Best with personal media, customer Xtream VOD, reviewed/public sources, explicitly authorized Stremio providers, health ranking, backup failover, and optional Real-Debrid optimization.
- Source decision diagnostics locally and profile-scoped cloud metadata.
- Channel Studio / channel customization with event-driven refresh in open Live and Guide screens.
- Synchronized Android timeline Guide viewport with fixed channel column.
- Sports source fusion and hardware-aware Multiview.
- WorkManager-based authorized direct download transport with network constraints, app-private storage, progress, retry, cancel, and Travel Mode storage policy.
- Google Play Billing client integration and backend purchase-verification endpoint; Premium remains server-entitlement-authoritative and paid-through expiry is enforced locally.

## Active release gates
- Configure Google Play Console Premium subscription and server service-account credentials.
- Set ASTRAWAVE_API_BASE_URL and ASTRAWAVE_PREMIUM_PRODUCT_ID in Android CI/release environment.
- Validate Google Play internal-test purchase, restore, pending purchase, canceled paid-through access, expiry, and acknowledgement.
- Complete download UI enqueue/progress/cancel/retry/offline-playback QA.
- Complete DVR recording transport/storage/conflict handling.
- Complete sports notification/reminder scheduling.
- Complete foreground remote-command consumption/device handoff transport.
- Continue universal web search, Control Center parity, household/watch-night, source intelligence, sports overlays, and remaining premium roadmap items.

## Release rule
Do not create/promote the final Vercel checkpoint or claim production readiness until current Android Release Candidate + web builds are green and device/playback QA is complete.
