# ADR 0001: Forwarding plugins and tool authentication

- Status: accepted
- Date: 2026-07-11

## Context

ACMEmailtrap forwards caught mail to a real service (Teil 6). Today that is a single
hardcoded SMTP path (`ForwardingService` + `ForwardSettings`). We need more than one
delivery channel — SMTP with TLS and Microsoft 365 (Graph) are the minimum, and a
developer tool benefits from a few more (webhook, Google, transactional HTTP APIs).

Separately, ACMEmailtrap is a developer tool. It runs open on `localhost` today. When
it is shared with a team it needs a login, and that login should use the accounts
developers already have: an internal password, or GitHub / (self-hosted) GitLab.

We want the same shape as ACMEsuite so the codebase feels familiar:

- `AuthProvider` SPI: `id()` / `displayName()` / `kind()` / `configSchema()`; kinds
  `LOCAL` and `OIDC`; concrete `LocalAuthProvider`, `OidcAuthProvider`,
  `EntraAuthProvider`; config rendered generically from a `ConfigField` schema;
  secret fields stored envelope-encrypted; **the role is always assigned locally, never
  derived from the provider**.
- `EntraGraphClient` (thin `RestClient` + Bearer) with an `EntraTokenSource` /
  `EntraTokenProvider` (OAuth2 client-credentials) behind an interface, so the HTTP
  boundary is deterministically testable.

## Decision

### 1. Forwarding becomes a `MailForwarder` plugin SPI

Mirror `AuthProvider`. A forwarder is a Spring bean discovered into a registry:

```java
interface MailForwarder {
    String id();                       // stable, e.g. "smtp", "graph", "webhook"
    String displayName();              // shown in the Weiterleitung UI
    ForwarderKind kind();              // SMTP | HTTP_API | GRAPH | GOOGLE | WEBHOOK
    List<ConfigField> configSchema();  // UI renders settings generically; secrets flagged
    ForwardResult send(RawMessage message, List<String> recipients, ForwarderConfig cfg);
}
```

- `ConfigField` carries `key`, `label`, `type` (text | number | password | select | url),
  `secret` flag and optional options — reused by both forwarders and auth providers.
- The current SMTP logic becomes the first `MailForwarder` (`SmtpForwarder`); the
  `ForwardingService` shrinks to a router: pick the configured forwarder per rule and
  call `send(...)` off the request thread.
- Routing stays per mailbox (the Weiterleitung view already has per-mailbox chips):
  a rule maps a mailbox (or "all") to one configured forwarder instance. The
  configuration is persisted next to the maildir (JSON), secret fields encrypted.
- HTTP boundaries (Graph, Google, transactional APIs, webhook) sit behind small
  interfaces with a `RestClient`, exactly like `EntraGraphClient`, so they are testable
  with a canned client and token source.

### 2. Provider roster

| Tier | Forwarder | Notes |
|------|-----------|-------|
| minimum | **SMTP (STARTTLS/SSL)** | universal; also covers Postmark/SES/Mailgun SMTP and self-hosted MTAs via config |
| minimum | **Microsoft 365 / Graph** | `POST /users/{id}/sendMail`, OAuth2 client-credentials (mirrors `EntraTokenProvider`) |
| recommended | **Webhook / HTTP relay** | POST raw MIME or JSON to a URL; the dev-native primitive (pipe into other test systems/CI); optional HMAC signing |
| recommended | **Google Workspace / Gmail API** | `users.messages.send` (base64url raw), OAuth2 (service account with domain-wide delegation, or refresh token) |
| family | **HTTP transactional API** | one forwarder with a small adapter per vendor: **Postmark**, **SendGrid**, **Amazon SES (SESv2)**. Shared base URL + auth header + payload shape |
| niche (later) | IMAP-APPEND, Null/Log sink, Maildir export | round-trips through a real store, side-effect-free load tests, feed other tools |

In scope for the first build: SMTP, Graph, Webhook, Gmail, and the HTTP-API family.
Niche forwarders are deferred.

### 3. Two authentication axes (kept separate)

- **Upstream credentials** — per forwarder (SMTP user/pass, Graph tenant/client/secret,
  Gmail OAuth, API keys, webhook HMAC). These are `ConfigField`s with `secret=true`,
  stored encrypted. This is *not* the tool login.
- **Tool login** — who may use ACMEmailtrap. Mirror `AuthProvider`:
  - `LOCAL` — a single dev password (config/in-memory). Default is **open on
    `localhost`**; login is opt-in for shared/team deployments.
  - `GITHUB` (OIDC/OAuth2) — access gated by an **org/team allowlist**.
  - `GITLAB` (OIDC/OAuth2) — configurable issuer/base URL so it works against the
    self-hosted GitLab (mygitlab.net / code.processq.io); access gated by a **group
    allowlist**.
  - Authorization is always **local** (an allowlist); it is never derived blindly from
    the provider — same principle as ACMEsuite. Implemented with Spring Security's
    OAuth2 client for the redirect flows.

### 4. Secret handling

Secret `ConfigField`s (forwarder credentials, OAuth client secrets, HMAC keys) are
stored envelope-encrypted in the config JSON under the data dir. The wrapping key comes
from an env var / local key file for the standalone dev tool; an external KMS/OpenBao
backend can replace it later without touching call sites. Secrets are never returned by
the API (masked, blank-on-write-keeps-existing — as `/api/forward` already does).

## Consequences

- One consistent extension point for delivery; adding a channel is a new bean, no
  router changes. The Weiterleitung UI renders any forwarder from its `configSchema`.
- Delivery and tool-access concerns are cleanly separated.
- The tool stays zero-friction locally (open on `localhost`) while supporting real
  team auth via existing GitHub/GitLab accounts.
- OAuth flows (Graph, Gmail, GitHub, GitLab) need real credentials to exercise
  end-to-end; unit tests cover the HTTP/token boundary with canned clients, and the
  wiring is verified with mocked providers.

## Build slices

1. `MailForwarder` SPI + `ConfigField` + registry; refactor SMTP into it; **Webhook**.
2. **Graph** and **Gmail** forwarders (OAuth2 token sources behind interfaces).
3. **HTTP transactional** family (Postmark / SendGrid / SES adapters).
4. Weiterleitung UI: pick a forwarder per rule, render its schema.
5. Tool login: `LOCAL` + **GitHub** + **GitLab** (Spring Security OAuth2), local allowlist.
