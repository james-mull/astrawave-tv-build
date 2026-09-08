'use client';

import Link from 'next/link';
import { Film, Home, RadioTower, Trophy, UserCircle2 } from 'lucide-react';

const primary = [
  { label: 'Home', icon: Home, target: 'Home' },
  { label: 'Live', icon: RadioTower, target: 'Live TV' },
  { label: 'Sports', icon: Trophy, target: 'Sports' },
  { label: 'Movies', icon: Film, target: 'Movies' },
] as const;

function clickShellDestination(target: string) {
  const buttons = Array.from(document.querySelectorAll<HTMLButtonElement>('.side nav button'));
  const button = buttons.find((item) => item.textContent?.trim() === target);
  button?.click();
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

export default function MobilePrimaryNav() {
  return (
    <nav className="mobile-primary-nav" aria-label="Primary mobile navigation">
      {primary.map(({ label, icon: Icon, target }) => (
        <button key={label} type="button" onClick={() => clickShellDestination(target)}>
          <Icon size={19} />
          <span>{label}</span>
        </button>
      ))}
      <Link href="/app/control">
        <UserCircle2 size={19} />
        <span>My</span>
      </Link>
    </nav>
  );
}
