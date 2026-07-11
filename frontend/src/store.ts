import { create } from 'zustand'
import { api } from './api'
import { ACCENT_DEFAULT } from './format'
import type {
  BuildInfo,
  ForwardConfig,
  ForwarderProvider,
  Filter,
  LogEntry,
  MailboxInfo,
  MessageDetail,
  MessageMeta,
  Mode,
  ReadView,
  Section,
  ServerInfo,
  Stats,
} from './types'

interface Comp {
  from: string
  to: string
  subject: string
  body: string
}

interface FwdForm {
  enabled: boolean
  forwarderId: string
  mailboxes: string[]
  values: Record<string, string>
  flash: string
}

interface State {
  mode: Mode
  accent: string
  section: Section
  readView: ReadView
  filter: Filter

  mailboxes: MailboxInfo[]
  currentMailbox: string | null
  messages: MessageMeta[]
  currentMessageId: string | null
  detail: MessageDetail | null
  rawText: string

  stats: Stats | null
  server: ServerInfo | null
  logs: LogEntry[]
  forward: ForwardConfig | null
  forwarders: ForwarderProvider[]
  build: BuildInfo | null

  comp: Comp
  compFlash: string
  fwd: FwdForm

  init: () => Promise<void>
  refresh: () => Promise<void>
  setSection: (s: Section) => void
  toggleTheme: () => void
  setFilter: (f: Filter) => void
  setReadView: (v: ReadView) => Promise<void>
  selectMailbox: (addr: string) => Promise<void>
  selectMessage: (id: string) => Promise<void>
  deleteMessage: () => Promise<void>
  forwardMessage: () => Promise<void>

  openComposer: () => void
  discardComposer: () => void
  setComp: (k: keyof Comp, v: string) => void
  sendMail: () => Promise<void>

  loadForward: () => Promise<void>
  setFwd: (patch: Partial<FwdForm>) => void
  setForwarderId: (id: string) => void
  setValue: (key: string, value: string) => void
  toggleFwdBox: (addr: string) => void
  saveForward: () => Promise<void>
}

const emptyComp: Comp = { from: 'no-reply@acme.test', to: '', subject: '', body: '' }

export const useStore = create<State>((set, get) => ({
  mode: 'dark',
  accent: ACCENT_DEFAULT,
  section: 'MAIL',
  readView: 'html',
  filter: 'alle',

  mailboxes: [],
  currentMailbox: null,
  messages: [],
  currentMessageId: null,
  detail: null,
  rawText: '',

  stats: null,
  server: null,
  logs: [],
  forward: null,
  forwarders: [],
  build: null,

  comp: emptyComp,
  compFlash: '',
  fwd: {
    enabled: false,
    forwarderId: 'smtp',
    mailboxes: [],
    values: {},
    flash: '',
  },

  init: async () => {
    try {
      set({ build: await api.version() })
    } catch {
      // build info is best-effort
    }
    await get().refresh()
    const boxes = get().mailboxes
    const first = boxes.find((b) => !b.sent) ?? boxes[0]
    if (first && !get().currentMailbox) {
      await get().selectMailbox(first.address)
    }
    await get().loadForward()
  },

  refresh: async () => {
    try {
      const [mailboxes, stats] = await Promise.all([api.mailboxes(), api.stats()])
      set({ mailboxes, stats })
      const { section, currentMailbox } = get()
      if (section === 'MAIL' && currentMailbox) {
        set({ messages: await api.messages(currentMailbox) })
      }
      if (section === 'SRV') {
        const [server, logs] = await Promise.all([api.server(), api.logs(120)])
        set({ server, logs })
      }
    } catch {
      // transient; next tick retries
    }
  },

  setSection: (s) => {
    set({ section: s })
    void get().refresh()
  },

  toggleTheme: () => set((st) => ({ mode: st.mode === 'dark' ? 'light' : 'dark' })),

  setFilter: (f) => set({ filter: f }),

  setReadView: async (v) => {
    set({ readView: v })
    if (v === 'raw' && !get().rawText) {
      const { currentMailbox, currentMessageId } = get()
      if (currentMailbox && currentMessageId) {
        set({ rawText: await api.rawText(currentMailbox, currentMessageId) })
      }
    }
  },

  selectMailbox: async (addr) => {
    set({ currentMailbox: addr, currentMessageId: null, detail: null, rawText: '' })
    const messages = await api.messages(addr)
    set({ messages })
    if (messages[0]) {
      await get().selectMessage(messages[0].id)
    }
  },

  selectMessage: async (id) => {
    const mb = get().currentMailbox
    if (!mb) return
    const detail = await api.message(mb, id)
    set({ currentMessageId: id, detail, readView: 'html', rawText: '' })
    if (!detail.seen) {
      await api.markSeen(mb, id, true).catch(() => {})
      const [mailboxes, messages, stats] = await Promise.all([
        api.mailboxes(),
        api.messages(mb),
        api.stats(),
      ])
      set({ mailboxes, messages, stats })
    }
  },

  deleteMessage: async () => {
    const { currentMailbox, currentMessageId } = get()
    if (!currentMailbox || !currentMessageId) return
    await api.deleteMessage(currentMailbox, currentMessageId).catch(() => {})
    set({ currentMessageId: null, detail: null, rawText: '' })
    const [mailboxes, messages, stats] = await Promise.all([
      api.mailboxes(),
      api.messages(currentMailbox),
      api.stats(),
    ])
    set({ mailboxes, messages, stats })
    if (messages[0]) await get().selectMessage(messages[0].id)
  },

  forwardMessage: async () => {
    const { currentMailbox, currentMessageId } = get()
    if (!currentMailbox || !currentMessageId) return
    await api.forwardMessage(currentMailbox, currentMessageId).catch(() => {})
    if (get().section === 'SRV') void get().refresh()
  },

  openComposer: () => set({ section: 'NEW', compFlash: '' }),
  discardComposer: () => set({ section: 'MAIL', comp: emptyComp, compFlash: '' }),
  setComp: (k, v) => set((st) => ({ comp: { ...st.comp, [k]: v }, compFlash: '' })),

  sendMail: async () => {
    const c = get().comp
    const to = c.to
      .split(',')
      .map((s) => s.trim().toLowerCase())
      .filter(Boolean)
    if (to.length === 0 || !c.subject.trim()) {
      set({ compFlash: 'Empfänger und Betreff erforderlich' })
      return
    }
    await api.sendMail({ from: c.from, to, subject: c.subject.trim(), text: c.body })
    set({ comp: emptyComp, section: 'MAIL', filter: 'alle' })
    await get().refresh()
    await get().selectMailbox(to[0])
  },

  loadForward: async () => {
    try {
      const [f, providers] = await Promise.all([api.forward(), api.forwarders()])
      set({
        forward: f,
        forwarders: providers,
        fwd: {
          enabled: f.enabled,
          forwarderId: f.forwarderId,
          mailboxes: f.mailboxes,
          values: f.values ?? {},
          flash: '',
        },
      })
    } catch {
      // ignore
    }
  },

  setFwd: (patch) => set((st) => ({ fwd: { ...st.fwd, ...patch, flash: '' } })),

  setForwarderId: (id) =>
    set((st) => ({ fwd: { ...st.fwd, forwarderId: id, values: {}, flash: '' } })),

  setValue: (key, value) =>
    set((st) => ({ fwd: { ...st.fwd, values: { ...st.fwd.values, [key]: value }, flash: '' } })),

  toggleFwdBox: (addr) =>
    set((st) => {
      const has = st.fwd.mailboxes.includes(addr)
      return {
        fwd: {
          ...st.fwd,
          flash: '',
          mailboxes: has
            ? st.fwd.mailboxes.filter((b) => b !== addr)
            : [...st.fwd.mailboxes, addr],
        },
      }
    }),

  saveForward: async () => {
    const f = get().fwd
    await api.saveForward({
      enabled: f.enabled,
      forwarderId: f.forwarderId,
      mailboxes: f.mailboxes,
      values: f.values,
    })
    await get().loadForward()
    set((st) => ({ fwd: { ...st.fwd, flash: 'Gespeichert' } }))
  },
}))
