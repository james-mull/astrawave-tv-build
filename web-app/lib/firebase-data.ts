import { doc, getDoc, setDoc, deleteDoc, collection, getDocs, serverTimestamp } from 'firebase/firestore';
import { firestore } from './firebase';

export type CloudProfile = { displayName?: string; avatarUrl?: string; kidsMode?: boolean };
export type CloudProgress = { mediaId: string; kind: string; title: string; positionMs: number; durationMs: number; updatedAt?: unknown };
export type CloudAddon = { id:string; name:string; kind:'stremio'|'cloudstream'|'provider-catalog'; url?:string|null; enabled:boolean; reviewed?:boolean; custom?:boolean };
export type CloudSource = { id:string; name:string; type:'M3U'|'XTREAM'|'XMLTV'|'PUBLIC'|'STALKER'|'JELLYFIN'|'PLEX'|'HDHOMERUN'|'TVHEADEND'|'ENIGMA2'; enabled:boolean; priority?:number; config?:Record<string,unknown> };
export type CloudAppConfig = { version:number; activeLiveSource?:string; theme?:'dark'|'system'; homeDensity?:'comfortable'|'compact'; autoplayTrailers?:boolean; aiDiscovery?:boolean; preferredLanguage?:string; preferredRegion?:string; addons?:CloudAddon[]; updatedAt?:unknown };
export type CloudChannelCustomization = {
  id:string; profileId:string; channelId:string; customName?:string|null; customNumber?:number|null;
  customGroup?:string|null; hidden:boolean; sortOrder?:number; epgIdOverride?:string|null;
  logoUrlOverride?:string|null; updatedAt?:unknown;
};
export type CloudPlaybackDiagnostic = {
  id:string; title:string; mediaType:string; provider:string; quality?:string|null; latencyMs?:number|null;
  providerCount:number; backupCount:number; personalMedia:boolean; debridOptimized:boolean;
  resolvedAtEpochMs:number; updatedAt?:unknown;
};

export type CloudDevice = {
  id:string; name:string; type:'web'|'phone'|'tablet'|'android-tv'|'fire-tv'|'cast';
  online:boolean; appVersion?:string; currentTitle?:string; currentKind?:string; positionMs?:number;
  playbackState?:'idle'|'playing'|'paused'|'buffering'; sourceName?:string; storageFreeMb?:number; lastSeenAt?:unknown;
};
export type CloudRemoteCommand = {
  id:string; deviceId:string; command:'PLAY'|'PAUSE'|'SEEK_FORWARD'|'SEEK_BACK'|'CHANNEL_UP'|'CHANNEL_DOWN'|'VOLUME_UP'|'VOLUME_DOWN'|'MUTE'|'BACK'|'HOME'|'OPEN_GUIDE'|'OPEN_SPORTS';
  value?:number; status?:'queued'|'delivered'|'handled'|'failed'; createdAt?:unknown;
};
export type CloudRecording = { id:string; title:string; channelId:string; sourceId:string; startEpochMs:number; endEpochMs:number; seriesId?:string|null; state:'scheduled'|'recording'|'complete'|'failed'|'canceled'; playbackUrl?:string|null };
export type CloudDownload = { id:string; mediaId:string; kind:string; title:string; state:'queued'|'downloading'|'paused'|'complete'|'failed'|'canceled'; progressPercent:number; deviceId?:string|null; posterUrl?:string|null };
export type CloudHouseholdCandidate = { mediaId:string; mediaType:string; title:string; posterUrl?:string|null };
export type CloudHouseholdVote = { profileId:string; mediaId:string; value:'LOVE'|'LIKE'|'MAYBE'|'PASS' };
export type CloudHouseholdSession = { id:string; name:string; profileIds:string[]; candidates:CloudHouseholdCandidate[]; votes:CloudHouseholdVote[]; status:'open'|'closed' };
export type CloudSportsReminder = { id:string; eventId:string; title:string; startTime:string; teamIds?:string[]; enabled:boolean };

function requireDb(){if(!firestore)throw new Error('Firebase is not configured');return firestore}
const userCollection=(uid:string,name:string)=>collection(requireDb(),'users',uid,name);
const profileCollection=(uid:string,profileId:string,name:string)=>collection(requireDb(),'users',uid,'profiles',profileId,name);

export const FirebaseData={
  async getProfile(uid:string):Promise<CloudProfile|null>{const snap=await getDoc(doc(requireDb(),'profiles',uid));return snap.exists()?(snap.data() as CloudProfile):null},
  async saveProfile(uid:string,profile:CloudProfile){await setDoc(doc(requireDb(),'profiles',uid),{...profile,updatedAt:serverTimestamp()},{merge:true})},
  async addWatchlist(uid:string,mediaId:string,payload:Record<string,unknown>){await setDoc(doc(requireDb(),'users',uid,'watchlist',mediaId),{...payload,mediaId,updatedAt:serverTimestamp()},{merge:true})},
  async removeWatchlist(uid:string,mediaId:string){await deleteDoc(doc(requireDb(),'users',uid,'watchlist',mediaId))},
  async saveProgress(uid:string,progress:CloudProgress){await setDoc(doc(requireDb(),'users',uid,'progress',progress.mediaId),{...progress,updatedAt:serverTimestamp()},{merge:true})},
  async listProgress(uid:string):Promise<CloudProgress[]>{const snaps=await getDocs(userCollection(uid,'progress'));return snaps.docs.map(x=>x.data() as CloudProgress)},
  async listPlaybackDiagnostics(uid:string,profileId:string):Promise<CloudPlaybackDiagnostic[]>{const snaps=await getDocs(profileCollection(uid,profileId,'playbackDiagnostics'));return snaps.docs.map(x=>({id:x.id,...(x.data() as Omit<CloudPlaybackDiagnostic,'id'>)})).sort((a,b)=>b.resolvedAtEpochMs-a.resolvedAtEpochMs)},
  async saveFavoriteTeam(uid:string,teamId:string,payload:Record<string,unknown>){await setDoc(doc(requireDb(),'users',uid,'favoriteTeams',teamId),{...payload,teamId,updatedAt:serverTimestamp()},{merge:true})},
  async getAppConfig(uid:string):Promise<CloudAppConfig|null>{const snap=await getDoc(doc(requireDb(),'users',uid,'settings','app'));return snap.exists()?(snap.data() as CloudAppConfig):null},
  async saveAppConfig(uid:string,config:CloudAppConfig){await setDoc(doc(requireDb(),'users',uid,'settings','app'),{...config,updatedAt:serverTimestamp()},{merge:true})},
  async listSources(uid:string):Promise<CloudSource[]>{const snaps=await getDocs(userCollection(uid,'sources'));return snaps.docs.map(x=>({id:x.id,...(x.data() as Omit<CloudSource,'id'>)}))},
  async saveSource(uid:string,source:CloudSource){const{id,...payload}=source;await setDoc(doc(requireDb(),'users',uid,'sources',id),{...payload,updatedAt:serverTimestamp()},{merge:true})},
  async deleteSource(uid:string,sourceId:string){await deleteDoc(doc(requireDb(),'users',uid,'sources',sourceId))},

  async listChannelCustomizations(uid:string):Promise<CloudChannelCustomization[]>{const snaps=await getDocs(userCollection(uid,'channelCustomizations'));return snaps.docs.map(x=>({id:x.id,...(x.data() as Omit<CloudChannelCustomization,'id'>)}))},
  async saveChannelCustomization(uid:string,item:CloudChannelCustomization){const{id,...payload}=item;await setDoc(doc(requireDb(),'users',uid,'channelCustomizations',id),{...payload,updatedAt:serverTimestamp()},{merge:true})},
  async deleteChannelCustomization(uid:string,id:string){await deleteDoc(doc(requireDb(),'users',uid,'channelCustomizations',id))},

  async listDevices(uid:string):Promise<CloudDevice[]>{const snaps=await getDocs(userCollection(uid,'devices'));return snaps.docs.map(x=>({id:x.id,...(x.data() as Omit<CloudDevice,'id'>)}))},
  async saveDevice(uid:string,device:CloudDevice){const{id,...payload}=device;await setDoc(doc(requireDb(),'users',uid,'devices',id),{...payload,lastSeenAt:serverTimestamp()},{merge:true})},
  async sendRemoteCommand(uid:string,command:CloudRemoteCommand){const{id,...payload}=command;await setDoc(doc(requireDb(),'users',uid,'commands',id),{...payload,status:'queued',createdAt:serverTimestamp()})},
  async listCommands(uid:string):Promise<CloudRemoteCommand[]>{const snaps=await getDocs(userCollection(uid,'commands'));return snaps.docs.map(x=>({id:x.id,...(x.data() as Omit<CloudRemoteCommand,'id'>)}))},

  async listRecordings(uid:string):Promise<CloudRecording[]>{const snaps=await getDocs(userCollection(uid,'recordings'));return snaps.docs.map(x=>({id:x.id,...(x.data() as Omit<CloudRecording,'id'>)}))},
  async saveRecording(uid:string,item:CloudRecording){const{id,...payload}=item;await setDoc(doc(requireDb(),'users',uid,'recordings',id),{...payload,updatedAt:serverTimestamp()},{merge:true})},
  async deleteRecording(uid:string,id:string){await deleteDoc(doc(requireDb(),'users',uid,'recordings',id))},

  async listDownloads(uid:string):Promise<CloudDownload[]>{const snaps=await getDocs(userCollection(uid,'downloads'));return snaps.docs.map(x=>({id:x.id,...(x.data() as Omit<CloudDownload,'id'>)}))},
  async saveDownload(uid:string,item:CloudDownload){const{id,...payload}=item;await setDoc(doc(requireDb(),'users',uid,'downloads',id),{...payload,updatedAt:serverTimestamp()},{merge:true})},
  async deleteDownload(uid:string,id:string){await deleteDoc(doc(requireDb(),'users',uid,'downloads',id))},

  async listHouseholdSessions(uid:string):Promise<CloudHouseholdSession[]>{const snaps=await getDocs(userCollection(uid,'household'));return snaps.docs.map(x=>({id:x.id,...(x.data() as Omit<CloudHouseholdSession,'id'>)}))},
  async saveHouseholdSession(uid:string,item:CloudHouseholdSession){const{id,...payload}=item;await setDoc(doc(requireDb(),'users',uid,'household',id),{...payload,updatedAt:serverTimestamp()},{merge:true})},
  async deleteHouseholdSession(uid:string,id:string){await deleteDoc(doc(requireDb(),'users',uid,'household',id))},

  async listSportsReminders(uid:string):Promise<CloudSportsReminder[]>{const snaps=await getDocs(userCollection(uid,'sportsReminders'));return snaps.docs.map(x=>({id:x.id,...(x.data() as Omit<CloudSportsReminder,'id'>)}))},
  async saveSportsReminder(uid:string,item:CloudSportsReminder){const{id,...payload}=item;await setDoc(doc(requireDb(),'users',uid,'sportsReminders',id),{...payload,updatedAt:serverTimestamp()},{merge:true})},

  async getEntitlement(uid:string){const snap=await getDoc(doc(requireDb(),'entitlements',uid));return snap.exists()?snap.data():{plan:'free'}},
};