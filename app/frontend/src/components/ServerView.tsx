import { useStore } from '../store'
import { fmtLogTime, fmtUptime, tagColor } from '../format'

interface ServerCard {
  name: string
  up: boolean
  hostport: string
  stats: { k: string; v: string }[]
}

export default function ServerView() {
  const server = useStore((s) => s.server)
  const logs = useStore((s) => s.logs)
  const accent = useStore((s) => s.accent)
  const build = useStore((s) => s.build)

  const cards: ServerCard[] = server
    ? [
        {
          name: 'SMTP-Empfänger',
          up: server.smtp.up,
          hostport: `127.0.0.1:${server.smtp.port}`,
          stats: [
            { k: 'UPTIME', v: fmtUptime(server.smtp.uptimeSec) },
            { k: 'EMPFANGEN', v: String(server.smtp.received) },
            { k: 'FEHLER', v: String(server.smtp.errors) },
          ],
        },
        {
          name: 'IMAP-Abfrage',
          up: server.imap.up,
          hostport: `127.0.0.1:${server.imap.port}`,
          stats: [
            { k: 'UPTIME', v: fmtUptime(server.imap.uptimeSec) },
            { k: 'SITZUNGEN', v: String(server.imap.sessions) },
            { k: 'ORDNER', v: String(server.imap.folders) },
          ],
        },
      ]
    : []

  const connRows = [
    { k: 'SMTP Host', v: '127.0.0.1' },
    { k: 'SMTP Port', v: String(server?.smtp.port ?? 1025) },
    { k: 'IMAP Host', v: '127.0.0.1' },
    { k: 'IMAP Port', v: String(server?.imap.port ?? 1143) },
    { k: 'Auth / TLS', v: 'keine (Test)' },
  ]

  return (
    <div style={{ flex: 1, minHeight: 0, display: 'flex', gap: 14, padding: '16px 24px 24px', borderTop: '1px solid var(--line)' }}>
      <div style={{ flex: 'none', width: 520, display: 'flex', flexDirection: 'column', gap: 14, overflow: 'auto' }}>
        {cards.map((s) => {
          const col = s.up ? '#159E5B' : '#E5322A'
          return (
            <div key={s.name} style={{ background: 'var(--panel)', border: '1px solid var(--line)', borderTop: `3px solid ${col}`, padding: '18px 20px', flex: 'none' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <span style={{ width: 9, height: 9, borderRadius: '50%', background: col, animation: 'acmepulse 1.7s infinite', flex: 'none' }} />
                <span style={{ font: "700 13px 'Archivo'", color: 'var(--ink)' }}>{s.name}</span>
                <span style={{ font: "700 9px 'Space Mono'", letterSpacing: '.08em', color: col, marginLeft: 'auto' }}>{s.up ? 'LÄUFT' : 'AUS'}</span>
              </div>
              <div style={{ font: "700 22px 'Space Mono'", color: 'var(--ink)', marginTop: 14 }}>{s.hostport}</div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 10, marginTop: 16 }}>
                {s.stats.map((st) => (
                  <div key={st.k}>
                    <div style={{ font: "700 8.5px 'Space Mono'", letterSpacing: '.06em', color: 'var(--faint)' }}>{st.k}</div>
                    <div style={{ font: "400 20px 'Anton'", color: 'var(--ink)', marginTop: 4 }}>{st.v}</div>
                  </div>
                ))}
              </div>
            </div>
          )
        })}
        <div style={{ background: 'var(--panel)', border: '1px solid var(--line)', padding: '16px 20px', flex: 'none' }}>
          <div style={{ font: "700 9.5px 'Space Mono'", letterSpacing: '.08em', color: 'var(--faint)' }}>VERBINDUNGSDATEN FÜR ACMESUITE</div>
          <div style={{ marginTop: 12, display: 'flex', flexDirection: 'column', gap: 9, font: "400 12px 'Space Mono'", color: 'var(--dim)' }}>
            {connRows.map((c) => (
              <div key={c.k} style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>{c.k}</span>
                <span style={{ color: 'var(--ink)' }}>{c.v}</span>
              </div>
            ))}
          </div>
        </div>
        {build && (
          <div style={{ background: 'var(--panel)', border: '1px solid var(--line)', padding: '14px 20px', flex: 'none' }}>
            <div style={{ font: "700 9.5px 'Space Mono'", letterSpacing: '.08em', color: 'var(--faint)' }}>BUILD</div>
            <div style={{ marginTop: 10, display: 'flex', flexDirection: 'column', gap: 8, font: "400 12px 'Space Mono'", color: 'var(--dim)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>Version</span>
                <span style={{ color: 'var(--ink)' }}>{build.version}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>Commit</span>
                <span style={{ color: 'var(--ink)' }}>{build.commit}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>Branch</span>
                <span style={{ color: 'var(--ink)' }}>{build.branch || '—'}</span>
              </div>
              {build.buildTime && (
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span>Gebaut</span>
                  <span style={{ color: 'var(--ink)' }}>{new Date(build.buildTime).toLocaleString('de-DE')}</span>
                </div>
              )}
            </div>
          </div>
        )}
      </div>

      <div style={{ flex: 1, minWidth: 0, background: 'var(--panel)', border: '1px solid var(--line)', display: 'flex', flexDirection: 'column' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '14px 18px', borderBottom: '1px solid var(--line)', flex: 'none' }}>
          <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#159E5B', animation: 'acmepulse 1.7s infinite' }} />
          <span style={{ font: "700 10px 'Space Mono'", letterSpacing: '.08em', color: 'var(--ink)' }}>LIVE-PROTOKOLL</span>
          <span style={{ marginLeft: 'auto', font: "400 10px 'Space Mono'", color: 'var(--faint)' }}>{logs.length} Einträge</span>
        </div>
        <div style={{ flex: 1, minHeight: 0, overflow: 'auto', padding: '6px 0' }}>
          {logs.map((l, i) => (
            <div
              key={`${l.time}-${i}`}
              style={{ display: 'flex', gap: 12, alignItems: 'baseline', padding: '6px 18px', font: "400 11.5px 'Space Mono'", animation: i === 0 ? 'acmefade .45s' : undefined }}
            >
              <span style={{ color: 'var(--faint)', flex: 'none' }}>{fmtLogTime(l.time)}</span>
              <span style={{ fontWeight: 700, color: tagColor(l.tag, accent), flex: 'none', width: 46 }}>{l.tag}</span>
              <span style={{ color: 'var(--dim)' }}>{l.msg}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
