import type { MetadataRoute } from 'next';

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: 'AstraWave',
    short_name: 'AstraWave',
    description: 'Movies, TV, live channels, sports, music, podcasts, radio and personal media in one entertainment hub.',
    start_url: '/app',
    scope: '/',
    display: 'standalone',
    background_color: '#06080d',
    theme_color: '#9b7cff',
    orientation: 'any',
    categories: ['entertainment', 'music', 'video'],
  };
}
