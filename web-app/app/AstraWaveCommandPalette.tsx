'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import { Command, Film, Home, RadioTower, Search, Settings, Trophy, Tv2, X } from 'lucide-react';

type CommandItem = {
  label: string;
  keywords: string;
  shortcut?: string;
  icon: React.ComponentType<{ size?: number }>;
  run: () => void;
};

function clickShellDestination(target: string) {
  const buttons = Array.from(document.querySelectorAll<HTMLButtonElement>('.side nav button'));
  const button = buttons.find((item) => item.textContent?.trim() === target);
  button?.click();
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function focusSearch() {
  const input = document.querySelector<HTMLInputElement>('.top .search input');
  input?.focus();
  input?.scrollIntoView({ block: 'center', behavior: 'smooth' });
}

function toggleVideoPlayback() {
  const video = document.querySelector<HTMLVideoElement>('.playerPanel video');
  if (!video) return;
  if (video.paused) video.play().catch(() => {});
  else video.pause();
}

export default function AstraWaveCommandPalette() {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const inputRef = useRef<HTMLInputElement | null>(null);

  const commands = useMemo<CommandItem[]>(() => [
    { label: 'Home', keywords: 'home start featured', shortcut: 'H', icon: Home, run: () => clickShellDestination('Home') },
    { label: 'Search Everything', keywords: 'search find movie tv live sports', shortcut: '/', icon: Search, run: focusSearch },
    { label: 'Live TV', keywords: 'live channels television', shortcut: 'L', icon: RadioTower, run: () => clickShellDestination('Live TV') },
    { label: 'Guide', keywords: 'guide epg schedule channels', shortcut: 'G', icon: Tv2, run: () => clickShellDestination('Guide') },
    { label: 'Sports', keywords: 'sports games teams live', shortcut: 'S', icon: Trophy, run: () => clickShellDestination('Sports') },
    { label: 'Movies', keywords: 'movies vod films', shortcut: 'M', icon: Film, run: () => clickShellDestination('Movies') },
    { label: 'TV Shows', keywords: 'series shows episodes television', icon: Tv2, run: () => clickShellDestination('TV Shows') },
    { label: 'Source Manager', keywords: 'sources addons providers stremio debrid', icon: Settings, run: () => clickShellDestination('Source Manager') },
    { label: 'Diagnostics', keywords: 'diagnostics health repair sources epg', icon: Settings, run: () => clickShellDestination('Diagnostics') },
    { label: 'My AstraWave', keywords: 'account profile settings control', icon: Settings, run: () => { window.location.href = '/app/control'; } },
  ], []);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return commands;
    return commands.filter((item) => `${item.label} ${item.keywords}`.toLowerCase().includes(q));
  }, [commands, query]);

  useEffect(() => {
    if (open) requestAnimationFrame(() => inputRef.current?.focus());
    else setQuery('');
  }, [open]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      const typing = target?.tagName === 'INPUT' || target?.tagName === 'TEXTAREA' || target?.isContentEditable;
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        setOpen((value) => !value);
        return;
      }
      if (event.key === 'Escape' && open) {
        event.preventDefault();
        setOpen(false);
        return;
      }
      if (typing || open || event.ctrlKey || event.metaKey || event.altKey) return;
      const key = event.key.toLowerCase();
      const map: Record<string, () => void> = {
        '/': focusSearch,
        h: () => clickShellDestination('Home'),
        l: () => clickShellDestination('Live TV'),
        g: () => clickShellDestination('Guide'),
        s: () => clickShellDestination('Sports'),
        m: () => clickShellDestination('Movies'),
      };
      if (key === ' ') {
        const video = document.querySelector<HTMLVideoElement>('.playerPanel video');
        if (video) {
          event.preventDefault();
          toggleVideoPlayback();
        }
        return;
      }
      const action = map[key];
      if (action) {
        event.preventDefault();
        action();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [open]);

  function execute(item: CommandItem) {
    item.run();
    setOpen(false);
  }

  return <>
    <button className="aw-command-launcher" type="button" onClick={() => setOpen(true)} aria-label="Open AstraWave command palette">
      <Command size={16}/><span>Command</span><kbd>⌘K</kbd>
    </button>
    {open && <div className="aw-command-backdrop" role="presentation" onMouseDown={() => setOpen(false)}>
      <section className="aw-command-palette" role="dialog" aria-modal="true" aria-label="AstraWave command palette" onMouseDown={(event) => event.stopPropagation()}>
        <header><Command size={18}/><input ref={inputRef} value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search AstraWave commands…"/><button type="button" onClick={() => setOpen(false)}><X size={18}/></button></header>
        <div className="aw-command-results">
          {filtered.length === 0 && <p>No matching command.</p>}
          {filtered.map((item) => {
            const Icon = item.icon;
            return <button type="button" key={item.label} onClick={() => execute(item)}>
              <Icon size={17}/><span><b>{item.label}</b><small>{item.keywords}</small></span>{item.shortcut && <kbd>{item.shortcut}</kbd>}
            </button>;
          })}
        </div>
        <footer><span>⌘K palette</span><span>/ search</span><span>G guide</span><span>L live</span><span>S sports</span><span>Space play/pause</span></footer>
      </section>
    </div>}
  </>;
}
