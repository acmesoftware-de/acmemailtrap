# ACMEmailtrap

A self-contained email trap for testing the mail flows of **ACMEsuite** (and anything
else that sends or reads mail) without touching real email. It catches SMTP, stores
every message per recipient on disk, serves those mailboxes over IMAP and a web UI,
lets you send test messages, and can optionally forward to a real mail service.

It is intentionally small and dependency-light: one Spring Boot process, no external
message broker, no database, no CDN. The on-disk store is plain files you can inspect.

## What it does

1. **Catch SMTP** - a permissive SMTP server accepts mail from any client for any
   recipient (no auth, every recipient accepted).
2. **Store per recipient** - each caught message is written into one folder per
   envelope recipient, as raw `.eml` plus a small JSON index sidecar.
3. **Serve IMAP** - a minimal IMAP4rev1 server lets another tool read the mailboxes
   back (LOGIN, LIST, SELECT, FETCH incl. ENVELOPE and BODY, SEARCH, STORE \Seen).
4. **Web UI** - a three-pane inbox (mailboxes / messages / detail) sorted by recipient
   mailbox, with HTML and text views, attachments, and raw source.
5. **Send** - compose and "send" a message; it is delivered straight into the
   recipients' mailboxes (which show up as new mailboxes) and can be relayed onward.
6. **Forward** - optionally relay every caught message to a real SMTP service, so a
   test can also verify that mail leaves the building.

## Architecture

The **filesystem is the single source of truth.** Layout under `data-dir`:

```
data/
  bob@kunde.test/
    .address                 canonical recipient address
    1720000000000-ab12cd34.eml   raw RFC 822 message
    1720000000000-ab12cd34.json  derived index (subject, from, seen, ...)
```

The `.eml` is authoritative; the `.json` is a regenerable cache. Components:

- `smtp/SmtpReceiver` - hand-rolled catch-all SMTP server (Teil 1).
- `store/MailStore` - the maildir-style store (Teil 2).
- `imap/ImapServer` + `ImapSession` - the IMAP server over the same store (Teil 3).
- `web/MailApiController` + `resources/static` - REST API and vanilla-JS UI (Teil 4).
- `smtp/MailSender` - compose and deliver into the trap (Teil 5).
- `smtp/ForwardingService` - optional relay to a real SMTP service (Teil 6).

SMTP and IMAP are hand-rolled (small socket servers) on purpose: it keeps the
dependency surface to Spring Boot plus Jakarta Mail (used only for MIME parsing,
composing and outbound relay), and avoids the javax/jakarta split of older libraries.

## Running

Requirements: JDK 21+ and Maven.

```
mvn spring-boot:run
```

Then open the web UI and point ACMEsuite at the SMTP port:

| Surface | Default            |
|---------|--------------------|
| Web UI  | http://localhost:8090 |
| SMTP    | localhost:2525     |
| IMAP    | localhost:1143     |

Send a quick test message from the shell:

```
printf 'From: alice@acmesuite.test\r\nTo: bob@kunde.test\r\nSubject: Hello\r\n\r\nBody\r\n' \
  | curl -s --url 'smtp://localhost:2525' \
    --mail-from alice@acmesuite.test --mail-rcpt bob@kunde.test --upload-file -
```

It appears in the UI under the `bob@kunde.test` mailbox and is readable over IMAP
(log in as `bob@kunde.test` with any password; the mailbox is `INBOX`).

## Configuration

All settings live under `acmemailtrap.*` (see `src/main/resources/application.yml`)
and can be overridden with environment variables or CLI args.

```yaml
acmemailtrap:
  data-dir: ./data
  smtp:   { enabled: true, bind: 0.0.0.0, port: 2525, max-message-size: 26214400 }
  imap:   { enabled: true, bind: 0.0.0.0, port: 1143 }
  forward:                        # Teil 6, off by default
    enabled: false
    host: smtp.example.com
    port: 587
    username: ""
    password: ""
    starttls: true
    recipient-domains: []         # empty = forward all; else only these domains
```

## Limitations

This is a testing tool, not a production mail server. It requires no authentication,
accepts every recipient, and its IMAP server implements a pragmatic read-mostly subset
(UID equals sequence number within a snapshot, constant UIDVALIDITY, single-part
BODYSTRUCTURE). Do not expose it to untrusted networks.

## License

Apache-2.0. See [LICENSE](LICENSE).
