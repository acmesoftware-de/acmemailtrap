import { useStore } from '../store'
import type { Section } from '../types'

const EYEBROW: Record<Section, string> = {
  MAIL: 'ACMEMAILTRAP · SMTP-FALLE',
  SRV: 'ACMEMAILTRAP · DIENSTE',
  FWD: 'ACMEMAILTRAP · RELAY',
  NEW: 'ACMEMAILTRAP · COMPOSER',
}

const TITLE: Record<Section, string> = {
  MAIL: 'Postfächer',
  SRV: 'Server & Protokoll',
  FWD: 'Weiterleitung',
  NEW: 'Neue Mail',
}

export default function ModuleHeader() {
  const section = useStore((s) => s.section)
  const filter = useStore((s) => s.filter)
  const setFilter = useStore((s) => s.setFilter)
  const openComposer = useStore((s) => s.openComposer)

  const segBg = (active: boolean) => (active ? 'var(--accent)' : 'transparent')
  const segCol = (active: boolean) => (active ? '#fff' : 'var(--dim)')

  return (
    <div style={{ display: 'flex', alignItems: 'flex-end', gap: 16, padding: '22px 24px 16px' }}>
      <div>
        <div style={{ font: "400 10px 'Space Mono'", letterSpacing: '.14em', color: 'var(--accent)' }}>
          {EYEBROW[section]}
        </div>
        <h1 style={{ font: "400 40px/0.9 'Anton'", color: 'var(--ink)', margin: '8px 0 0' }}>{TITLE[section]}</h1>
      </div>
      <div style={{ flex: 1 }} />
      {section === 'MAIL' && (
        <div style={{ display: 'flex', border: '1px solid var(--line2)', marginBottom: 7 }}>
          <button
            onClick={() => setFilter('alle')}
            style={{
              padding: '8px 13px',
              border: 'none',
              font: "700 10px 'Space Mono'",
              letterSpacing: '.04em',
              cursor: 'pointer',
              background: segBg(filter === 'alle'),
              color: segCol(filter === 'alle'),
            }}
          >
            ALLE
          </button>
          <button
            onClick={() => setFilter('unread')}
            style={{
              padding: '8px 13px',
              border: 'none',
              borderLeft: '1px solid var(--line2)',
              font: "700 10px 'Space Mono'",
              letterSpacing: '.04em',
              cursor: 'pointer',
              background: segBg(filter === 'unread'),
              color: segCol(filter === 'unread'),
            }}
          >
            UNGELESEN
          </button>
        </div>
      )}
      <button
        onClick={openComposer}
        style={{
          marginBottom: 7,
          padding: '8px 15px',
          border: '1px solid var(--accent)',
          background: 'var(--accent)',
          color: '#fff',
          font: "700 10px 'Space Mono'",
          letterSpacing: '.04em',
          cursor: 'pointer',
        }}
      >
        + NEUE MAIL
      </button>
    </div>
  )
}
