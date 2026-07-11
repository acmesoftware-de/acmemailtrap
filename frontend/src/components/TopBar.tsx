import { useStore } from '../store'
import type { Section } from '../types'

const TABS: [string, Section][] = [
  ['POSTFÄCHER', 'MAIL'],
  ['SERVER', 'SRV'],
  ['WEITERLEITUNG', 'FWD'],
]

const BARS = [
  { h: 8, c: '#E5322A' },
  { h: 13, c: '#E9AE06' },
  { h: 19, c: '#1358D8' },
  { h: 11, c: '#159E5B' },
]

export default function TopBar() {
  const section = useStore((s) => s.section)
  const mode = useStore((s) => s.mode)
  const build = useStore((s) => s.build)
  const setSection = useStore((s) => s.setSection)
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
      <div style={{ display: 'flex', alignItems: 'flex-end', gap: 2, height: 19 }}>
        {BARS.map((b, i) => (
          <span key={i} style={{ width: 4, height: b.h, background: b.c }} />
        ))}
      </div>
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
        }}
      >
        <span>Postfächer durchsuchen…</span>
        <span style={{ marginLeft: 'auto', font: "700 10px 'Space Mono'", padding: '1px 5px', border: '1px solid var(--line)', color: 'var(--faint)' }}>
          ⌘K
        </span>
      </div>
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
        JS
      </div>
    </div>
  )
}
