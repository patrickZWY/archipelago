# Archipelago

Archipelago is a secure two-part web app in one repo:

- Spring Boot + MyBatis + PostgreSQL backend under `/api`
- Vite + React + TypeScript SPA in [`frontend`](./frontend)

The app uses server-side session authentication with cookies. The frontend never stores bearer tokens.

## Product Flow

- Authenticate with register, login, logout, forgot password, reset password, and session bootstrap endpoints.
- Search one movie from the local catalog.
- View only the current user's saved connections for the selected movie.
- Create, edit, and delete two-movie connections in a separate editor.
- Inspect connections as an interactive graph.
- Update profile settings from the authenticated session.

## Security Notes

- CSRF is enabled for mutating requests.
- The SPA reads the standard `XSRF-TOKEN` cookie and sends `X-XSRF-TOKEN`.
- Session cookies are `HttpOnly` and `SameSite=Lax`.
- `Secure` is env-configurable. Keep it `true` in production behind HTTPS.
- No tracked config file contains live credentials.

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
- A deterministic local movie seed set loads automatically at startup.
- The app does not depend on any external movie API in v1.

## Testing

Backend:

```bash
./mvnw test
```

Frontend:

```bash
cd frontend
npm test
```

## Backlog

Open [future.md](/Users/zhengwangyuan/repos/archipelago/future.md) for the current follow-up list.

## Production Pattern

Use a single-origin deployment:

- Serve the compiled SPA from a reverse proxy or CDN on the same origin as the API.
- Route `/api/*` to Spring Boot.
- Keep `ARCHIPELAGO_SESSION_COOKIE_SECURE=true`.
