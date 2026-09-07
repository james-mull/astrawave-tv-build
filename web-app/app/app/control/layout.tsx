import Link from 'next/link';
import type { ReactNode } from 'react';

export default function ControlCenterLayout({ children }: { children: ReactNode }) {
  return <div className="controlShell">
    <nav className="controlNav" aria-label="AstraWave Control Center">
      <Link href="/app" className="controlBrand"><span>AW</span><div><b>AstraWave</b><small>Control Center</small></div></Link>
      <div className="controlLinks">
        <ControlLink href="/app/control">Sources</ControlLink>
        <ControlLink href="/app/control/channels">Channels</ControlLink>
        <ControlLink href="/app/control/trakt">Trakt</ControlLink>
        <ControlLink href="/app/control/diagnostics">Diagnostics</ControlLink>
      </div>
      <Link href="/app" className="controlBack">Back to AstraWave</Link>
    </nav>
    <div className="controlBody">{children}</div>
  </div>;
}

function ControlLink({href,children}:{href:string;children:ReactNode}){
  return <Link href={href} className="controlLink">{children}</Link>;
}
