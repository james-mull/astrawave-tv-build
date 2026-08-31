import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'AstraWave — All Your Entertainment. One Place.',
  description: 'AstraWave brings movies, TV, live channels, sports, music, podcasts and your own media sources into one polished experience.',
  keywords: ['AstraWave','live TV','movies','TV shows','sports','music','podcasts','M3U','Xtream','media hub'],
  openGraph: {
    title: 'AstraWave — All Your Entertainment. One Place.',
    description: 'Movies, TV, live channels, sports, music and podcasts in one clean experience.',
    type: 'website'
  }
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
