export type MdbListCatalogMapping={owner:string;slug:string};

/**
 * Verified public MDBList mappings for AstraWave built-in catalogs.
 * Keep this list conservative: only add owner/slug pairs confirmed from a live MDBList page and
 * whose actual contents match the AstraWave catalog intent. Unmapped catalogs use metadata fallback.
 */
export const mdblistCatalogMap:Record<string,MdbListCatalogMapping>={
  // Official MDBList collections.
  'movie:popular-movies':{owner:'official',slug:'movies/popular'},
  'movie:most-watched-movies':{owner:'official',slug:'movies/most-watched'},
  'movie:most-anticipated-movies':{owner:'official',slug:'movies/anticipated'},
  'show:popular-shows':{owner:'official',slug:'shows/popular'},
  'show:most-watched-shows':{owner:'official',slug:'shows/most-watched'},
  'show:most-anticipated-shows':{owner:'official',slug:'shows/anticipated'},

  // Frequently refreshed public discovery lists.
  'movie:trending-movies':{owner:'ndg3270',slug:'trending-movies'},
  'show:trending-shows':{owner:'ndg3270',slug:'trending-shows'},
  'movie:top-rated-movies':{owner:'ndg3270',slug:'top-rated-movies'},
  'show:top-rated-shows':{owner:'ndg3270',slug:'top-rated-tv-shows'},
  'movie:imdb-top-movies':{owner:'peri0dic1',slug:'imdb-top-rated-movies'},
  'movie:new-releases':{owner:'ndg3270',slug:'latest-movies'},
  'show:new-series':{owner:'ndg3270',slug:'latest-series'},
  'movie:critics-favorites':{owner:'ndg3270',slug:'rotten-tomatoes-fresh-movies'},
  'show:critics-favorites-tv':{owner:'ndg3270',slug:'certified-fresh-tv-shows'},
  'show:limited-series':{owner:'ndg3270',slug:'tv-mini-series'},

  // High-traffic genre collections with verified dynamic MDBList pages.
  'movie:action-essentials':{owner:'ndg3270',slug:'action-movies'},
  'movie:comedy-hits':{owner:'ndg3270',slug:'comedy-movies'},
  'movie:drama-essentials':{owner:'ndg3270',slug:'drama'},
  'movie:horror-hits':{owner:'ndg3270',slug:'horror'},
  'movie:sci-fi-essentials':{owner:'ndg3270',slug:'science-fiction'},
  'movie:crime-movies':{owner:'ndg3270',slug:'crime-movies'},
  'movie:sports-movies':{owner:'hdlists',slug:'150-best-sports-movies-of-all-time'},
  'show:true-crime':{owner:'an-kah',slug:'best-true-crime-shows'},
  'movie:anime-movies':{owner:'apg2886',slug:'top-anime-movies'},
  'show:anime-series':{owner:'aaron713',slug:'anime-shows'},

  // International discovery collections.
  'show:k-dramas':{owner:'egmi1',slug:'all-kdramas'},
  'show:indian-series':{owner:'apollocat',slug:'indian-tv-shows-hindi-english'},

  // Curated premium/editorial collections.
  'movie:psychological-thrillers':{owner:'ndg3270',slug:'psychological-thrillers'},
  'movie:comfort-movies':{owner:'billryan',slug:'comfort-movies'},
  'movie:dolby-vision-picks':{owner:'ndg3270',slug:'dolby-vision-releases'},
  'movie:4k-hdr-showcase':{owner:'littlerooster',slug:'the-complete-dolby-vision-4k-remux-list'},
  'movie:audience-favorites':{owner:'billryan',slug:'letterboxds-top-500-films'},
  'show:prestige-drama':{owner:'billryan',slug:'metacritic-must-see-tv'},
};

export function mdblistPathForCatalog(id:string):string|undefined{
  const mapping=mdblistCatalogMap[id];
  return mapping?`${mapping.owner}/${mapping.slug}`:undefined;
}
