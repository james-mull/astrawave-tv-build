export const metadata = {
  title: 'Privacy Policy | AstraWave',
  description: 'AstraWave privacy policy and data-handling overview.',
};

const updated = 'September 14, 2026';

export default function PrivacyPage() {
  return (
    <main style={{maxWidth: 900, margin: '0 auto', padding: '48px 24px 80px', lineHeight: 1.65}}>
      <p style={{opacity: .7, letterSpacing: '.12em', fontWeight: 700}}>ASTRAWAVE</p>
      <h1>Privacy Policy</h1>
      <p>Last updated: {updated}</p>

      <h2>Overview</h2>
      <p>AstraWave is entertainment software that helps you organize, discover, and play media from sources you are authorized to use. This policy describes the information AstraWave may process when you use the app, website, account sync, subscriptions, connected sources, and related services.</p>

      <h2>Information you provide</h2>
      <p>Depending on the features you use, AstraWave may process account information such as your email address and profile name; household profiles and preferences; watchlist, favorites, playback progress, history, sports preferences, and settings; and configuration details for media sources you choose to connect.</p>

      <h2>Authentication and cloud sync</h2>
      <p>AstraWave may use Firebase Authentication and Firestore to authenticate your account and synchronize eligible profile data across devices. Cloud-synced data is associated with your signed-in account. Features that do not require cloud sync may remain local to your device.</p>

      <h2>Connected media sources</h2>
      <p>If you connect IPTV, personal-media servers, compatible addons, or other authorized services, AstraWave may process the connection details needed to access those sources on your behalf. Credentials and tokens should be handled only for the service or source you explicitly connect. AstraWave does not grant rights to content you do not already have permission to access.</p>

      <h2>Subscriptions and billing</h2>
      <p>Android subscriptions are billed through Google Play. AstraWave receives purchase identifiers and verification results needed to confirm entitlement status, restore purchases, and activate paid software features. AstraWave does not receive your full payment-card number from Google Play.</p>

      <h2>Playback and reliability data</h2>
      <p>AstraWave may process playback state, selected source information, stream availability, error details, and device capability information when needed to provide playback, resume, failover, troubleshooting, and compatibility features. We aim to limit this processing to what is necessary for the feature being used.</p>

      <h2>Children and household controls</h2>
      <p>AstraWave includes household profiles and parental controls. The primary account holder is responsible for configuring child profiles, ratings, approved content, bedtime, and other household restrictions. AstraWave is not intended to let children independently purchase subscriptions or bypass household controls.</p>

      <h2>Third-party services</h2>
      <p>Features may rely on services such as Google Play, Firebase, metadata providers, podcast or sports directories, and media sources you connect. Those providers may process data under their own privacy policies. AstraWave only intends to request access needed for the feature you choose to use.</p>

      <h2>Data retention and deletion</h2>
      <p>Local app data remains on the device until removed by the app, operating system, or user. Cloud account data may remain while your account is active or as needed for security, billing, legal, and service-integrity purposes. Account and data-deletion requests will be handled through the support destination published with AstraWave.</p>

      <h2>Security</h2>
      <p>We use reasonable technical safeguards intended to protect authentication, entitlement, and connected-source data. No internet-connected service can guarantee absolute security, so you should use unique credentials and only connect sources you trust.</p>

      <h2>Your choices</h2>
      <p>You can choose whether to sign in, which compatible sources to connect, which household features to enable, and whether to use optional cloud or personalization features where available. Google Play provides subscription-management controls for Android purchases.</p>

      <h2>Changes to this policy</h2>
      <p>We may update this policy as AstraWave changes. The effective date above will be updated when material revisions are published.</p>

      <h2>Contact</h2>
      <p>For privacy, account, or data requests, use the official AstraWave support contact published in the Google Play listing or the AstraWave Support page. A verified support contact must be configured before public launch.</p>
    </main>
  );
}
