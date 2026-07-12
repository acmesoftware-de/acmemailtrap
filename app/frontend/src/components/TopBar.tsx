import { useStore } from '../store'
import type { Section } from '../types'
import LogoGlyph from './LogoGlyph'

const TABS: [string, Section][] = [
  ['POSTFÄCHER', 'MAIL'],
  ['SERVER', 'SRV'],
  ['WEITERLEITUNG', 'FWD'],
]

export default function TopBar() {
  const section = useStore((s) => s.section)
  const mode = useStore((s) => s.mode)
  const build = useStore((s) => s.build)
  const auth = useStore((s) => s.auth)
  const setSection = useStore((s) => s.setSection)
  const setPalette = useStore((s) => s.setPalette)
  const toggleTheme = useStore((s) => s.toggleTheme)

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        height: 57,
        flex: 'none',
        padding: '0 20px',
        borderBottom: '1px solid var(--line)',
        background: 'var(--panel)',
      }}
    >
      <LogoGlyph height={19} />
      <span style={{ font: "700 13px 'Archivo'", letterSpacing: '.06em', color: 'var(--ink)', marginLeft: 9 }}>
        ACME<span style={{ color: 'var(--accent)', fontWeight: 500 }}>MAILTRAP</span>
      </span>
      {build && (
        <span
          title={`${build.branch}${build.buildTime ? ' · ' + new Date(build.buildTime).toLocaleString('de-DE') : ''}`}
          style={{ font: "9px 'Space Mono'", color: 'var(--faint)', marginLeft: 8, whiteSpace: 'nowrap' }}
        >
          v{build.version} · {build.commit}
        </span>
      )}
      <div style={{ width: 1, height: 22, background: 'var(--line)', margin: '0 18px' }} />
      <div style={{ display: 'flex', alignSelf: 'stretch', alignItems: 'stretch' }}>
        {TABS.map(([code, key]) => {
          const active = section === key
          return (
            <div
              key={key}
              onClick={() => setSection(key)}
              style={{ display: 'flex', alignItems: 'center', padding: '0 15px', position: 'relative', cursor: 'pointer' }}
            >
              <span style={{ font: "13px 'Archivo'", fontWeight: active ? 700 : 500, color: active ? 'var(--ink)' : 'var(--dim)' }}>
                {code}
              </span>
              <span
                style={{
                  position: 'absolute',
                  left: 0,
                  right: 0,
                  bottom: -1,
                  height: 3,
                  background: active ? 'var(--accent)' : 'transparent',
                }}
              />
            </div>
          )
        })}
      </div>
      <div style={{ flex: 1 }} />
      <div
        onClick={() => setPalette(true)}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          padding: '7px 11px',
          minWidth: 190,
          border: '1px solid var(--line)',
          background: 'var(--bg)',
          color: 'var(--dim)',
          fontSize: 12.5,
          cursor: 'pointer',
        }}
      >
        <span>Postfächer durchsuchen…</span>
        <span style={{ marginLeft: 'auto', font: "700 10px 'Space Mono'", padding: '1px 5px', border: '1px solid var(--line)', color: 'var(--faint)' }}>
          ⌘K
        </span>
      </div>
      <a
        href="/swagger.html"
        target="_blank"
        rel="noopener"
        title="API-Dokumentation (Swagger UI)"
        style={{
          marginLeft: 10,
          padding: '8px 10px',
          border: '1px solid var(--line)',
          background: 'var(--bg)',
          color: 'var(--dim)',
          font: "700 10px 'Space Mono'",
          letterSpacing: '.04em',
          textDecoration: 'none',
        }}
      >
        API
      </a>
      <button
        onClick={toggleTheme}
        title="Hell / Dunkel"
        style={{
          width: 34,
          height: 34,
          marginLeft: 10,
          border: '1px solid var(--line)',
          background: 'var(--bg)',
          color: 'var(--ink)',
          fontSize: 15,
          cursor: 'pointer',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        {mode === 'dark' ? '◐' : '◑'}
      </button>
      <div
        title={auth?.user ?? undefined}
        style={{
          width: 32,
          height: 32,
          marginLeft: 10,
          background: 'var(--accent)',
          color: '#fff',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          font: "700 11px 'Space Mono'",
        }}
      >
        {initials(auth?.user)}
      </div>
      {auth?.enabled && auth?.authenticated && (
        <form action="/logout" method="post" style={{ margin: 0 }}>
          <button
            type="submit"
            title="Abmelden"
            style={{
              marginLeft: 8,
              padding: '8px 10px',
              border: '1px solid var(--line)',
              background: 'var(--bg)',
              color: 'var(--dim)',
              font: "700 9px 'Space Mono'",
              letterSpacing: '.04em',
              cursor: 'pointer',
            }}
          >
            ABMELDEN
          </button>
        </form>
      )}
    </div>
  )
}

function initials(user: string | null | undefined): string {
  if (!user) return 'JS'
  const parts = user.replace(/[^A-Za-z ]/g, ' ').trim().split(/\s+/).filter(Boolean)
  const a = parts[0]?.[0] ?? user[0]
  const b = parts[1]?.[0] ?? ''
  return (a + b).toUpperCase() || 'JS'
}
