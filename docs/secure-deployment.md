# Running ACMEmailtrap securely (systemd + ufw + nginx + TLS)

ACMEmailtrap is a testing tool. Two facts drive this whole guide:

- **SMTP (`:1025`) and IMAP (`:1143`) have no authentication by design.** Anyone who can
  reach them can inject mail and read every mailbox. **Never expose those ports to the
  public internet.** Keep them on loopback (or a private interface reachable only from the
  host running ACMEsuite).
- **The web UI/API can be gated** (local password or GitHub/GitLab OAuth) and should be
  put behind TLS.

The target setup: the app binds to loopback, a firewall denies everything by default,
nginx terminates TLS and reverse-proxies the UI, and ACMEmailtrap's own login protects it.

---

## 1. Run as a hardened systemd service

Create a dedicated unprivileged user and lay out the files:

```bash
sudo useradd --system --home /var/lib/acmemailtrap --shell /usr/sbin/nologin acmemailtrap
sudo mkdir -p /opt/acmemailtrap /var/lib/acmemailtrap/data /etc/acmemailtrap
sudo curl -Lo /opt/acmemailtrap/acmemailtrap.jar \
  https://github.com/acmesoftware-de/acmemailtrap/releases/latest/download/acmemailtrap.jar
sudo chown -R acmemailtrap:acmemailtrap /var/lib/acmemailtrap
```

Configuration via an environment file (Spring relaxed binding maps `A_B_C` to
`a.b.c`). Note that everything binds to `127.0.0.1`:

`/etc/acmemailtrap/acmemailtrap.env`

```ini
# Web UI: loopback only — nginx reaches it locally
SERVER_PORT=8090
SERVER_ADDRESS=127.0.0.1
# trust the reverse proxy's X-Forwarded-* (https scheme, real host) for
# correct OAuth redirect URIs and secure cookies
SERVER_FORWARD_HEADERS_STRATEGY=framework

# SMTP/IMAP: loopback only (ACMEsuite on the same host). For a remote ACMEsuite,
# bind to the private interface instead, e.g. 10.0.0.10, and open the port in ufw
# only for that host (see step 2).
ACMEMAILTRAP_SMTP_BIND=127.0.0.1
ACMEMAILTRAP_IMAP_BIND=127.0.0.1
ACMEMAILTRAP_DATA_DIR=/var/lib/acmemailtrap/data

# Gate the UI. Local password shown here; GitHub/GitLab OAuth is recommended for teams.
ACMEMAILTRAP_AUTH_ENABLED=true
ACMEMAILTRAP_AUTH_LOCAL_ENABLED=true
ACMEMAILTRAP_AUTH_LOCAL_USERNAME=admin
ACMEMAILTRAP_AUTH_LOCAL_PASSWORD=change-me
# GitHub example (callback registered as https://<host>/login/oauth2/code/github):
# ACMEMAILTRAP_AUTH_GITHUB_ENABLED=true
# ACMEMAILTRAP_AUTH_GITHUB_CLIENT_ID=...
# ACMEMAILTRAP_AUTH_GITHUB_CLIENT_SECRET=...
# ACMEMAILTRAP_AUTH_GITHUB_ALLOWED_ORGS=acmesoftware-de
```

```bash
sudo chmod 600 /etc/acmemailtrap/acmemailtrap.env
sudo chown acmemailtrap:acmemailtrap /etc/acmemailtrap/acmemailtrap.env
```

`/etc/systemd/system/acmemailtrap.service`

```ini
[Unit]
Description=ACMEmailtrap
After=network-online.target
Wants=network-online.target

[Service]
User=acmemailtrap
Group=acmemailtrap
WorkingDirectory=/var/lib/acmemailtrap
EnvironmentFile=/etc/acmemailtrap/acmemailtrap.env
ExecStart=/usr/bin/java -jar /opt/acmemailtrap/acmemailtrap.jar
Restart=on-failure
RestartSec=5

# Hardening
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
PrivateTmp=true
ReadWritePaths=/var/lib/acmemailtrap
CapabilityBoundingSet=
AmbientCapabilities=
RestrictAddressFamilies=AF_INET AF_INET6

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now acmemailtrap
sudo systemctl status acmemailtrap
```

Because it binds to `127.0.0.1`, nothing is reachable from outside yet — exactly what we
want before the firewall and proxy are in place.

---

## 2. Firewall with ufw

Default-deny, then open only what is needed. The trap ports are **not** opened.

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo ufw status verbose
```

**Remote ACMEsuite only:** if ACMEsuite runs on another host, bind SMTP to the private
interface (`ACMEMAILTRAP_SMTP_BIND=10.0.0.10` in the env file) and allow that single host:

```bash
sudo ufw allow from 10.0.0.5 to any port 1025 proto tcp   # ACMEsuite host -> SMTP
# same for 1143 if it needs IMAP read-back
```

Never `ufw allow 1025` (that opens the unauthenticated trap to the world).

---

## 3. nginx reverse proxy + TLS

`/etc/nginx/sites-available/acmemailtrap`

```nginx
server {
    listen 80;
    server_name mail-trap.example.com;
    location /.well-known/acme-challenge/ { root /var/www/certbot; }
    location / { return 301 https://$host$request_uri; }
}

server {
    listen 443 ssl;
    http2 on;
    server_name mail-trap.example.com;

    ssl_certificate     /etc/letsencrypt/live/mail-trap.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/mail-trap.example.com/privkey.pem;

    # allow large test messages (match acmemailtrap.smtp.max-message-size, 25 MiB)
    client_max_body_size 30m;

    location / {
        proxy_pass http://127.0.0.1:8090;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host  $host;
    }
}
```

```bash
sudo ln -s /etc/nginx/sites-available/acmemailtrap /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d mail-trap.example.com     # obtains + installs the certificate
```

The `X-Forwarded-Proto https` header plus `SERVER_FORWARD_HEADERS_STRATEGY=framework`
(step 1) make Spring build correct absolute URLs — the OAuth redirect becomes
`https://mail-trap.example.com/login/oauth2/code/github`. Register exactly that as the
callback URL in the GitHub/GitLab OAuth app.

---

## 4. Pointing ACMEsuite at the trap

- **Same host:** ACMEsuite sends to `127.0.0.1:1025` (SMTP, no auth, no TLS).
- **Remote host:** ACMEsuite sends to the private IP you bound in step 1
  (`10.0.0.10:1025`), reachable only because of the host-scoped ufw rule in step 2.

For reading mail back over IMAP, connect to the same host/interface on `:1143`; log in
with the recipient address as the username and any password (the mailbox is `INBOX`).

---

## Checklist

- [ ] App runs as the `acmemailtrap` system user, `Restart=on-failure`.
- [ ] `server.address`, `smtp.bind`, `imap.bind` are all loopback (or a private IP).
- [ ] `ufw status` shows default-deny incoming; only 22/80/443 open; 1025/1143 not public.
- [ ] nginx terminates TLS and forwards `X-Forwarded-Proto`; `forward-headers-strategy=framework`.
- [ ] `acmemailtrap.auth.enabled=true` with a real password or an OAuth allowlist.
- [ ] Secrets live in the root-owned, `chmod 600` env file — not in a committed config.
