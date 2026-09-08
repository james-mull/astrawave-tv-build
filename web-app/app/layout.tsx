import type { Metadata, Viewport } from 'next';
import './globals.css';
import './premium-overrides.css';
import './app-polish.css';
import './control-polish.css';
import './mobile-primary-nav.css';
import MobilePrimaryNav from './MobilePrimaryNav';

export const metadata: Metadata = {
  title: 'AstraWave — All Your Entertainment. One Place.',
  description: 'AstraWave brings movies, TV, live channels, sports, music, podcasts, radio and your own media sources into one polished experience.',
  keywords: ['AstraWave','live TV','movies','TV shows','sports','music','podcasts','radio','M3U','Xtream','media hub'],
  manifest: '/manifest.webmanifest',
  appleWebApp: {
    capable: true,
    title: 'AstraWave',
    statusBarStyle: 'black-translucent',
  },
  openGraph: {
    title: 'AstraWave — All Your Entertainment. One Place.',
    description: 'Movies, TV, live channels, sports, music, podcasts and radio in one clean experience.',
    type: 'website'
  }
};

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  viewportFit: 'cover',
  themeColor: '#06080d',
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>
        {children}
        <MobilePrimaryNav />
      </body>
    </html>
  );
}
