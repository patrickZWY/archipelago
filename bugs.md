# Bugs

## Registration in Safari shows "The string did not match the expected pattern"

### Symptom
Submitting the register form in Safari (any credentials, e.g. `helloworld@gmail.com` / `helloworld` / `helloworld`) shows the red banner `The string did not match the expected pattern.` instead of a real error message. Chrome did not reproduce.

### Root cause
Two layered problems. The CORS rejection was the real failure; the charset bug hid it behind a cryptic Safari message.

1. **`SecurityConfig.writeJson` wrote response bodies via `response.getWriter()` without setting a character encoding.** Tomcat defaulted the writer to ISO-8859-1 and appended `;charset=ISO-8859-1` to `Content-Type`. WebKit's `Response.json()` refuses to decode a JSON body declared as non-UTF-8 and throws a `SyntaxError` whose `.message` is literally `"The string did not match the expected pattern."`. The frontend's catch block surfaced that message verbatim. This path is only used by the Spring Security entry-point and access-denied handlers (auth/CSRF failures), so the bug only appeared when those failures happened — masking whatever the real auth/CSRF problem was.

2. **The actual 403 was a CORS rejection.** `app.frontend-base-url` defaults to `http://localhost:5173`, and `SecurityConfig.corsConfigurationSource` only allows that exact origin. Three orphan Vite dev servers were holding 5173–5175, so a fresh `npm run dev` fell through to 5176. Requests from `http://localhost:5176` failed Spring's CORS check, returning a 403 with no JSON body — which Safari then tried to parse, triggering bug #1.

### Fix
- [src/main/java/com/archipelago/config/SecurityConfig.java:94](src/main/java/com/archipelago/config/SecurityConfig.java#L94) — added `response.setCharacterEncoding("UTF-8")` before `getWriter()` so security-failure responses are served as UTF-8 JSON. This restores readable error messages in WebKit.
- [frontend/src/lib/api.ts:14-31](frontend/src/lib/api.ts#L14-L31) — hardened `readCsrfCookie` to use `slice(prefix.length)` (so `=` chars in the value are preserved) and to strip any non-printable-ASCII byte before returning, since WebKit's `Headers.set` rejects such values with the same "expected pattern" message.
- Killed orphan Vite processes holding 5173–5175 so the dev server binds to the configured port and CORS accepts it.

### How to confirm
With one Vite on 5173 and the backend restarted, `POST /api/auth/register` returns 201 and registration succeeds in Safari. If registration fails again, check (a) the Vite port matches `app.frontend-base-url`, and (b) the response `Content-Type` is `application/json;charset=UTF-8`.
