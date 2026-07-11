import { useStore } from '../store'

interface Kpi {
  label: string
  value: string
  sub: string
  subcol: string
}

export default function KpiBar() {
  const section = useStore((s) => s.section)
  const stats = useStore((s) => s.stats)
  const server = useStore((s) => s.server)

  if (section !== 'MAIL' && section !== 'SRV') return null

  let kpis: Kpi[] = []
  if (section === 'MAIL') {
    kpis = [
      { label: 'NACHRICHTEN', value: String(stats?.messages ?? 0), sub: 'abgefangen', subcol: 'var(--faint)' },
      { label: 'POSTFÄCHER', value: String(stats?.mailboxes ?? 0), sub: 'Empfänger', subcol: 'var(--faint)' },
      { label: 'UNGELESEN', value: String(stats?.unread ?? 0), sub: 'neu', subcol: 'var(--accent)' },
      { label: 'HEUTE', value: String(stats?.today ?? 0), sub: 'empfangen', subcol: '#159E5B' },
    ]
  } else {
    const smtp = server?.smtp
    const imap = server?.imap
    kpis = [
      { label: `SMTP :${smtp?.port ?? 1025}`, value: smtp?.up ? 'Aktiv' : 'Aus', sub: '●', subcol: smtp?.up ? '#159E5B' : '#E5322A' },
      { label: `IMAP :${imap?.port ?? 1143}`, value: imap?.up ? 'Aktiv' : 'Aus', sub: '●', subcol: imap?.up ? '#159E5B' : '#E5322A' },
      { label: 'VERBINDUNGEN', value: String(server?.connections ?? 0), sub: 'live', subcol: 'var(--faint)' },
      { label: 'DURCHSATZ', value: String(server?.throughputPerMin ?? 0), sub: '/min', subcol: 'var(--faint)' },
    ]
  }

  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 12, padding: '0 24px 16px', flex: 'none' }}>
      {kpis.map((k) => (
        <div key={k.label} style={{ background: 'var(--panel)', border: '1px solid var(--line)', padding: '13px 15px' }}>
          <div style={{ font: "700 9.5px 'Space Mono'", letterSpacing: '.08em', color: 'var(--faint)' }}>{k.label}</div>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, marginTop: 9 }}>
            <span style={{ font: "400 27px 'Anton'", color: 'var(--ink)' }}>{k.value}</span>
            <span style={{ font: "700 10px 'Space Mono'", color: k.subcol }}>{k.sub}</span>
          </div>
        </div>
      ))}
    </div>
  )
}
