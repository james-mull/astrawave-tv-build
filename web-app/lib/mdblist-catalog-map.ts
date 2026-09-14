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
  'movie:adventure-movies':{owner:'bbrodka1963',slug:'top-100-adventure-movies-english'},
  'movie:animation-favorites':{owner:'hdlists',slug:'latest-hd-animated-movies-from-1980-to-today'},
  'movie:comedy-hits':{owner:'ndg3270',slug:'comedy-movies'},
  'movie:crime-movies':{owner:'ndg3270',slug:'crime-movies'},
  'movie:documentaries':{owner:'hdlists',slug:'latest-hd-documentary-movies-1980-to-today'},
  'movie:drama-essentials':{owner:'ndg3270',slug:'drama'},
  'movie:family-night':{owner:'hdlists',slug:'latest-hd-family-movies-top-rated-from-1980-to-today'},
  'movie:fantasy-worlds':{owner:'hdlists',slug:'best-fantasy-movies-of-all-time'},
  'movie:horror-hits':{owner:'ndg3270',slug:'horror'},
  'movie:mystery-movies':{owner:'hdlists',slug:'latest-hd-mystery-movies-top-rated-from-1980-to-today'},
  'movie:sci-fi-essentials':{owner:'ndg3270',slug:'science-fiction'},
  'movie:thriller-picks':{owner:'dmasej',slug:'popular-thriller-movies'},
  'movie:superhero-movies':{owner:'hdlists',slug:'latest-hd-superhero-movies-from-1980-to-today'},
  'movie:sports-movies':{owner:'hdlists',slug:'150-best-sports-movies-of-all-time'},
  'movie:christmas-movies':{owner:'hdlists',slug:'christmas-movies'},
  'movie:halloween-movies':{owner:'hdlists',slug:'halloween-movies-the-best-of-all-time'},
  'show:comedy-series':{owner:'MaxtronTV',slug:'comedy-tv-shows'},
  'show:crime-series':{owner:'MaxtronTV',slug:'crime-tv-shows'},
  'show:documentary-series':{owner:'trackertrack',slug:'documentary-tv-shows'},
  'show:drama-series':{owner:'MaxtronTV',slug:'drama-tv-shows'},
  'show:family-series':{owner:'MaxtronTV',slug:'family-tv-shows'},
  'show:fantasy-series':{owner:'MaxtronTV',slug:'fantasy-tv-shows'},
  'show:horror-series':{owner:'MaxtronTV',slug:'horror-tv-shows'},
  'show:mystery-series':{owner:'MaxtronTV',slug:'mystery-tv-shows'},
  'show:sci-fi-series':{owner:'digvijay-sai',slug:'sci-fi-tv-shows'},
  'show:superhero-series':{owner:'MaxtronTV',slug:'superhero-tv-shows'},
  'show:thriller-series':{owner:'MaxtronTV',slug:'thriller-tv-shows'},
  'show:western-series':{owner:'MaxtronTV',slug:'western-tv-shows'},
  'show:reality-favorites':{owner:'MaxtronTV',slug:'reality-tv-shows'},
  'show:sports-series':{owner:'MaxtronTV',slug:'sports-tv-shows'},
  'show:true-crime':{owner:'an-kah',slug:'best-true-crime-shows'},
  'show:kids-tv':{owner:'baruchin',slug:'kids-tv-shows'},
  'movie:anime-movies':{owner:'apg2886',slug:'top-anime-movies'},
  'show:anime-series':{owner:'aaron713',slug:'anime-shows'},

  // International discovery collections.
  'movie:international-cinema':{owner:'xsampsonxxsampsonx',slug:'critically-acclaimed-foreign-films'},
  'movie:japanese-movies':{owner:'coreyh047',slug:'popular-japanese-movies'},
  'movie:french-cinema':{owner:'coreyh047',slug:'popular-french-movies'},
  'show:british-tv':{owner:'amything',slug:'latest-uk-shows'},
  'show:k-dramas':{owner:'egmi1',slug:'all-kdramas'},
  'show:indian-series':{owner:'apollocat',slug:'indian-tv-shows-hindi-english'},

  // Streaming-service substitutions. Stable legacy IDs preserve saved user layout/preferences.
  'movie:girls-night':{owner:'snoak',slug:'latest-netflix-movies'},
  'movie:guys-night':{owner:'snoak',slug:'latest-amazon-prime-movies'},
  'movie:date-night':{owner:'snoak',slug:'latest-disney-plus-movies'},
  'movie:feel-good-movies':{owner:'snoak',slug:'latest-max-movies'},
  'movie:summer-blockbusters':{owner:'snoak',slug:'latest-apple-tv-plus-movies'},
  'movie:music-movies':{owner:'snoak',slug:'latest-hulu-movies'},
  'movie:disaster-movies':{owner:'petera209',slug:'peacock-movies'},
  'movie:survival-stories':{owner:'snoak',slug:'latest-paramount-plus-movies'},
  'show:martial-arts-series':{owner:'snoak',slug:'latest-netflix-tv-shows'},
  'show:heist-series':{owner:'snoak',slug:'latest-amazon-prime-tv-shows'},
  'show:spy-series':{owner:'snoak',slug:'latest-disney-plus-tv-shows'},
  'show:survival-series':{owner:'snoak',slug:'latest-max-tv-shows'},
  'show:post-apocalyptic-series':{owner:'snoak',slug:'latest-apple-tv-plus-tv-shows'},
  'show:time-travel-series':{owner:'snoak',slug:'latest-hulu-series'},
  'show:psychological-series':{owner:'brovik',slug:'peacock-shows'},
  'show:biographical-series':{owner:'snoak',slug:'latest-paramount-plus-tv-shows'},

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
