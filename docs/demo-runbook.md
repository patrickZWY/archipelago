# Archipelago Demo Runbook

This is the exact local-to-online flow for the Archipelago demo frontdoor:

- Public frontdoor: `https://archipelago-demo.zhengwangyuan-patrick.com/`
- Live tunnel hostname: `https://live-archipelago-demo.zhengwangyuan-patrick.com/`
- Local app origin: `http://127.0.0.1:8080`
- Cloudflare tunnel name: `archipelago-demo`

## Start the Demo

From the Archipelago repo:

```bash
cd /Users/zhengwangyuan/repos/archipelago
docker compose up -d
scripts/run-demo.sh
```

Keep that terminal open. It builds the Vite frontend, starts Spring Boot on
`127.0.0.1:8080`, enables secure session cookies, and disables outbound mail
unless explicitly configured. Persistent signup defaults to `approval`; the
public `Enter demo` flow remains open.

In a second terminal:

```bash
cd /Users/zhengwangyuan/repos/archipelago
scripts/run-demo-tunnel.sh
```

Keep that terminal open too. It connects the `archipelago-demo` Cloudflare
tunnel to `http://127.0.0.1:8080`.

## Open the Online Demo

Open:

```text
https://archipelago-demo.zhengwangyuan-patrick.com/
```

Quick smoke test:

```bash
curl -I http://127.0.0.1:8080/
curl -I https://live-archipelago-demo.zhengwangyuan-patrick.com/
curl -I https://archipelago-demo.zhengwangyuan-patrick.com/
```

Expected result while running: all three return `200`.

In the browser:

- Click `Enter demo`.
- Refresh `/explore`.
- Refresh `/global-graphs`.
- Create and open a share link to verify `/shared/<token>`.
- Try `Register` with a test address and confirm the app stays unauthenticated
  with the check-email message. Do not expect persistent accounts to enter the
  workspace without email verification and approval.

## Stop the Demo

Stop the Spring Boot terminal and the tunnel terminal with `Ctrl-C`.

Optional:

```bash
docker compose down
```

After the app or tunnel is stopped, the public frontdoor should return the
Worker offline page:

```bash
curl -I https://archipelago-demo.zhengwangyuan-patrick.com/
```

Expected result while offline: `503`.

## One-Time Cloudflare Requirements

DNS records:

- `live-archipelago-demo.zhengwangyuan-patrick.com`
  - Type: `Tunnel`
  - Target/content: `archipelago-demo`
  - Proxy status: proxied
- `archipelago-demo.zhengwangyuan-patrick.com`
  - Type: `A`
  - IPv4 address: `192.0.2.1`
  - Proxy status: proxied

Worker route:

```bash
cd /Users/zhengwangyuan/repos/my-site
npx wrangler deploy --config cloudflare/wrangler.worker.toml
```

The deployed Worker routes include:

```text
archipelago-demo.zhengwangyuan-patrick.com/*
```

This setup does not require Cloudflare Zero Trust. Without Zero Trust, the demo
is public while the local app and tunnel are running. The seeded demo account is
intentionally public; persistent accounts remain behind email verification and
signup gating.

## Auth Operations

- Keep `ARCHIPELAGO_SESSION_COOKIE_SECURE=true` for the HTTPS frontdoor. Startup
  fails if the public base URL is HTTPS and secure cookies are disabled.
- Use `ARCHIPELAGO_SIGNUP_MODE=approval` for the by-request demo unless a real
  invite or approval workflow is being operated.
- If outbound mail is disabled, verification and reset links are logged only for
  local operation. Do not rely on persistent signup for public evaluators until
  mail delivery is configured.
- Review `auth_audit_events` for login, registration, verification, reset,
  logout, demo-login, and profile-delete outcomes. The table stores hashed IP
  and user-agent values, not raw request bodies or plaintext tokens.
