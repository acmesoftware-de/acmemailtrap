import { useEffect, type CSSProperties } from 'react'
import { useStore } from './store'
import TopBar from './components/TopBar'
import ModuleHeader from './components/ModuleHeader'
import KpiBar from './components/KpiBar'
import MailboxesView from './components/MailboxesView'
import ServerView from './components/ServerView'
import ForwardingView from './components/ForwardingView'
import ComposerView from './components/ComposerView'
import Login from './components/Login'
import CommandPalette from './components/CommandPalette'
import Footer from './components/Footer'

export default function App() {
  const mode = useStore((s) => s.mode)
  const accent = useStore((s) => s.accent)
  const section = useStore((s) => s.section)
  const auth = useStore((s) => s.auth)
  const init = useStore((s) => s.init)

  useEffect(() => {
    void init()
  }, [init])

  useEffect(() => {
    const t = setInterval(() => {
      void useStore.getState().refresh()
    }, 3000)
    return () => clearInterval(t)
  }, [])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        const s = useStore.getState()
        s.setPalette(!s.paletteOpen)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  const rootStyle = {
    '--accent': accent,
    width: '100%',
    height: '100vh',
    display: 'flex',
    flexDirection: 'column',
    background: 'var(--bg)',
    color: 'var(--ink)',
    overflow: 'hidden',
  } as CSSProperties

  if (auth && auth.enabled && !auth.authenticated) {
    return <Login />
  }

  return (
    <div className="acme-app" data-mode={mode} style={rootStyle}>
      <TopBar />
      <ModuleHeader />
      <KpiBar />
      <div style={{ flex: 1, minHeight: 0, display: 'flex' }}>
        {section === 'MAIL' && <MailboxesView />}
        {section === 'SRV' && <ServerView />}
        {section === 'FWD' && <ForwardingView />}
        {section === 'NEW' && <ComposerView />}
      </div>
      <Footer />
      <CommandPalette />
    </div>
  )
}
