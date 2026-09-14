import type { BuiltInCatalogDefinition } from './builtin-catalogs';

/** Stable-ID service substitutions; keeps saved hidden/pinned/order preferences intact. */
const serviceTitles:Record<string,string>={
  'movie:girls-night':'Netflix Movies',
  'movie:guys-night':'Prime Video Movies',
  'movie:date-night':'Disney+ Movies',
  'movie:feel-good-movies':'Max Movies',
  'movie:summer-blockbusters':'Apple TV+ Movies',
  'movie:music-movies':'Hulu Movies',
  'movie:disaster-movies':'Peacock Movies',
  'movie:survival-stories':'Paramount+ Movies',
  'show:martial-arts-series':'Netflix Shows',
  'show:heist-series':'Prime Video Shows',
  'show:spy-series':'Disney+ Shows',
  'show:survival-series':'Max Shows',
  'show:post-apocalyptic-series':'Apple TV+ Shows',
  'show:time-travel-series':'Hulu Shows',
  'show:psychological-series':'Peacock Shows',
  'show:biographical-series':'Paramount+ Shows',
};

export const serviceCatalogIds=new Set(Object.keys(serviceTitles));

export function applyServiceCatalogOverride(definition:BuiltInCatalogDefinition):BuiltInCatalogDefinition & {service?:boolean}{
  const title=serviceTitles[definition.id];
  return title?{...definition,title,category:'Premium & World',service:true}:definition;
}
