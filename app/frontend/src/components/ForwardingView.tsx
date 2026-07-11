import { useStore } from '../store'
import type { ConfigField } from '../types'

const inputStyle = {
  width: '100%',
  border: '1px solid var(--line2)',
  background: 'var(--bg)',
  color: 'var(--ink)',
  padding: '10px 12px',
  font: "400 13px 'Archivo'",
} as const

const labelStyle = {
  font: "700 9px 'Space Mono'",
  letterSpacing: '.06em',
  color: 'var(--faint)',
  marginBottom: 7,
} as const

export default function ForwardingView() {
  const fwd = useStore((s) => s.fwd)
  const forwarders = useStore((s) => s.forwarders)
  const mailboxes = useStore((s) => s.mailboxes)
  const setFwd = useStore((s) => s.setFwd)
  const setForwarderId = useStore((s) => s.setForwarderId)
  const setValue = useStore((s) => s.setValue)
  const toggleFwdBox = useStore((s) => s.toggleFwdBox)
  const saveForward = useStore((s) => s.saveForward)

  const boxes = mailboxes.filter((m) => !m.sent)
  const rules = boxes.filter((b) => fwd.mailboxes.includes(b.address))
  const selected = forwarders.find((p) => p.id === fwd.forwarderId)

  return (
    <div style={{ flex: 1, minHeight: 0, display: 'flex', gap: 14, padding: '16px 24px 24px', borderTop: '1px solid var(--line)', overflow: 'auto' }}>
      <div style={{ flex: 1, minWidth: 0, background: 'var(--panel)', border: '1px solid var(--line)', padding: '22px 24px' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', paddingBottom: 16, borderBottom: '1px solid var(--line)' }}>
          <div>
            <div style={{ font: "600 14px 'Archivo'", color: 'var(--ink)' }}>Echten E-Mail-Dienst anbinden</div>
            <div style={{ font: "400 11.5px 'Archivo'", color: 'var(--dim)', marginTop: 3 }}>
              Abgefangene Mails zusätzlich über ein echtes Relay real zustellen.
            </div>
          </div>
          <div
            onClick={() => setFwd({ enabled: !fwd.enabled })}
            style={{ width: 46, height: 26, borderRadius: 13, background: fwd.enabled ? 'var(--accent)' : 'var(--line2)', position: 'relative', cursor: 'pointer', flex: 'none', transition: 'background .15s' }}
          >
            <span style={{ position: 'absolute', top: 3, left: fwd.enabled ? 23 : 3, width: 20, height: 20, borderRadius: '50%', background: '#fff', transition: 'left .15s' }} />
          </div>
        </div>

        <div style={{ marginTop: 18 }}>
          <div style={labelStyle}>DIENST</div>
          <select value={fwd.forwarderId} onChange={(e) => setForwarderId(e.target.value)} style={{ ...inputStyle, font: "400 13px 'Space Mono'" }}>
            {forwarders.map((p) => (
              <option key={p.id} value={p.id}>
                {p.displayName}
              </option>
            ))}
          </select>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14, marginTop: 16 }}>
          {selected?.schema.map((f) => (
            <Field key={f.key} field={f} value={fwd.values[f.key] ?? ''} onChange={(v) => setValue(f.key, v)} />
          ))}
        </div>

        <div style={{ ...labelStyle, margin: '20px 0 8px' }}>POSTFÄCHER WEITERLEITEN</div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
          {boxes.map((b) => {
            const on = fwd.mailboxes.includes(b.address)
            return (
              <button
                key={b.folder}
                onClick={() => toggleFwdBox(b.address)}
                style={{
                  padding: '7px 13px',
                  border: on ? '1px solid var(--accent)' : '1px solid var(--line2)',
                  background: on ? 'var(--accent)' : 'transparent',
                  color: on ? '#fff' : 'var(--dim)',
                  font: "700 10px 'Space Mono'",
                  letterSpacing: '.02em',
                  cursor: 'pointer',
                }}
              >
                {b.address}
              </button>
            )
          })}
          {boxes.length === 0 && <span style={{ font: "400 11px 'Archivo'", color: 'var(--faint)' }}>Noch keine Empfänger-Postfächer.</span>}
        </div>

        <button
          onClick={() => void saveForward()}
          style={{ marginTop: 22, padding: '11px 20px', border: '1px solid var(--accent)', background: 'var(--accent)', color: '#fff', font: "700 10px 'Space Mono'", letterSpacing: '.06em', cursor: 'pointer' }}
        >
          VERBINDUNG SPEICHERN
        </button>
        {fwd.flash && <span style={{ marginLeft: 14, font: "700 10px 'Space Mono'", color: '#159E5B' }}>{fwd.flash}</span>}
      </div>

      <div style={{ flex: 'none', width: 340, background: 'var(--panel)', border: '1px solid var(--line)', padding: 20, alignSelf: 'flex-start' }}>
        <div style={{ font: "700 9.5px 'Space Mono'", letterSpacing: '.08em', color: 'var(--faint)' }}>AKTIVE WEITERLEITUNGEN</div>
        {fwd.enabled ? (
          rules.length > 0 ? (
            rules.map((r) => (
              <div key={r.folder} style={{ borderLeft: '3px solid var(--accent)', background: 'var(--chip)', padding: '12px 14px', marginTop: 12 }}>
                <div style={{ font: "600 12.5px 'Archivo'", color: 'var(--ink)' }}>{r.address}</div>
                <div style={{ font: "400 10.5px 'Space Mono'", color: 'var(--dim)', marginTop: 5 }}>
                  → {selected?.displayName ?? fwd.forwarderId}
                </div>
              </div>
            ))
          ) : (
            <div style={{ marginTop: 14, font: "400 12px/1.6 'Archivo'", color: 'var(--dim)' }}>
              Weiterleitung aktiv über {selected?.displayName ?? fwd.forwarderId}, aber kein Postfach ausgewählt — alle abgefangenen Mails werden weitergeleitet.
            </div>
          )
        ) : (
          <div style={{ marginTop: 14, font: "400 12px/1.6 'Archivo'", color: 'var(--dim)' }}>
            Weiterleitung ist deaktiviert. Alle Mails werden ausschließlich abgefangen und lokal in ACMEmailtrap gespeichert.
          </div>
        )}
      </div>
    </div>
  )
}

function Field({ field, value, onChange }: { field: ConfigField; value: string; onChange: (v: string) => void }) {
  return (
    <div>
      <div style={labelStyle}>{field.label.toUpperCase()}</div>
      {field.type === 'SELECT' ? (
        <select value={value} onChange={(e) => onChange(e.target.value)} style={{ ...inputStyle, font: "400 13px 'Space Mono'" }}>
          {field.options.map((o) => (
            <option key={o} value={o}>
              {o}
            </option>
          ))}
        </select>
      ) : field.type === 'BOOL' ? (
        <input type="checkbox" checked={value === 'true'} onChange={(e) => onChange(String(e.target.checked))} />
      ) : (
        <input
          type={field.secret ? 'password' : field.type === 'NUMBER' ? 'text' : 'text'}
          value={value}
          placeholder={field.secret ? '••••••••' : ''}
          onChange={(e) => onChange(e.target.value)}
          style={{ ...inputStyle, font: field.type === 'NUMBER' || field.secret ? "400 13px 'Space Mono'" : "400 13px 'Archivo'" }}
        />
      )}
    </div>
  )
}
