# Archipelago

Archipelago is a secure movie-graph app in one repo:

- Spring Boot + MyBatis + PostgreSQL backend at the repo root, served under `/api/*`
- Vite + React + TypeScript SPA in [`frontend`](./frontend)

The app uses server-side session authentication with cookies. The frontend never stores bearer tokens.

## Product Flow

- Authenticate with register, email verification, login, logout, forgot password, reset password, and session bootstrap endpoints.
- Show inline field-level validation feedback for register, forgot-password, and reset-password failures, while preserving general status messaging for token and session errors.
- Open a seeded demo session from the auth screen for first-run evaluation.
- Move through separate authenticated workspaces:
  - `Explore` for movie search and graph viewing
  - `Connections` for creating, editing, and deleting saved links
  - `Network` for user search, friend requests, and friend management
  - `Global Graphs` for full-scope graph browsing across `Me`, `Friend`, and `All Friends`
- Search one movie from the local catalog in `Explore`.
- Render the current user's full connected component for the selected movie.
- Inspect connections as an interactive force-directed graph with category and weight filters, shortest-path explanation, movie/edge detail drawers, draggable nodes, wheel zoom, pan, and a zoom slider.
- Create, edit, and delete two-movie connections in the separate `Connections` workspace.
- Jump from `Explore` into `Connections` to edit an existing edge or preload a movie pair.
- Create, list, open, and revoke read-only shared graph URLs from `Explore`.
- Browse accepted friends in a dedicated read-only `Friend Archive` page with the same category and weight filters used in `Explore`.
- Browse full read-only graph scopes in `Global Graphs`, including:
  - your full saved graph across disconnected components
  - one accepted friend's full graph
  - a merged `All Friends` graph built from your graph plus all accepted friends, with duplicate movie-pair edges collapsed and contributor provenance preserved
- Focus one contributor inside `All Friends` to highlight that friend's merged edges and dim the rest of the graph.
- Sync an expanded curated movie dataset from `Connections`.
- Update profile settings from the authenticated session.

## Frontend Workspace Notes

- Logging in routes the user into `Explore`; registering now leaves the user on the auth screen until email verification is complete.
- The graph renderer is lazy-loaded so auth and lighter non-graph flows avoid paying the D3 graph bundle cost up front.
- The graph page and connection editor are intentionally split so the graph canvas can occupy most of the screen.
- `Network` stays focused on social actions; friend graph browsing opens on its own route.
- Graph interaction uses a D3 force simulation with draggable nodes, wheel zoom, drag-to-pan, and a matching zoom slider.
- `All Friends` uses a tighter compact force preset than the other graph views so merged friend graphs read as a denser cluster.
- The `All Friends` detail drawer exposes contributor count and contributor usernames for collapsed aggregate edges, including your own graph when you are one of the contributors.
- Movie pickers and explore search support keyboard navigation with a highlighted active result.
- Logging out clears authenticated status text before returning to the auth screen.

## Security Notes

- CSRF is enabled for mutating requests.
- The SPA reads the standard `XSRF-TOKEN` cookie and sends `X-XSRF-TOKEN`.
- Session cookies are `HttpOnly` and `SameSite=Lax`.
- Browser auth stays in server-side sessions; the frontend never stores bearer tokens.
- `Secure` is env-configurable and startup fails when `app.frontend-base-url` is HTTPS while secure session cookies are disabled.
- Persistent accounts are created as `PENDING_VERIFICATION`; verification uses high-entropy one-time tokens stored only as hashes.
- `ARCHIPELAGO_SIGNUP_MODE` controls persistent signup: `public`, `approval`, or `invite`. The public demo session remains available through `/api/auth/demo`.
- Password reset tokens are stored as hashes, expire, are single-use, and revoke existing sessions after a successful reset.
- Auth audit events record event type, outcome, user id when known, timestamp, and IP/user-agent hashes. They do not store request bodies, passwords, or plaintext tokens.
- No tracked config file contains live credentials.

## Repo Layout

- Backend source: [`src/main/java`](./src/main/java)
- SQL migrations: [`src/main/resources/db/migration`](./src/main/resources/db/migration)
- Curated catalog seed: [`src/main/resources/catalog/curated-spring-2026.json`](./src/main/resources/catalog/curated-spring-2026.json)
- Frontend SPA: [`frontend`](./frontend)

## Local Setup

1. Copy `.env.example` into `.env` and adjust values if needed.
2. Start local dependencies:

```bash
docker compose up -d
```

3. Start the backend:

```bash
./mvnw spring-boot:run
```

4. Start the frontend:

```bash
cd frontend
npm install
npm run dev
```

Backend runs on `http://localhost:8080`.
Frontend runs on `http://localhost:5173`.
Docker Postgres is exposed on `localhost:5433` to avoid conflicts with an existing local Postgres on `5432`.

## Database

- Flyway migrations live in `src/main/resources/db/migration`.
- Startup migrations create the core schema, load the base movie catalog, expand movie metadata, and seed demo graphs.
- The deterministic demo data includes:
  - a `demo` account available through the `Open demo session` flow
  - two accepted demo friends with their own saved graph components
- Curated import datasets live in `src/main/resources/catalog`.
- The current curated import source is `curated-spring-2026`.
- The app does not depend on any external movie API in v1.

## API Surface

All API responses use the standard envelope:

```json
{
  "success": true,
  "data": {},
  "message": "..."
}
```

Main route groups:

- `/api/auth`: register, login, demo login, logout, session bootstrap, account verification, verification resend, forgot password, reset password
- `/api/movies`: catalog search, movie detail, current-user graph component, shortest path, curated catalog import
- `/api/connections`: CRUD for the authenticated user's saved movie-to-movie edges
- `/api/users`: profile read/update/delete and username search
- `/api/friends`: friend list, incoming/outgoing requests, request accept/decline, friend removal, read-only friend graph access
- `/api/global-graphs`: full current-user graph, accepted-friend list, single-friend full graph, merged all-friends graph, and scope-aware shortest-path explanation
- `/api/shares`: create, list, revoke, and publicly read shared graph exports

## Testing

Backend:

```bash
./mvnw test
```

Frontend:

```bash
cd frontend
npm test
npm run build
```

## By-Request Demo

The demo frontdoor is `https://archipelago-demo.zhengwangyuan-patrick.com/`.
It proxies to the live tunnel at
`https://live-archipelago-demo.zhengwangyuan-patrick.com/` while the local app
is running, and returns the personal site's Worker offline page otherwise.

Run the single-origin demo locally:

```bash
scripts/run-demo.sh
```

The script builds `frontend/dist`, serves it from Spring Boot with the `demo`
profile, binds the app to `127.0.0.1:8080`, sets
`ARCHIPELAGO_FRONTEND_BASE_URL` to the public frontdoor URL, enables secure
session cookies, sets persistent signup to `approval`, and leaves outbound mail disabled unless
`ARCHIPELAGO_MAIL_ENABLED` is already set.

Run the named tunnel in a second shell:

```bash
scripts/run-demo-tunnel.sh
```

Default tunnel settings:

- Tunnel name: `archipelago-demo`
- Live hostname: `live-archipelago-demo.zhengwangyuan-patrick.com`
- Local service: `http://127.0.0.1:8080`

Configure the Cloudflare tunnel public hostname to point
`live-archipelago-demo.zhengwangyuan-patrick.com` at
`http://127.0.0.1:8080`. Without Cloudflare Access this demo is public while
the local app and tunnel are running, so stop the app or tunnel when the demo is
not intentionally live.

For exact local and online run steps, see [docs/demo-runbook.md](docs/demo-runbook.md).

## Backlog

Open [future.md](/Users/zhengwangyuan/repos/archipelago/future.md) for the current follow-up list.

## Production Pattern

Use a single-origin deployment:

- Serve the compiled SPA from a reverse proxy or CDN on the same origin as the API.
- Route `/api/*` to Spring Boot.
- Keep `ARCHIPELAGO_SESSION_COOKIE_SECURE=true`.
- Set `ARCHIPELAGO_SIGNUP_MODE=approval` or `invite` for public demos unless mail and account approval are intentionally configured.
- Set `ARCHIPELAGO_AUDIT_HASH_SALT` to a deployment-specific secret so audit hashes are not reusable across environments.
