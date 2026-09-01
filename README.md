# AstraWave

Android and web builds for AstraWave.

## Firebase setup

Use one Firebase project on the Spark plan:

- Project display name: `AstraWave`
- Project ID: `astrawave`, or the available Firebase-generated variant if `astrawave` is taken
- Android package: `com.astrawave.app`
- Web app nickname: `AstraWave Web`
- Auth provider: Email/Password
- Database: Cloud Firestore

The repo already contains the deploy config:

- `.firebaserc`
- `web-app/firebase.json`
- `web-app/firestore.rules`
- `web-app/firestore.indexes.json`

After creating the Firebase apps, set these values for the web build:

```bash
NEXT_PUBLIC_FIREBASE_API_KEY=AIzaSyDCVU-Xte6aog6xPkDdqcNny1reQjn7lrY
NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN=astrawave.firebaseapp.com
NEXT_PUBLIC_FIREBASE_PROJECT_ID=astrawave
NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET=astrawave.firebasestorage.app
NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID=1053104069475
NEXT_PUBLIC_FIREBASE_APP_ID=1:1053104069475:web:122e095549dafb87f81156
```

Set these values for the Android build as Gradle properties or environment variables:

```bash
ASTRAWAVE_FIREBASE_API_KEY=AIzaSyDoQ35kpsWeVGI99SNYeLiwWMoV-Rk0FvA
ASTRAWAVE_FIREBASE_APP_ID=1:1053104069475:android:7a00e70c7edcb7e3f81156
ASTRAWAVE_FIREBASE_PROJECT_ID=astrawave
ASTRAWAVE_FIREBASE_SENDER_ID=1053104069475
```

Deploy Firestore rules from `web-app`:

```bash
firebase deploy --only firestore:rules,firestore:indexes --project astrawave
```

## Free live TV data

The web app uses `https://iptv-org.github.io/iptv/index.m3u` as its free default live TV playlist when `ASTRAWAVE_LIVE_M3U_URL` is empty. Set `ASTRAWAVE_LIVE_M3U_URL` only when you want to replace it with your own authorized M3U feed.

## Builds

Web:

```bash
cd web-app
npm install
npm run build
```

Android:

```bash
cd android-app
gradle --no-daemon assembleDebug
```
