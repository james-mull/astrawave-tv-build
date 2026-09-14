# AstraWave Customer Release Gate

A build is not customer-promotion eligible unless all of the following are true:

- Web app and `/api/astrawave/billing/verify` build successfully from the same commit.
- Android debug/release Kotlin compilation, unit tests, lint, APK and AAB builds pass.
- Release APK/AAB are signed with production credentials and signature verification passes.
- Firebase Android configuration, AstraWave API base URL, and Google Play Premium product ID are configured in CI.
- The deployed billing verifier has a Google Play service account with Android Publisher access and permission to write AstraWave entitlements.
- `RELEASE_METADATA.txt` reports `promotion_eligible=true`.
- Phone, tablet, and Android TV smoke checks pass for the customer-facing app code.
- A real Google Play test purchase and restore completes and produces a verified Premium entitlement before launch.
- Public privacy, terms, and support destinations are configured for store distribution.

CI release artifacts that do not satisfy these conditions are test/release-candidate artifacts only and must not be sold or promoted as production builds.
