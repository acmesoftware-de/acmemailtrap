import { useEffect, useMemo, useRef, useState } from 'react'
import { useStore } from '../store'
import { api } from '../api'
import { fmtTime, modColor, parseFrom } from '../format'
import type { SearchResults } from '../types'

type Item =
  | { kind: 'mb'; address: string }
  | { kind: 'msg'; mailbox: string; id: string; from: string; subject: string; mod: string; receivedAt: number }

const empty: SearchResults = { mailboxes: [], messages: [] }

export default function CommandPalette() {
  const open = useStore((s) => s.paletteOpen)
  const accent = useStore((s) => s.accent)
  const setPalette = useStore((s) => s.setPalette)
  const setSection = useStore((s) => s.setSection)
  const selectMailbox = useStore((s) => s.selectMailbox)
  const selectMessage = useStore((s) => s.selectMessage)

  const [q, setQ] = useState('')
  const [res, setRes] = useState<SearchResults>(empty)
  const [active, setActive] = useState(0)
  const inputRef = useRef<HTMLInputElement>(null)

  // Reset + focus on open.
  useEffect(() => {
    if (open) {
      setQ('')
      setRes(empty)
      setActive(0)
      setTimeout(() => inputRef.current?.focus(), 0)
    }
  }, [open])

  // Debounced search.
  useEffect(() => {
    if (!open) return
    const term = q.trim()
    if (!term) {
      setRes(empty)
      return
    }
    const t = setTimeout(() => {
      api
        .search(term)
        .then((r) => {
          setRes(r)
          setActive(0)
        })
        .catch(() => setRes(empty))
    }, 120)
    return () => clearTimeout(t)
  }, [q, open])

  const items: Item[] = useMemo(() => {
    const mb: Item[] = res.mailboxes.map((m) => ({ kind: 'mb', address: m.address }))
    const msg: Item[] = res.messages.map((m) => ({
      kind: 'msg',
      mailbox: m.mailbox,
      id: m.id,
      from: m.from,
      subject: m.subject,
      mod: m.mod,
      receivedAt: m.receivedAt,
    }))
    return [...mb, ...msg]
  }, [res])

  const choose = async (item: Item) => {
    setSection('MAIL')
    if (item.kind === 'mb') {
      await selectMailbox(item.address)
    } else {
      await selectMailbox(item.mailbox)
      await selectMessage(item.id)
    }
    setPalette(false)
  }

  if (!open) return null

  const onKey = (e: React.KeyboardEvent) => {
    if (e.key === 'Escape') {
      setPalette(false)
    } else if (e.key === 'ArrowDown') {
      e.preventDefault()
      setActive((a) => Math.min(a + 1, items.length - 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setActive((a) => Math.max(a - 1, 0))
    } else if (e.key === 'Enter' && items[active]) {
      e.preventDefault()
      void choose(items[active])
    }
  }

  const mbCount = res.mailboxes.length

  return (
    <div
      onClick={() => setPalette(false)}
      style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,.5)', display: 'flex', justifyContent: 'center', alignItems: 'flex-start', paddingTop: '10vh', zIndex: 50 }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        onKeyDown={onKey}
        style={{ width: 'min(640px, 92vw)', background: 'var(--panel)', border: '1px solid var(--line2)', boxShadow: '0 24px 70px rgba(0,0,0,.5)' }}
      >
        <input
          ref={inputRef}
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Postfächer und Nachrichten durchsuchen…"
          style={{ width: '100%', padding: '16px 18px', border: 'none', borderBottom: '1px solid var(--line)', background: 'transparent', color: 'var(--ink)', font: "400 15px 'Archivo'", outline: 'none' }}
        />
        <div style={{ maxHeight: '52vh', overflow: 'auto' }}>
          {items.length === 0 && q.trim() && (
            <div style={{ padding: '18px', font: "400 12px 'Archivo'", color: 'var(--faint)' }}>Keine Treffer.</div>
          )}
          {!q.trim() && (
            <div style={{ padding: '18px', font: "400 12px 'Archivo'", color: 'var(--faint)' }}>
              Nach Absender, Betreff, Inhalt oder Postfach suchen.
            </div>
          )}

          {res.mailboxes.length > 0 && <Group label="POSTFÄCHER" />}
          {res.mailboxes.map((m, i) => (
            <Row key={'mb-' + m.folder} active={active === i} onHover={() => setActive(i)} onClick={() => void choose(items[i])}>
              <span style={{ width: 8, height: 8, borderRadius: '50%', background: m.unseen > 0 ? 'var(--accent)' : 'var(--line2)', flex: 'none' }} />
              <span style={{ font: "600 13px 'Archivo'", color: 'var(--ink)' }}>{m.address}</span>
              <span style={{ marginLeft: 'auto', font: "400 10px 'Space Mono'", color: 'var(--faint)' }}>{m.total} Nachr.</span>
            </Row>
          ))}

          {res.messages.length > 0 && <Group label="NACHRICHTEN" />}
          {res.messages.map((m, i) => {
            const idx = mbCount + i
            return (
              <Row key={'msg-' + m.mailbox + m.id} active={active === idx} onHover={() => setActive(idx)} onClick={() => void choose(items[idx])}>
                <span style={{ width: 8, height: 8, borderRadius: '2px', background: modColor(m.mod, accent), flex: 'none' }} />
                <div style={{ minWidth: 0, flex: 1 }}>
                  <div style={{ font: "600 12.5px 'Archivo'", color: 'var(--ink)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                    {m.subject || '(kein Betreff)'}
                  </div>
                  <div style={{ font: "400 11px 'Space Mono'", color: 'var(--faint)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                    {parseFrom(m.from).name} · {m.mailbox}
                  </div>
                </div>
                <span style={{ marginLeft: 'auto', font: "400 10px 'Space Mono'", color: 'var(--faint)', flex: 'none' }}>{fmtTime(m.receivedAt)}</span>
              </Row>
            )
          })}
        </div>
      </div>
    </div>
  )
}

function Group({ label }: { label: string }) {
  return (
    <div style={{ padding: '10px 18px 4px', font: "700 9px 'Space Mono'", letterSpacing: '.08em', color: 'var(--faint)' }}>{label}</div>
  )
}

function Row({ active, onHover, onClick, children }: { active: boolean; onHover: () => void; onClick: () => void; children: React.ReactNode }) {
  return (
    <div
      onMouseEnter={onHover}
      onClick={onClick}
      style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '10px 18px', cursor: 'pointer', background: active ? 'var(--chip)' : 'transparent', borderLeft: `3px solid ${active ? 'var(--accent)' : 'transparent'}` }}
    >
      {children}
    </div>
  )
}
