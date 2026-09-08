# AstraWave v1.0 Third-Party Notices

This file accompanies AstraWave v1.0 release artifacts and documents the principal third-party components and distribution obligations tracked by the project.

## Nuvio Mobile

AstraWave's client architecture is derived in part from Nuvio Mobile.

- Upstream project: NuvioMedia/NuvioMobile
- Pinned AstraWave baseline commit: `9b09045f7af32073c8073893f7e324f135c8060a`
- License: GNU General Public License v3.0 (GPL-3.0)

For any AstraWave binary distribution containing GPL-covered derivative code, the corresponding covered source code and required license notices must be made available under GPL-3.0. AstraWave tracks the corresponding source on the rebuild branch and includes `AstraWave-TV-source.zip` in the repository as a distribution source package reference. The production release process must verify that the source package corresponds to the released commit before publication.

## Android / Jetpack / Media3

AstraWave uses AndroidX, Jetpack Compose, Media3 / ExoPlayer, WorkManager, Navigation and related Android libraries. These components are distributed under their respective upstream licenses, principally Apache License 2.0. Their copyright and license terms remain those of their upstream projects.

## Firebase

AstraWave uses Firebase Auth and Cloud Firestore client libraries. Use and redistribution are subject to Google's Firebase SDK terms and the licenses shipped with those SDKs.

## Kotlin and kotlinx.coroutines

AstraWave uses Kotlin and kotlinx.coroutines under their respective JetBrains / Apache 2.0 licensing terms.

## Coil

AstraWave uses Coil for image loading under the Apache License 2.0.

## Web dependencies

The AstraWave web app uses Next.js, React, React DOM, lucide-react, Firebase web SDK and hls.js. Their upstream licenses and notices apply. hls.js is used to provide HLS playback on browsers that do not expose native HLS support.

## Metadata and external services

AstraWave may interoperate with services such as TMDB, Trakt, TheSportsDB and customer-authorized media sources. Those services retain their own trademarks, data rights, API terms and attribution requirements. AstraWave does not claim ownership of third-party metadata, trademarks or customer-provided media catalogs.

## AstraWave Free TV

AstraWave Free TV publication is restricted to reviewed public, authorized, public-domain, Creative Commons, or otherwise legally distributable feeds. Inclusion in source registries does not transfer rights in third-party channel names, logos, programming or trademarks.

## Release requirement

Before a production release is published, the release package must contain or reference:

1. This third-party notices file.
2. The exact release commit SHA.
3. The corresponding GPL-covered source package or an equivalent durable source-distribution location tied to that release.
4. The signed APK and AAB.
5. SHA-256 checksums and release provenance metadata.

This notice is an engineering distribution record and does not replace the full text of any applicable third-party license.