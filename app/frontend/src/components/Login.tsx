import { useStore } from '../store'

/**
 * Login screen shown when auth is enabled and nobody is signed in. OAuth providers are
 * full-page links to their Spring Security start URL; the local form posts to /login
 * (form login, CSRF disabled) and Spring redirects back to "/" on success.
 */
export default function Login() {
  const auth = useStore((s) => s.auth)
  const providers = auth?.providers ?? []
  const oauth = providers.filter((p) => p.kind === 'OAUTH')
  const local = providers.find((p) => p.kind === 'LOCAL')

  return (
    <div
      className="acme-app"
      data-mode="dark"
      style={{
        width: '100%',
        height: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'var(--bg)',
        color: 'var(--ink)',
      }}
    >
      <div style={{ width: 360, background: 'var(--panel)', border: '1px solid var(--line)', padding: '32px 32px 28px' }}>
        <div style={{ display: 'flex', alignItems: 'flex-end', gap: 2, height: 19 }}>
          {[{ h: 8, c: '#E5322A' }, { h: 13, c: '#E9AE06' }, { h: 19, c: '#1358D8' }, { h: 11, c: '#159E5B' }].map((b, i) => (
            <span key={i} style={{ width: 4, height: b.h, background: b.c }} />
          ))}
        </div>
        <div style={{ font: "700 14px 'Archivo'", letterSpacing: '.06em', marginTop: 10 }}>
          ACME<span style={{ color: 'var(--accent)', fontWeight: 500 }}>MAILTRAP</span>
        </div>
        <h1 style={{ font: "400 30px/0.95 'Anton'", margin: '14px 0 4px' }}>Anmelden</h1>
        <div style={{ font: "400 12px 'Archivo'", color: 'var(--dim)', marginBottom: 22 }}>
          Dieses Werkzeug ist geschützt. Melde dich an, um fortzufahren.
        </div>

        {oauth.map((p) => (
          <a
            key={p.id}
            href={p.loginUrl}
            style={{
              display: 'block',
              textAlign: 'center',
              textDecoration: 'none',
              marginBottom: 10,
              padding: '11px 14px',
              border: '1px solid var(--line2)',
              background: 'var(--panel2)',
              color: 'var(--ink)',
              font: "700 11px 'Space Mono'",
              letterSpacing: '.04em',
            }}
          >
            MIT {p.displayName.toUpperCase()} ANMELDEN
          </a>
        ))}

        {local && oauth.length > 0 && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, margin: '16px 0', color: 'var(--faint)', font: "400 10px 'Space Mono'" }}>
            <span style={{ flex: 1, height: 1, background: 'var(--line)' }} />
            ODER
            <span style={{ flex: 1, height: 1, background: 'var(--line)' }} />
          </div>
        )}

        {local && (
          <form action="/login" method="post">
            <input
              name="username"
              placeholder="Benutzername"
              autoComplete="username"
              style={inputStyle}
            />
            <input
              name="password"
              type="password"
              placeholder="Passwort"
              autoComplete="current-password"
              style={{ ...inputStyle, marginTop: 10 }}
            />
            <button
              type="submit"
              style={{
                width: '100%',
                marginTop: 14,
                padding: '11px 14px',
                border: '1px solid var(--accent)',
                background: 'var(--accent)',
                color: '#fff',
                font: "700 11px 'Space Mono'",
                letterSpacing: '.06em',
                cursor: 'pointer',
              }}
            >
              ANMELDEN
            </button>
          </form>
        )}

        {providers.length === 0 && (
          <div style={{ font: "400 12px/1.6 'Archivo'", color: 'var(--dim)' }}>
            Es ist keine Anmeldemethode konfiguriert. Prüfe <code>acmemailtrap.auth.*</code>.
          </div>
        )}
      </div>
    </div>
  )
}

const inputStyle = {
  width: '100%',
  border: '1px solid var(--line2)',
  background: 'var(--bg)',
  color: 'var(--ink)',
  padding: '11px 12px',
  font: "400 13px 'Archivo'",
} as const
