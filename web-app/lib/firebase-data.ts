import { doc, getDoc, setDoc, deleteDoc, collection, getDocs, serverTimestamp } from 'firebase/firestore';
import { firestore } from './firebase';

export type CloudProfile = { displayName?: string; avatarUrl?: string; kidsMode?: boolean };
export type CloudProgress = { mediaId: string; kind: string; title: string; positionMs: number; durationMs: number; updatedAt?: unknown };
export type CloudAddon = {
  id: string;
  name: string;
  kind: 'stremio' | 'cloudstream' | 'provider-catalog';
  url?: string | null;
  enabled: boolean;
  reviewed?: boolean;
  custom?: boolean;
};
export type CloudSource = {
  id: string;
  name: string;
  type: 'M3U' | 'XTREAM' | 'XMLTV' | 'PUBLIC';
  enabled: boolean;
  priority?: number;
  config?: Record<string, unknown>;
};
export type CloudAppConfig = {
  version: number;
  activeLiveSource?: string;
  theme?: 'dark' | 'system';
  homeDensity?: 'comfortable' | 'compact';
  autoplayTrailers?: boolean;
  aiDiscovery?: boolean;
  preferredLanguage?: string;
  preferredRegion?: string;
  addons?: CloudAddon[];
  updatedAt?: unknown;
};

function requireDb() {
  if (!firestore) throw new Error('Firebase is not configured');
  return firestore;
}

export const FirebaseData = {
  async getProfile(uid: string): Promise<CloudProfile | null> {
    const snap = await getDoc(doc(requireDb(), 'profiles', uid));
    return snap.exists() ? (snap.data() as CloudProfile) : null;
  },
  async saveProfile(uid: string, profile: CloudProfile) {
    await setDoc(doc(requireDb(), 'profiles', uid), { ...profile, updatedAt: serverTimestamp() }, { merge: true });
  },
  async addWatchlist(uid: string, mediaId: string, payload: Record<string, unknown>) {
    await setDoc(doc(requireDb(), 'users', uid, 'watchlist', mediaId), { ...payload, mediaId, updatedAt: serverTimestamp() }, { merge: true });
  },
  async removeWatchlist(uid: string, mediaId: string) {
    await deleteDoc(doc(requireDb(), 'users', uid, 'watchlist', mediaId));
  },
  async saveProgress(uid: string, progress: CloudProgress) {
    await setDoc(doc(requireDb(), 'users', uid, 'progress', progress.mediaId), { ...progress, updatedAt: serverTimestamp() }, { merge: true });
  },
  async listProgress(uid: string): Promise<CloudProgress[]> {
    const snaps = await getDocs(collection(requireDb(), 'users', uid, 'progress'));
    return snaps.docs.map((x) => x.data() as CloudProgress);
  },
  async saveFavoriteTeam(uid: string, teamId: string, payload: Record<string, unknown>) {
    await setDoc(doc(requireDb(), 'users', uid, 'favoriteTeams', teamId), { ...payload, teamId, updatedAt: serverTimestamp() }, { merge: true });
  },
  async getAppConfig(uid: string): Promise<CloudAppConfig | null> {
    const snap = await getDoc(doc(requireDb(), 'users', uid, 'settings', 'app'));
    return snap.exists() ? (snap.data() as CloudAppConfig) : null;
  },
  async saveAppConfig(uid: string, config: CloudAppConfig) {
    await setDoc(doc(requireDb(), 'users', uid, 'settings', 'app'), { ...config, updatedAt: serverTimestamp() }, { merge: true });
  },
  async listSources(uid: string): Promise<CloudSource[]> {
    const snaps = await getDocs(collection(requireDb(), 'users', uid, 'sources'));
    return snaps.docs.map((x) => ({ id: x.id, ...(x.data() as Omit<CloudSource, 'id'>) }));
  },
  async saveSource(uid: string, source: CloudSource) {
    const { id, ...payload } = source;
    await setDoc(doc(requireDb(), 'users', uid, 'sources', id), { ...payload, updatedAt: serverTimestamp() }, { merge: true });
  },
  async deleteSource(uid: string, sourceId: string) {
    await deleteDoc(doc(requireDb(), 'users', uid, 'sources', sourceId));
  },
  async getEntitlement(uid: string) {
    const snap = await getDoc(doc(requireDb(), 'entitlements', uid));
    return snap.exists() ? snap.data() : { plan: 'free' };
  },
};