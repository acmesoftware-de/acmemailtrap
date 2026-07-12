import type {
  AuthState,
  BuildInfo,
  ForwardConfig,
  ForwarderProvider,
  LogEntry,
  MailboxInfo,
  MessageDetail,
  MessageMeta,
  SearchResults,
  ServerInfo,
  Stats,
} from './types'

const enc = encodeURIComponent

async function get<T>(path: string): Promise<T> {
  const res = await fetch(path)
  if (!res.ok) throw new Error(`GET ${path} -> ${res.status}`)
  return res.json() as Promise<T>
}

async function send<T>(method: string, path: string, body?: unknown): Promise<T | null> {
  const res = await fetch(path, {
    method,
    headers: body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })
  if (!res.ok) throw new Error(`${method} ${path} -> ${res.status}`)
  return res.status === 204 ? null : ((await res.json()) as T)
}

export const api = {
  mailboxes: () => get<MailboxInfo[]>('/api/mailboxes'),
  messages: (mb: string) => get<MessageMeta[]>(`/api/mailboxes/${enc(mb)}/messages`),
  message: (mb: string, id: string) => get<MessageDetail>(`/api/mailboxes/${enc(mb)}/messages/${enc(id)}`),
  rawUrl: (mb: string, id: string) => `/api/mailboxes/${enc(mb)}/messages/${enc(id)}/raw`,
  attachmentUrl: (mb: string, id: string, i: number) =>
    `/api/mailboxes/${enc(mb)}/messages/${enc(id)}/attachments/${i}`,
  rawText: async (mb: string, id: string) => {
    const res = await fetch(`/api/mailboxes/${enc(mb)}/messages/${enc(id)}/raw`)
    return res.ok ? res.text() : ''
  },
  markSeen: (mb: string, id: string, seen: boolean) =>
    send<void>('POST', `/api/mailboxes/${enc(mb)}/messages/${enc(id)}/seen`, { seen }),
  deleteMessage: (mb: string, id: string) =>
    send<void>('DELETE', `/api/mailboxes/${enc(mb)}/messages/${enc(id)}`),
  forwardMessage: (mb: string, id: string) =>
    send<{ relayed: boolean }>('POST', `/api/mailboxes/${enc(mb)}/messages/${enc(id)}/forward`),
  sendMail: (body: { from: string; to: string[]; subject: string; text: string }) =>
    send<{ id: string }>('POST', '/api/send', body),
  stats: () => get<Stats>('/api/stats'),
  version: () => get<BuildInfo>('/api/version'),
  auth: () => get<AuthState>('/api/auth'),
  search: (q: string) => get<SearchResults>(`/api/search?q=${enc(q)}`),
  server: () => get<ServerInfo>('/api/server'),
  logs: (limit = 100) => get<LogEntry[]>(`/api/logs?limit=${limit}`),
  forward: () => get<ForwardConfig>('/api/forward'),
  forwarders: () => get<ForwarderProvider[]>('/api/forward/providers'),
  saveForward: (body: ForwardConfig) => send<ForwardConfig>('PUT', '/api/forward', body),
}
