export interface MailboxInfo {
  address: string
  folder: string
  total: number
  unseen: number
  lastReceivedAt: number
  sent: boolean
  label: string | null
}

export interface MessageMeta {
  id: string
  from: string
  subject: string
  receivedAt: number
  size: number
  seen: boolean
  recipients: string[]
  mod: string
  snippet: string
}

export interface Attachment {
  index: number
  filename: string
  contentType: string
  size: number
}

export interface MessageDetail {
  id: string
  mailbox: string
  from: string
  subject: string
  receivedAt: number
  size: number
  seen: boolean
  recipients: string[]
  mod: string
  text: string
  html: string
  attachments: Attachment[]
}

export interface Stats {
  messages: number
  mailboxes: number
  unread: number
  today: number
}

export interface ServerInfo {
  smtp: { port: number; up: boolean; uptimeSec: number; received: number; errors: number }
  imap: { port: number; up: boolean; uptimeSec: number; sessions: number; folders: number }
  connections: number
  throughputPerMin: number
  forwards: number
}

export interface LogEntry {
  time: number
  tag: string
  msg: string
}

export interface ForwardConfig {
  enabled: boolean
  host: string
  port: number
  username: string
  hasPassword: boolean
  tls: string
  mailboxes: string[]
}

export interface BuildInfo {
  version: string
  commit: string
  branch: string
  buildTime: number | null
  label: string
}

export type Section = 'MAIL' | 'SRV' | 'FWD' | 'NEW'
export type ReadView = 'html' | 'text' | 'raw'
export type Filter = 'alle' | 'unread'
export type Mode = 'dark' | 'light'
