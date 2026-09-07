'use client';

import { useEffect, useRef, useState } from 'react';
import { onAuthStateChanged, User } from 'firebase/auth';
import { CheckCircle2, ExternalLink, LoaderCircle, RefreshCw, Tv2 } from 'lucide-react';
import { firebaseAuth } from '../../../../lib/firebase';

type DeviceCodeResponse = {
  connected?: boolean;
  device_code?: string;
  user_code?: string;
  verification_url?: string;
  expires_in?: number;
  interval?: number;
  error?: string;
};

type PollResponse = { connected?: boolean; pending?: boolean; provider?: string; error?: string };

export default function TraktControlPage() {
  const [user, setUser] = useState<User | null>(null);
  const [device, setDevice] = useState<DeviceCodeResponse | null>(null);
  const [status, setStatus] = useState('Sign in with AstraWave to connect Trakt.');
  const [busy, setBusy] = useState(false);
  const [connected, setConnected] = useState(false);
  const pollTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const expiresAt = useRef(0);

  useEffect(() => {
    if (!firebaseAuth) return;
    return onAuthStateChanged(firebaseAuth, next => {
      setUser(next);
      setStatus(next ? 'Ready to connect your Trakt account.' : 'Sign in with AstraWave to connect Trakt.');
    });
  }, []);

  useEffect(() => () => { if (pollTimer.current) clearTimeout(pollTimer.current); }, []);

  async function authHeader() {
    if (!user) throw new Error('AstraWave sign-in is required.');
    return { authorization: `Bearer ${await user.getIdToken()}` };
  }

  async function startConnection() {
    if (!user || busy) return;
    setBusy(true); setConnected(false); setDevice(null); setStatus('Requesting a Trakt device code…');
    try {
      const response = await fetch('/api/astrawave/trakt/device/start', { method: 'POST', headers: await authHeader() });
      const data = await response.json() as DeviceCodeResponse;
      if (!response.ok || !data.device_code || !data.user_code) throw new Error(data.error || 'Unable to start Trakt connection');
      setDevice(data);
      expiresAt.current = Date.now() + Math.max(60, data.expires_in || 600) * 1000;
      setStatus('Open Trakt, enter the code below, and AstraWave will finish automatically.');
      schedulePoll(data.device_code, Math.max(5, data.interval || 5));
    } catch (error) {
      setStatus(error instanceof Error ? error.message : 'Unable to start Trakt connection');
    } finally { setBusy(false); }
  }

  function schedulePoll(deviceCode: string, intervalSeconds: number) {
    if (pollTimer.current) clearTimeout(pollTimer.current);
    pollTimer.current = setTimeout(() => poll(deviceCode, intervalSeconds), intervalSeconds * 1000);
  }

  async function poll(deviceCode: string, intervalSeconds: number) {
    if (!user || connected) return;
    if (Date.now() >= expiresAt.current) { setStatus('The Trakt code expired. Start a new connection.'); setDevice(null); return; }
    try {
      const response = await fetch('/api/astrawave/trakt/device/poll', {
        method: 'POST', headers: { ...(await authHeader()), 'content-type': 'application/json' }, body: JSON.stringify({ device_code: deviceCode }),
      });
      const data = await response.json() as PollResponse;
      if (response.ok && data.connected) {
        setConnected(true); setStatus('Trakt connected. AstraWave can now use this account for synced lists and watch activity.'); setDevice(null); return;
      }
      if (response.status === 202 || data.pending) { schedulePoll(deviceCode, intervalSeconds); return; }
      throw new Error(data.error || 'Trakt connection failed');
    } catch (error) {
      setStatus(error instanceof Error ? error.message : 'Trakt connection failed');
    }
  }

  const verifyUrl = device?.verification_url || 'https://trakt.tv/activate';

  return <main className="trakt-shell">
    <section className="trakt-hero">
      <div className="icon"><Tv2 size={28}/></div>
      <div><small>ASTRAWAVE + TRAKT</small><h1>Sync what you watch.</h1><p>Connect one Trakt account to power cross-device watch history, lists and future scrobbling across AstraWave TV, Android and web.</p></div>
    </section>

    <section className="card">
      <div className="status">{connected ? <CheckCircle2/> : busy ? <LoaderCircle className="spin"/> : <RefreshCw/>}<span>{status}</span></div>
      {!user && <p className="muted">Return to Sources & Settings and sign in with your AstraWave account first.</p>}
      {user && !device && !connected && <button className="primary" onClick={startConnection} disabled={busy}>{busy ? 'Starting…' : 'Connect Trakt'}</button>}
      {device && <div className="device-code">
        <small>ENTER THIS CODE ON TRAKT</small><strong>{device.user_code}</strong>
        <a href={verifyUrl} target="_blank" rel="noreferrer">Open Trakt activation <ExternalLink size={15}/></a>
        <p>AstraWave is checking automatically. You can leave this page open while you approve the device.</p>
      </div>}
      {connected && <div className="connected"><CheckCircle2/><div><b>Connected securely</b><span>Tokens are encrypted server-side and are never written into the browser or Android APK.</span></div></div>}
    </section>

    <section className="grid">
      <article><b>Watch history</b><span>Import watched movies and episodes into your AstraWave profile.</span></article>
      <article><b>Lists & watchlist</b><span>Use your Trakt lists as discovery rails across TV, phone and web.</span></article>
      <article><b>Scrobbling</b><span>Next phase: keep playback progress and completion synchronized automatically.</span></article>
    </section>

    <style jsx>{`.trakt-shell{max-width:900px;margin:auto;padding:46px 22px 100px;color:#f7f8fb}.trakt-hero{display:grid;grid-template-columns:auto 1fr;gap:18px;align-items:start}.icon{width:52px;height:52px;border-radius:16px;background:#171b25;display:grid;place-items:center;color:#b9aeff}.trakt-hero small{color:#8b7cff;letter-spacing:.14em}.trakt-hero h1{font-size:clamp(38px,6vw,64px);letter-spacing:-.05em;margin:8px 0}.trakt-hero p,.muted,.device-code p{color:#9ca3b2;line-height:1.65}.card{margin-top:28px;background:#10131b;border:1px solid #252b38;border-radius:22px;padding:24px}.status{display:flex;gap:10px;align-items:center;font-weight:750}.primary{margin-top:20px;border:0;border-radius:12px;background:#f2eeff;color:#090b11;padding:13px 18px;font-weight:850}.device-code{margin-top:24px;text-align:center;padding:24px;border-radius:18px;background:#090b11}.device-code small{display:block;color:#8b7cff;letter-spacing:.12em}.device-code strong{display:block;font-size:52px;letter-spacing:.12em;margin:12px}.device-code a{display:inline-flex;align-items:center;gap:7px;color:#b9aeff;text-decoration:none}.connected{display:flex;gap:12px;align-items:flex-start;margin-top:22px;padding:16px;background:#0d1a13;border-radius:14px;color:#72dc96}.connected b,.connected span{display:block}.connected span{color:#9db9a7;font-size:13px;margin-top:4px}.grid{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin-top:14px}.grid article{background:#10131b;border:1px solid #252b38;border-radius:16px;padding:17px}.grid b,.grid span{display:block}.grid span{font-size:13px;color:#9ca3b2;margin-top:7px;line-height:1.5}.spin{animation:spin 1s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}@media(max-width:700px){.grid{grid-template-columns:1fr}.trakt-hero{grid-template-columns:1fr}.device-code strong{font-size:40px}}`}</style>
  </main>;
}
