import { useStore } from '../store'

const FROM_OPTS = [
  'no-reply@acme.test',
  'billing@acme.test',
  'alerts@acme.test',
  'security@acme.test',
  'crm@acme.test',
]

const inputStyle = {
  width: '100%',
  border: '1px solid var(--line2)',
  background: 'var(--bg)',
  color: 'var(--ink)',
  padding: '10px 12px',
} as const

const labelStyle = {
  font: "700 9px 'Space Mono'",
  letterSpacing: '.06em',
  color: 'var(--faint)',
  margin: '16px 0 7px',
} as const

export default function ComposerView() {
  const comp = useStore((s) => s.comp)
  const compFlash = useStore((s) => s.compFlash)
  const setComp = useStore((s) => s.setComp)
  const sendMail = useStore((s) => s.sendMail)
  const discardComposer = useStore((s) => s.discardComposer)

  return (
    <div style={{ flex: 1, minHeight: 0, overflow: 'auto', padding: '16px 24px 24px', borderTop: '1px solid var(--line)', display: 'flex', justifyContent: 'center' }}>
      <div style={{ width: 680, background: 'var(--panel)', border: '1px solid var(--line)', padding: '24px 26px', alignSelf: 'flex-start' }}>
        <div style={{ font: "600 15px 'Archivo'", color: 'var(--ink)' }}>Neue Test-Mail senden</div>
        <div style={{ font: "400 11.5px 'Archivo'", color: 'var(--dim)', marginTop: 3 }}>
          Der Empfänger erscheint automatisch als neues Postfach. Es wird nichts real zugestellt.
        </div>

        <div style={{ ...labelStyle, marginTop: 20 }}>VON</div>
        <select value={comp.from} onChange={(e) => setComp('from', e.target.value)} style={{ ...inputStyle, font: "400 13px 'Space Mono'" }}>
          {FROM_OPTS.map((o) => (
            <option key={o} value={o}>
              {o}
            </option>
          ))}
        </select>

        <div style={labelStyle}>AN</div>
        <input value={comp.to} onChange={(e) => setComp('to', e.target.value)} placeholder="empfaenger@test.local" style={{ ...inputStyle, font: "400 13px 'Space Mono'" }} />

        <div style={labelStyle}>BETREFF</div>
        <input value={comp.subject} onChange={(e) => setComp('subject', e.target.value)} placeholder="Betreff der Test-Mail" style={{ ...inputStyle, font: "400 13px 'Archivo'" }} />

        <div style={labelStyle}>NACHRICHT</div>
        <textarea value={comp.body} onChange={(e) => setComp('body', e.target.value)} rows={7} placeholder="Text der Nachricht…" style={{ ...inputStyle, font: "400 13px/1.5 'Archivo'", resize: 'vertical' }} />

        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 20 }}>
          <button
            onClick={() => void sendMail()}
            style={{ padding: '11px 22px', border: '1px solid var(--accent)', background: 'var(--accent)', color: '#fff', font: "700 10px 'Space Mono'", letterSpacing: '.06em', cursor: 'pointer' }}
          >
            SENDEN
          </button>
          <button
            onClick={discardComposer}
            style={{ padding: '11px 18px', border: '1px solid var(--line2)', background: 'transparent', color: 'var(--dim)', font: "700 10px 'Space Mono'", letterSpacing: '.06em', cursor: 'pointer' }}
          >
            VERWERFEN
          </button>
          {compFlash && <span style={{ font: "700 10px 'Space Mono'", color: '#159E5B' }}>{compFlash}</span>}
        </div>
      </div>
    </div>
  )
}
