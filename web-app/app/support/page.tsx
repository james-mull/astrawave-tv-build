export const metadata = {
  title: 'Support | AstraWave',
  description: 'AstraWave support, billing, playback, and account help.',
};

export default function SupportPage() {
  return (
    <main style={{maxWidth: 900, margin: '0 auto', padding: '48px 24px 80px', lineHeight: 1.65}}>
      <p style={{opacity: .7, letterSpacing: '.12em', fontWeight: 700}}>ASTRAWAVE</p>
      <h1>Support</h1>
      <p>Help with your AstraWave account, subscription, playback, connected sources, and devices.</p>

      <h2>Subscription and billing</h2>
      <p>Android Premium purchases are billed through Google Play and must be verified to your signed-in AstraWave account before Premium activates. If you changed devices or reinstalled the app, sign in with the same AstraWave account and use <strong>Restore Purchases</strong> from the subscription screen.</p>

      <h2>Playback problems</h2>
      <p>If a title or live channel does not play, try the available Watch Options, confirm the connected source is still authorized and reachable, and check your network connection. AstraWave can automatically try eligible backup playback options when they are available.</p>

      <h2>Live TV and Guide</h2>
      <p>For customer-provided M3U or Xtream services, confirm the source credentials and playlist remain valid. Guide coverage depends on channel metadata and EPG mappings supplied by your source or an eligible AstraWave guide feed.</p>

      <h2>Personal media</h2>
      <p>For Plex, Jellyfin, Emby, WebDAV, NAS, or similar personal-media connections, confirm the server is online and that the account or token you supplied still has access to the requested library.</p>

      <h2>Account and data</h2>
      <p>Sign in to synchronize eligible profile information across devices. Household and parental-control settings are managed by the household administrator.</p>

      <h2>Before public launch</h2>
      <p>The verified customer-support email or contact form used for account, privacy, billing, and deletion requests must be published here and in the Google Play listing before AstraWave is sold to customers. Until that verified destination is configured, this page is a release-candidate support resource rather than the final public contact channel.</p>
    </main>
  );
}
