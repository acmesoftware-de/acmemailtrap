import { useStore } from '../store'
import { api } from '../api'
import {
  fmtDateFull,
  fmtTime,
  initials,
  modColor,
  modTag,
  parseFrom,
} from '../format'
import type { MailboxInfo, MessageMeta } from '../types'

export default function MailboxesView() {
  const mailboxes = useStore((s) => s.mailboxes)
  const currentMailbox = useStore((s) => s.currentMailbox)
  const messages = useStore((s) => s.messages)
  const filter = useStore((s) => s.filter)
  const currentMessageId = useStore((s) => s.currentMessageId)

  const shown = filter === 'unread' ? messages.filter((m) => !m.seen) : messages

  return (
    <div style={{ flex: 1, minHeight: 0, display: 'flex', borderTop: '1px solid var(--line)' }}>
      <Rail mailboxes={mailboxes} current={currentMailbox} />
      <List messages={shown} currentMailbox={currentMailbox} currentMessageId={currentMessageId} />
      <Reader />
    </div>
  )
}

function Rail({ mailboxes, current }: { mailboxes: MailboxInfo[]; current: string | null }) {
  const selectMailbox = useStore((s) => s.selectMailbox)
  return (
    <div
      style={{
        flex: 'none',
        width: 246,
        display: 'flex',
        flexDirection: 'column',
        borderRight: '1px solid var(--line)',
        background: 'var(--panel)',
      }}
    >
      <div style={{ padding: '13px 16px 9px', font: "700 9.5px 'Space Mono'", letterSpacing: '.08em', color: 'var(--faint)' }}>
        POSTFÄCHER · EMPFÄNGER
      </div>
      <div style={{ flex: 1, minHeight: 0, overflow: 'auto' }}>
        {mailboxes.map((mb) => {
          const active = mb.address === current
          const label = mb.label ?? (mb.sent ? 'Ausgehende Test-Mails' : 'Empfänger-Postfach')
          return (
            <div
              key={mb.folder}
              onClick={() => void selectMailbox(mb.address)}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                padding: '11px 14px',
                cursor: 'pointer',
                borderLeft: `3px solid ${active ? 'var(--accent)' : 'transparent'}`,
                background: active ? 'var(--chip)' : 'transparent',
              }}
            >
              <span
                style={{
                  width: 8,
                  height: 8,
                  borderRadius: '50%',
                  background: mb.unseen > 0 ? 'var(--accent)' : 'var(--line2)',
                  flex: 'none',
                }}
              />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ font: "600 12.5px 'Archivo'", color: 'var(--ink)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {mb.address}
                </div>
                <div style={{ font: "500 10px 'Space Mono'", letterSpacing: '.01em', color: 'var(--faint)', marginTop: 2, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {label}
                </div>
              </div>
              {mb.unseen > 0 && (
                <span style={{ font: "700 10px 'Space Mono'", background: 'var(--accent)', color: '#fff', padding: '1px 6px', minWidth: 18, textAlign: 'center', flex: 'none' }}>
                  {mb.unseen}
                </span>
              )}
            </div>
          )
        })}
      </div>
      <div style={{ padding: '12px 16px', borderTop: '1px solid var(--line)', font: "400 10px 'Space Mono'", color: 'var(--faint)', lineHeight: 1.5 }}>
        Alle eingehenden Mails werden abgefangen und nach Empfänger sortiert abgelegt.
      </div>
    </div>
  )
}

function List({
  messages,
  currentMailbox,
  currentMessageId,
}: {
  messages: MessageMeta[]
  currentMailbox: string | null
  currentMessageId: string | null
}) {
  const selectMessage = useStore((s) => s.selectMessage)
  return (
    <div style={{ flex: 'none', width: 360, display: 'flex', flexDirection: 'column', borderRight: '1px solid var(--line)', background: 'var(--bg)' }}>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, padding: '13px 16px 11px', borderBottom: '1px solid var(--line)' }}>
        <span style={{ font: "600 13px 'Archivo'", color: 'var(--ink)' }}>{currentMailbox ?? '—'}</span>
        <span style={{ marginLeft: 'auto', font: "400 10px 'Space Mono'", color: 'var(--faint)' }}>{messages.length} Nachrichten</span>
      </div>
      <div style={{ flex: 1, minHeight: 0, overflow: 'auto' }}>
        {messages.map((m) => {
          const active = m.id === currentMessageId
          const fw = m.seen ? 400 : 600
          const name = parseFrom(m.from).name
          return (
            <div
              key={m.id}
              onClick={() => void selectMessage(m.id)}
              style={{
                padding: '12px 16px',
                cursor: 'pointer',
                borderBottom: '1px solid var(--line)',
                borderLeft: `3px solid ${active ? 'var(--accent)' : 'transparent'}`,
                background: active ? 'var(--chip)' : 'transparent',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                {!m.seen && <span style={{ width: 7, height: 7, borderRadius: '50%', background: 'var(--accent)', flex: 'none' }} />}
                <span style={{ flex: 1, minWidth: 0, font: "12.5px 'Archivo'", fontWeight: fw, color: 'var(--ink)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {name}
                </span>
                <span style={{ font: "400 10px 'Space Mono'", color: 'var(--faint)', flex: 'none' }}>{fmtTime(m.receivedAt)}</span>
              </div>
              <div style={{ font: "12.5px 'Archivo'", fontWeight: fw, color: 'var(--ink)', marginTop: 5, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                {m.subject || '(kein Betreff)'}
              </div>
              {m.snippet && (
                <div style={{ font: "400 11.5px 'Archivo'", color: 'var(--dim)', marginTop: 3, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {m.snippet}
                </div>
              )}
            </div>
          )
        })}
        {messages.length === 0 && (
          <div style={{ padding: '40px 24px', textAlign: 'center', font: "400 12px 'Archivo'", color: 'var(--faint)' }}>
            Keine Nachrichten in dieser Ansicht.
          </div>
        )}
      </div>
    </div>
  )
}

function Reader() {
  const detail = useStore((s) => s.detail)
  const readView = useStore((s) => s.readView)
  const rawText = useStore((s) => s.rawText)
  const accent = useStore((s) => s.accent)
  const currentMailbox = useStore((s) => s.currentMailbox)
  const setReadView = useStore((s) => s.setReadView)
  const deleteMessage = useStore((s) => s.deleteMessage)
  const forwardMessage = useStore((s) => s.forwardMessage)

  if (!detail) {
    return (
      <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'var(--panel)', color: 'var(--faint)', font: "400 13px 'Archivo'" }}>
        Keine Nachricht ausgewählt
      </div>
    )
  }

  const { name, addr } = parseFrom(detail.from)
  const seg = (active: boolean) => ({ background: active ? 'var(--accent)' : 'transparent', color: active ? '#fff' : 'var(--dim)' })

  return (
    <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', background: 'var(--panel)' }}>
      <div style={{ padding: '20px 24px 16px', borderBottom: '1px solid var(--line)', flex: 'none' }}>
        <div style={{ display: 'flex', alignItems: 'flex-start', gap: 16 }}>
          <h1 style={{ flex: 1, minWidth: 0, font: "400 24px/1.05 'Anton'", color: 'var(--ink)', margin: 0 }}>
            {detail.subject || '(kein Betreff)'}
          </h1>
          <div style={{ display: 'flex', gap: 8, flex: 'none' }}>
            <button onClick={() => void forwardMessage()} style={ghost('var(--ink)')}>
              WEITERLEITEN
            </button>
            <button onClick={() => void deleteMessage()} style={ghost('#E5322A')}>
              LÖSCHEN
            </button>
          </div>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 11, marginTop: 14 }}>
          <div style={{ width: 34, height: 34, background: modColor(detail.mod, accent), color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', font: "700 12px 'Space Mono'", flex: 'none' }}>
            {initials(name)}
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ font: "600 12.5px 'Archivo'", color: 'var(--ink)' }}>
              {name} <span style={{ color: 'var(--dim)', fontWeight: 400 }}>&lt;{addr}&gt;</span>
            </div>
            <div style={{ font: "400 11px 'Space Mono'", color: 'var(--faint)', marginTop: 2 }}>
              an {currentMailbox} · {fmtDateFull(detail.receivedAt)}
            </div>
          </div>
          <span style={{ font: "700 9px 'Space Mono'", letterSpacing: '.08em', padding: '3px 8px', border: '1px solid var(--line2)', color: 'var(--dim)', flex: 'none' }}>
            {modTag(detail.mod)}
          </span>
        </div>
        <div style={{ display: 'flex', border: '1px solid var(--line2)', marginTop: 15, width: 'max-content' }}>
          {(['html', 'text', 'raw'] as const).map((v, i) => (
            <button
              key={v}
              onClick={() => void setReadView(v)}
              style={{
                padding: '6px 15px',
                border: 'none',
                borderLeft: i === 0 ? 'none' : '1px solid var(--line2)',
                font: "700 9.5px 'Space Mono'",
                letterSpacing: '.04em',
                cursor: 'pointer',
                ...seg(readView === v),
              }}
            >
              {v === 'html' ? 'HTML' : v === 'text' ? 'TEXT' : 'QUELLE'}
            </button>
          ))}
        </div>
      </div>

      <div style={{ flex: 1, minHeight: 0, overflow: 'auto', padding: 24 }}>
        {readView === 'html' && (
          <div style={{ maxWidth: 600, margin: '0 auto', background: '#fff', color: '#1a1a1a', border: '1px solid rgba(0,0,0,.12)', padding: '34px 36px', font: "400 14px/1.6 'Archivo'" }}>
            {detail.html ? (
              <iframe
                title="mail"
                sandbox=""
                srcDoc={detail.html}
                style={{ width: '100%', minHeight: 420, border: 'none', background: '#fff' }}
              />
            ) : (
              <pre style={{ margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-word', font: "400 14px/1.6 'Archivo'" }}>
                {detail.text || '(kein Inhalt)'}
              </pre>
            )}
            {detail.attachments.length > 0 && (
              <div style={{ marginTop: 20, display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                {detail.attachments.map((a) => (
                  <a
                    key={a.index}
                    href={api.attachmentUrl(detail.mailbox, detail.id, a.index)}
                    style={{ font: "400 11px 'Space Mono'", color: '#6E56CF', border: '1px solid rgba(0,0,0,.15)', padding: '4px 8px', textDecoration: 'none' }}
                  >
                    📎 {a.filename} ({Math.round((a.size || 0) / 1024)} KB)
                  </a>
                ))}
              </div>
            )}
            <div style={{ marginTop: 28, borderTop: '1px solid rgba(0,0,0,.1)', paddingTop: 14, font: "400 10.5px 'Space Mono'", color: 'rgba(0,0,0,.4)', lineHeight: 1.5 }}>
              ACMEsuite · Von ACMEmailtrap abgefangen — nicht real zugestellt.
            </div>
          </div>
        )}
        {readView === 'text' && (
          <pre style={{ margin: 0, font: "400 12.5px/1.7 'Space Mono'", color: 'var(--ink)', whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
            {detail.text || '(kein Text-Teil)'}
          </pre>
        )}
        {readView === 'raw' && (
          <pre style={{ margin: 0, font: "400 12px/1.6 'Space Mono'", color: 'var(--dim)', whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
            {rawText || '…'}
          </pre>
        )}
      </div>
    </div>
  )
}

function ghost(color: string) {
  return {
    padding: '7px 12px',
    border: '1px solid var(--line2)',
    background: 'transparent',
    color,
    font: "700 9.5px 'Space Mono'",
    letterSpacing: '.04em',
    cursor: 'pointer',
  } as const
}
