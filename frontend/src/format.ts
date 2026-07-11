// Presentation helpers mirroring the prototype's logic, adapted to real data.

export const ACCENT_DEFAULT = '#6E56CF'

const MOD_COLORS: Record<string, string> = {
  auth: '#E5322A',
  admin: '#E5322A',
  billing: '#E9AE06',
  supply: '#159E5B',
  crm: '#1358D8',
}

const MOD_TAGS: Record<string, string> = {
  auth: 'AUTH',
  admin: 'ADMIN',
  billing: 'BILLING',
  supply: 'SUPPLY',
  crm: 'CRM',
  core: 'CORE',
}

const TAG_COLORS: Record<string, string> = {
  RECV: '#159E5B',
  SMTP: '#1358D8',
  IMAP: '#6E56CF',
  WARN: '#E9AE06',
  ERR: '#E5322A',
}

export function modColor(mod: string, accent: string): string {
  return MOD_COLORS[mod] ?? accent
}

export function modTag(mod: string): string {
  return MOD_TAGS[mod] ?? 'CORE'
}

export function tagColor(tag: string, accent: string): string {
  if (tag === 'SEND') return accent
  return TAG_COLORS[tag] ?? 'var(--dim)'
}

/** Split a From header into a display name and bare address. */
export function parseFrom(from: string): { name: string; addr: string } {
  if (!from) return { name: '', addr: '' }
  const lt = from.indexOf('<')
  const gt = from.indexOf('>')
  if (lt >= 0 && gt > lt) {
    const addr = from.slice(lt + 1, gt).trim()
    let name = from.slice(0, lt).trim().replace(/^"|"$/g, '')
    if (!name) name = localPart(addr)
    return { name, addr }
  }
  return { name: localPart(from), addr: from.trim() }
}

function localPart(addr: string): string {
  const at = addr.indexOf('@')
  return at > 0 ? addr.slice(0, at) : addr
}

export function initials(name: string): string {
  const parts = (name || '')
    .replace(/[^A-Za-zÄÖÜäöü ]/g, '')
    .trim()
    .split(/\s+/)
    .filter(Boolean)
  const a = parts[0]?.[0] ?? '?'
  const b = parts[1]?.[0] ?? ''
  return (a + b).toUpperCase()
}

const WD = ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa']
const MO = ['Jan', 'Feb', 'Mär', 'Apr', 'Mai', 'Jun', 'Jul', 'Aug', 'Sep', 'Okt', 'Nov', 'Dez']

function pad(n: number): string {
  return n < 10 ? '0' + n : String(n)
}

/** Compact list-row time: HH:mm today, else "Do 17:30". */
export function fmtTime(ms: number): string {
  if (!ms) return ''
  const d = new Date(ms)
  const now = new Date()
  const sameDay =
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate()
  const hm = `${pad(d.getHours())}:${pad(d.getMinutes())}`
  return sameDay ? hm : `${WD[d.getDay()]} ${hm}`
}

/** Full header line: "Fr, 11 Jul 2026 09:42". */
export function fmtDateFull(ms: number): string {
  if (!ms) return ''
  const d = new Date(ms)
  return `${WD[d.getDay()]}, ${d.getDate()} ${MO[d.getMonth()]} ${d.getFullYear()} ${pad(
    d.getHours(),
  )}:${pad(d.getMinutes())}`
}

/** Log timestamp HH:mm:ss. */
export function fmtLogTime(ms: number): string {
  const d = new Date(ms)
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

export function fmtUptime(sec: number): string {
  if (sec < 60) return `${sec}s`
  if (sec < 3600) return `${Math.floor(sec / 60)}m`
  if (sec < 86400) return `${Math.floor(sec / 3600)}h`
  return `${Math.floor(sec / 86400)}d`
}
