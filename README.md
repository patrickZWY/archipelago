# Archipelago

Archipelago is a secure movie-graph app in one repo:

- Spring Boot + MyBatis + PostgreSQL backend at the repo root, served under `/api/*`
- Vite + React + TypeScript SPA in [`frontend`](./frontend)

The app uses server-side session authentication with cookies. The frontend never stores bearer tokens.

## Product Flow

- Authenticate with register, login, logout, forgot password, reset password, and session bootstrap endpoints.
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

- Logging in or registering routes the user into `Explore`.
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
- `Secure` is env-configurable. Keep it `true` in production behind HTTPS.
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

- `/api/auth`: register, login, demo login, logout, session bootstrap, account verification, forgot password, reset password
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

## Backlog

Open [future.md](/Users/zhengwangyuan/repos/archipelago/future.md) for the current follow-up list.

## Production Pattern

Use a single-origin deployment:

- Serve the compiled SPA from a reverse proxy or CDN on the same origin as the API.
- Route `/api/*` to Spring Boot.
- Keep `ARCHIPELAGO_SESSION_COOKIE_SECURE=true`.
