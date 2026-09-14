'use client';

import Link from 'next/link';
import { useState } from 'react';
import { Film, Home, RadioTower, Search, UserCircle2 } from 'lucide-react';

const primary = [
  { label: 'Home', icon: Home, target: 'Home' },
  { label: 'Search', icon: Search, target: 'Search' },
  { label: 'Live', icon: RadioTower, target: 'Live TV' },
  { label: 'VOD', icon: Film, target: 'Movies' },
] as const;

function clickShellDestination(target: string) {
  if (target === 'Search') {
    const input = document.querySelector<HTMLInputElement>('.top .search input');
    input?.focus();
    input?.scrollIntoView({ block: 'center', behavior: 'smooth' });
    return;
  }
  const buttons = Array.from(document.querySelectorAll<HTMLButtonElement>('.side nav button'));
  const button = buttons.find((item) => item.textContent?.trim() === target);
  button?.click();
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

export default function MobilePrimaryNav() {
  const [active, setActive] = useState('Home');
  return (
    <nav className="mobile-primary-nav" aria-label="Primary mobile navigation">
      {primary.map(({ label, icon: Icon, target }) => (
        <button
          key={label}
          type="button"
          className={active === target ? 'active' : ''}
          aria-current={active === target ? 'page' : undefined}
          onClick={() => {
            setActive(target);
            clickShellDestination(target);
          }}
        >
          <Icon size={19} />
          <span>{label}</span>
        </button>
      ))}
      <Link href="/app/control" className={active === 'My' ? 'active' : ''} onClick={() => setActive('My')}>
        <UserCircle2 size={19} />
        <span>My</span>
      </Link>
    </nav>
  );
}
