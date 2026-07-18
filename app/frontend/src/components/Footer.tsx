import { useStore } from '../store'

/**
 * Slim footer bar: build identity (version and commit, branded from /api/version) on the left,
 * copyright and vendor link on the right.
 */
export default function Footer() {
  const build = useStore((s) => s.build)

  return (
    <footer
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 12,
        flexWrap: 'wrap',
        height: 28,
        padding: '0 18px',
        borderTop: '1px solid var(--line)',
        background: 'var(--panel)',
        font: "10px 'Space Mono'",
        color: 'var(--faint)',
        whiteSpace: 'nowrap',
      }}
    >
      <span title={build ? `${build.branch}${build.buildTime ? ' · ' + new Date(build.buildTime).toLocaleString('de-DE') : ''}` : undefined}>
        {build ? <>v{build.version} · {build.commit}</> : ' '}
      </span>
      <span>
        &copy; 2026 AFINA GmbH.{' '}
        <a
          href="https://www.acmesoftware.de"
          target="_blank"
          rel="noopener noreferrer"
          style={{ color: 'var(--accent)', textDecoration: 'none' }}
        >
          www.acmesoftware.de
        </a>
      </span>
    </footer>
  )
}
