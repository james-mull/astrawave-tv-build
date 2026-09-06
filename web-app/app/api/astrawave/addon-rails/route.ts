import { NextResponse } from 'next/server';

const reviewedManifests = [
  'https://v3-cinemeta.strem.io/manifest.json',
  'https://v3-channels.strem.io/manifest.json',
  'https://watchhub.strem.io/manifest.json',
  'https://caching.stremio.net/publicdomainmovies.now.sh/manifest.json',
  'https://opensubtitles-v3.strem.io/manifest.json',
];

function baseUrl(manifestUrl: string) {
  return manifestUrl.replace(/\/manifest\.json$/i, '').replace(/\/$/, '');
}

function mapMeta(meta: any, type: string) {
  const normalizedType = String(meta.type || type || '').toLowerCase();
  const kind = normalizedType === 'series' ? 'series' : normalizedType === 'movie' ? 'movie' : 'live';
  return {
    id: String(meta.id || meta.imdb_id || meta.name || Math.random()),
    kind,
    title: meta.name || meta.title || 'Untitled',
    subtitle: meta.releaseInfo || meta.year || meta.description || undefined,
    posterUrl: meta.poster || undefined,
    backdropUrl: meta.background || meta.logo || undefined,
    overview: meta.description || undefined,
    addonMeta: true,
  };
}

async function loadAddon(manifestUrl: string) {
  const manifestResponse = await fetch(manifestUrl, { next: { revalidate: 1800 } });
  if (!manifestResponse.ok) throw new Error(`Manifest ${manifestResponse.status}`);
  const manifest = await manifestResponse.json();
  const base = baseUrl(manifestUrl);
  const catalogs = (manifest.catalogs || []).filter((catalog: any) => {
    const type = String(catalog.type || '').toLowerCase();
    if (!['movie', 'series', 'tv', 'channel'].includes(type)) return false;
    const extras = Array.isArray(catalog.extra) ? catalog.extra : [];
    return !extras.some((extra: any) => typeof extra === 'object' && extra?.isRequired === true);
  }).slice(0, 10);

  const rows = await Promise.allSettled(catalogs.map(async (catalog: any) => {
    const url = `${base}/catalog/${encodeURIComponent(catalog.type)}/${encodeURIComponent(catalog.id)}.json`;
    const response = await fetch(url, { next: { revalidate: 900 } });
    if (!response.ok) throw new Error(`Catalog ${response.status}`);
    const body = await response.json();
    const items = (body.metas || []).slice(0, 30).map((meta: any) => mapMeta(meta, catalog.type));
    return {
      title: `${manifest.name || 'Addon'} • ${catalog.name || catalog.id}`,
      source: manifest.name || 'Stremio addon',
      addonId: manifest.id,
      manifestUrl,
      items,
    };
  }));

  return {
    id: manifest.id,
    name: manifest.name,
    version: manifest.version,
    manifestUrl,
    resources: manifest.resources || [],
    rows: rows.flatMap((row) => row.status === 'fulfilled' && row.value.items.length ? [row.value] : []),
  };
}

export async function GET() {
  const settled = await Promise.allSettled(reviewedManifests.map(loadAddon));
  const addons = settled.flatMap((result) => result.status === 'fulfilled' ? [result.value] : []);
  return NextResponse.json({
    reviewedOnly: true,
    manifests: reviewedManifests,
    addons,
    rails: addons.flatMap((addon) => addon.rows),
  });
}
