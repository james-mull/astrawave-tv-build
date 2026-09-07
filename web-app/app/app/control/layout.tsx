import Link from 'next/link';
import type { ReactNode } from 'react';

export default function ControlCenterLayout({ children }: { children: ReactNode }) {
  return <>
    <nav aria-label="AstraWave Control Center" style={{position:'sticky',top:0,zIndex:80,display:'flex',gap:8,alignItems:'center',flexWrap:'wrap',padding:'10px 18px',background:'rgba(6,7,11,.94)',borderBottom:'1px solid #27212d',backdropFilter:'blur(16px)'}}>
      <strong style={{color:'#c38bff',marginRight:6,letterSpacing:'.08em'}}>ASTRAWAVE CONTROL</strong>
      <ControlLink href="/app/control">Sources & Settings</ControlLink>
      <ControlLink href="/app/control/channels">Channel Studio</ControlLink>
      <ControlLink href="/app/control/trakt">Trakt</ControlLink>
      <ControlLink href="/app/control/diagnostics">Playback Diagnostics</ControlLink>
      <span style={{marginLeft:'auto',fontSize:12,color:'#77707e'}}>Private account tools • no playback secrets shown</span>
    </nav>
    {children}
  </>;
}

function ControlLink({href,children}:{href:string;children:ReactNode}){
  return <Link href={href} style={{color:'#f6f3f9',textDecoration:'none',background:'#17141d',border:'1px solid #30283a',borderRadius:999,padding:'8px 12px',fontSize:13,fontWeight:700}}>{children}</Link>;
}
