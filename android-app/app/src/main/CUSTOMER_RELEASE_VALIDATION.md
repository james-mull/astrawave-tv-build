# AstraWave Customer Release Validation

This marker exists to trigger the Android build, release-candidate, and phone/tablet/TV smoke workflows on the exact customer-facing source state after release polish changes.

Current validation scope includes the Viewella-inspired content-first UI across Home, Movies/TV, Search, Live TV, Guide, Sports, My Stuff, onboarding, subscription/billing, player UI, VOD Watch Options, direct My List save/remove, phone-safe cinematic VOD hero actions, simplified phone navigation with My Stuff access, customer-facing season/episode browsing with progress and Watch Options terminology, Premium Hub wording, customer-readable playback/device/backup settings, simplified Kids Mode / privacy / cloud-sync language, in-app Help & Legal links for Support, Privacy Policy, Terms, and app version using the configured production AstraWave base URL, plus the final Content Sources experience with Recommended Sources, Connected Sources, Streaming Providers, Cloud & Debrid, advanced Community Sources, and authorization-gated playback.

Full Viewella-style mobile visual reset validation: edge-to-edge and flatter Home browsing, denser artwork-first rails, reduced card chrome, tighter phone typography and radii, simplified Movies/TV browsing, shorter title-detail hero with focused actions, content-first Live TV and Guide, search-first Search, library-first My Stuff with settings collapsed, simplified Sports mobile hierarchy, sparse mobile player controls, and stable five-tab bottom navigation (Home, Movies, TV, Live, Search) with no persistent top menu.

First-run validation now also covers the simplified ready-state onboarding experience: a minimal welcome, one primary Enter AstraWave action, and setup customization kept secondary instead of presenting a setup dashboard before normal use.

Final gallery-driven corrections: phones now route Live TV and Guide to their dedicated mobile layouts instead of TV-first multi-column/timeline layouts, while tablet/TV retain the richer large-screen experiences. Title details are action-first on mobile, with Play/Continue and Episodes/Watch Options immediately beneath the title before metadata, lists, and recommendations.

This pass validates the complete mobile visual reset while preserving TV remote/focus behavior and existing playback, billing, account, source, and authorization logic.
