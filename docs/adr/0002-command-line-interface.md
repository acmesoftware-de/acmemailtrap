# ADR 0002: Command line interface with contexts

- Status: accepted
- Date: 2026-07-17

## Context

ACMEmailtrap is driven through the web UI today. Its actual job, however, is to serve
*tests*: an ACMEsuite flow sends a mail, and something has to check that it arrived and
pull a confirmation link or a one-time code out of it. That job belongs in a shell
script or a CI stage, not in a browser.

A developer typically has more than one trap in play: one on `localhost`, one on a
shared dev host, one in CI. Every invocation would otherwise have to repeat the base
URL and the credentials.

The BOWL2 CLI already solved the same problem and its shape is proven, so we mirror it
rather than invent a second dialect:

- picocli, own Maven module, native binary.
- `~/.config/bowl2/config.yaml` (override via `BOWL2_CONFIG` or `-Dbowl2.config`),
  holding `currentContext`, a `contexts` map (kubectl style) and global `prefs`.
- Verbs `config get-contexts` / `set-context` / `use-context` / `set-pref` / `prefs`.
- Inherited options `--context`, `-o/--output table|json`, `--pretty` / `--plain`,
  `--color` / `--no-color`, `--insecure`.

## Decision

### 1. A `cli/` module, binary `acmemailtrap` with an `amt` alias

A new Maven module `cli/` in this repository, picocli, packaged as its own executable
jar. It is a **client** of the HTTP API and of the SMTP port; it never embeds the
server. The binary is named `acmemailtrap`; distributions additionally ship a short
`amt` alias, because the commands that matter are typed in loops and test scripts.

Startup latency is a feature here: `wait` and `extract` run inside test loops, where a
JVM start (roughly half a second or more) is felt. A GraalVM native image (about
20 ms) is therefore the intended distribution format, with the jar as the portable
fallback.

### 2. Contexts, mirroring BOWL2

`~/.config/acmemailtrap/config.json`, override via `ACMEMAILTRAP_CONFIG` or
`-Dacmemailtrap.config`. Same logical shape as BOWL2's config: `currentContext`,
`contexts`, `prefs`. The format is JSON rather than YAML so the CLI's only serialization
path carries no snakeyaml, which misresolves scalar types under a GraalVM native image
(slice 5); the shape is otherwise identical.

Per context:

| Field | Purpose |
|-------|---------|
| `url` | Base URL of the web/API endpoint (e.g. `http://127.0.0.1:8090`) |
| `smtp` | `host:port` of the SMTP receiver (default `<url-host>:1025`) |
| `imap` | `host:port` of the IMAP server (default `<url-host>:1143`) |
| `user`, `password` | Tool login, only when auth is enabled; blank for the default open localhost setup |
| `token` | Cached session/bearer, written by `login` |
| `insecureTls` | Skip certificate verification (self-signed dev host) |

Secrets stay deliberately plain-text-light, as in BOWL2: the token is short-lived, and
an OS keychain store is a follow-up cut. A context with a password is a shared-host
context; the common local one has no credentials at all. The config file is written
`0600`, since it may carry a password.

For CI, where writing a config file is friction, `ACMEMAILTRAP_URL` (plus `_USER`,
`_PASSWORD`, `_SMTP`, `_IMAP`, `_INSECURE_TLS`) forms an implicit context that is used
when no context is configured; `ACMEMAILTRAP_TOKEN` fills in a missing token.

Note for the login slice: the server currently offers only form login and OAuth2
(`SecurityConfig`), so a CLI has no way to authenticate against `/api/**` when auth is
enabled. Enabling `httpBasic()` for `/api/**` is the intended fix and is decided in
slice 4; the context and client plumbing is built for it from the start.

### 3. Command tree

| Group | Commands |
|-------|----------|
| Context | `config get-contexts` · `set-context <name> --url --smtp --imap --user --password [--insecure]` · `use-context <name>` · `delete-context <name>` · `set-pref` · `prefs` |
| Auth | `login` · `logout` (only meaningful when the tool login is enabled) |
| Overview | `status` · `version` · `logs [-f] [--limit] [--tag]` |
| Mailboxes | `mailbox ls` · `mailbox rm <address>` · `purge [--all]` |
| Messages | `msg ls <mailbox> [--unseen]` · `msg show <mailbox> <id> [--text\|--html]` · `msg raw <mailbox> <id>` · `msg open <mailbox> <id>` · `msg rm <mailbox> <id>` · `msg seen <mailbox> <id> [--unseen]` · `msg attach ls\|get <mailbox> <id> [<index>] [-o <file>]` |
| Send | `send --to --subject --text\|--html [--from] [--attach] [--via smtp\|api]` |
| Search | `search <query> [--limit]` |
| Forwarding | `forward providers` · `forward get` · `forward set --forwarder <id> --set k=v` · `forward send <mailbox> <id>` |
| Test support | `wait --to --subject --from [--timeout]` · `extract link\|code [--to --subject --pattern]` |
| Shell | `completion bash\|zsh\|fish` |

Every command supports `-o json` for `jq` pipelines; `msg raw` writes the `.eml` to
stdout unchanged, so it composes with existing mail tooling.

### 4. `wait` and `extract` are the point

A pure REST wrapper would add little over `curl`. The value of a CLI for a mail trap is
that it turns "a mail arrives eventually" into a synchronous, assertable step:

```sh
amt wait --to bob@kunde.test --subject 'Passwort' --timeout 30s
TOKEN=$(amt extract code --to bob@kunde.test)
URL=$(amt extract link --to bob@kunde.test --pattern '/reset/')
amt purge --all
```

- `wait` blocks until a matching message arrives, prints it (or its id under
  `-o json`) and exits 0; on timeout it exits 1 with no match. It is an assertion.
- `extract link|code` pulls a URL or a one-time code out of the matching message,
  bare on stdout, ready for command substitution. `--pattern` narrows it.
- `purge` resets the trap between test runs.

Exit codes are part of the contract: 0 success, 1 no match / assertion failed,
2 usage error, 3 connection or auth error against the trap.

### 5. `send` defaults to SMTP

`send` delivers through the SMTP receiver (port 1025) by default, not through
`/api/send`, because that exercises the real receive path an application would take.
`--via api` uses the composer endpoint instead (which also records a Sent copy). This
is why the context carries `smtp` alongside `url`.

### 6. Read paths use the HTTP API, not IMAP

The CLI reads through the HTTP API even though IMAP is available: the API already
exposes parsed messages, attachments, search and stats, while IMAP would mean
re-implementing a client for no gain. `imap` stays in the context as connection
information the CLI reports (`status`) and points other tools at.

## Consequences

- Anyone who knows the BOWL2 CLI knows this one: same context file shape, same verbs,
  same global flags. Two tools, one dialect.
- ACMEsuite mail flows become testable from a shell script or a CI stage without a
  browser and without an IMAP client.
- A second consumer of the HTTP API keeps that API honest; gaps show up as awkward
  commands.
- The native image adds a build path and its own reflection configuration to maintain.
  The jar stays the fallback so the CLI is never blocked on it.
- Contexts hold credentials in plain text (as BOWL2 does). Acceptable for a dev tool
  with short-lived tokens; a keychain backend can replace the storage later without
  touching call sites.

## Build slices

1. `cli/` module, picocli, `CliConfig` + `config` verbs (contexts, prefs), `status`,
   `version`, global flags, table/json output.
2. Core read/write: `mailbox ls|rm`, `purge`, `msg ls|show|raw`, `send`, `search`.
3. Test support: `wait`, `extract link|code`, exit-code contract.
4. Remainder: `login`/`logout`, `logs -f`, `msg open|rm|seen|attach`, `forward *`,
   `completion`.
5. GraalVM native image + `amt` alias. The native binary (`mvn -Pnative-image -pl cli`,
   built per OS/arch in CI and attached to the release) starts in ~20ms; the portable fat
   jar stays the fallback. `amt` is the same binary/jar under a short name. The server
   Docker image also ships the CLI jar with an `amt`/`acmemailtrap` wrapper and
   `ACMEMAILTRAP_URL`/`_SMTP` pre-pointed at the in-container trap, so
   `docker exec <container> amt wait --to …` works out of the box — a native compile is
   not baked into the multi-arch image (it would be QEMU-slow for arm64; the release
   binaries cover host use).
