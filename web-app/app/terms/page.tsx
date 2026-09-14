export const metadata = {
  title: 'Terms of Service | AstraWave',
  description: 'Terms governing use of AstraWave software and services.',
};

const updated = 'September 14, 2026';

export default function TermsPage() {
  return (
    <main style={{maxWidth: 900, margin: '0 auto', padding: '48px 24px 80px', lineHeight: 1.65}}>
      <p style={{opacity: .7, letterSpacing: '.12em', fontWeight: 700}}>ASTRAWAVE</p>
      <h1>Terms of Service</h1>
      <p>Last updated: {updated}</p>

      <h2>1. The service</h2>
      <p>AstraWave provides software for entertainment discovery, organization, playback, personalization, household features, and access to compatible sources that you are authorized to use. AstraWave does not sell ownership of third-party movies, television channels, sports events, music, or other media unless expressly stated.</p>

      <h2>2. Your responsibility for connected sources</h2>
      <p>You may only connect, configure, or play sources and media that you have the legal right and authorization to access. You are responsible for complying with the terms, licenses, geographic restrictions, and applicable laws governing third-party services and content you use with AstraWave.</p>

      <h2>3. Accounts</h2>
      <p>You are responsible for your AstraWave account, household profiles, credentials, and device access. Keep account credentials secure and notify support if you believe your account has been compromised. Household administrators are responsible for parental-control and child-profile settings.</p>

      <h2>4. Subscriptions</h2>
      <p>Paid Android subscriptions are processed through Google Play. Prices, taxes, billing periods, trials, cancellation, refunds, and renewal behavior are subject to the terms shown during Google Play checkout and applicable Google Play policies. Premium features activate only after AstraWave verifies the purchase to the signed-in account.</p>

      <h2>5. Software features and availability</h2>
      <p>Feature availability may depend on your device, location, connected sources, third-party services, subscription level, network conditions, and provider support. Some features may be unavailable for a particular source or device even when they are available elsewhere in AstraWave.</p>

      <h2>6. Third-party services</h2>
      <p>AstraWave may interoperate with third-party metadata providers, Google Play, Firebase, personal-media servers, compatible extensions, and services you choose to connect. Those services are governed by their own terms. AstraWave is not responsible for third-party outages, catalog changes, stream availability, pricing, policies, or content decisions.</p>

      <h2>7. Acceptable use</h2>
      <p>You may not use AstraWave to obtain unauthorized access to systems or media, defeat access controls, distribute unlawful content, interfere with the service, abuse accounts, or use the software in a way that violates applicable law or the rights of others.</p>

      <h2>8. Updates and changes</h2>
      <p>We may update AstraWave to improve reliability, security, compatibility, or features. We may modify or discontinue features when required by technical, legal, provider, or platform changes. Material subscription changes will be handled in accordance with applicable law and store policies.</p>

      <h2>9. Disclaimers</h2>
      <p>AstraWave is provided on an as-available basis to the extent permitted by law. We do not guarantee that every connected source, stream, program guide, metadata result, device integration, or third-party service will always be available, accurate, uninterrupted, or compatible.</p>

      <h2>10. Limitation of liability</h2>
      <p>To the extent permitted by applicable law, AstraWave and its operators are not liable for indirect, incidental, special, consequential, or punitive damages arising from use of the service, third-party sources, network failures, or loss of access. Nothing in these terms limits rights that cannot legally be waived.</p>

      <h2>11. Termination</h2>
      <p>You may stop using AstraWave at any time and may manage eligible subscriptions through Google Play. We may restrict access for material violations of these terms, fraud, security abuse, unlawful use, or when required by law.</p>

      <h2>12. Contact and changes to these terms</h2>
      <p>Questions about these terms should be sent through the official support destination published with AstraWave. These terms may be updated as the service changes; the effective date above will identify the current version.</p>
    </main>
  );
}
