import type {
  ApiEnvelope,
  Connection,
  Movie,
  MovieConnections,
  SessionResponse,
  UserProfile,
} from "./types";

const JSON_HEADERS = {
  "Content-Type": "application/json",
};

function readCsrfCookie() {
  const prefix = "XSRF-TOKEN=";
  const match = document.cookie
    .split("; ")
    .find((part) => part.startsWith(prefix));
  if (!match) {
    return "";
  }
  const raw = match.slice(prefix.length);
  let decoded = raw;
  try {
    decoded = decodeURIComponent(raw);
  } catch {
    // Safari surfaces malformed cookie decoding as "The string did not match the expected pattern".
  }
  // Strip any character WebKit refuses as an HTTP header value — same error otherwise.
  return decoded.replace(/[^\x21-\x7e]/g, "");
}

export class ApiError extends Error {
  data: unknown;
  status: number;

  constructor(message: string, data: unknown, status: number) {
    super(message);
    this.name = "ApiError";
    this.data = data;
    this.status = status;
  }
}

async function request<T>(input: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers ?? {});
  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (init.method && init.method !== "GET") {
    const csrfToken = readCsrfCookie();
    if (csrfToken) {
      headers.set("X-XSRF-TOKEN", csrfToken);
    }
  }

  const response = await fetch(input, {
    credentials: "include",
    ...init,
    headers,
  });
  const payload = (await response.json()) as ApiEnvelope<T>;
  if (!response.ok || !payload.success) {
    throw new ApiError(payload.message || "Request failed", payload.data, response.status);
  }
  return payload.data;
}

export const api = {
  getSession: () => request<SessionResponse>("/api/auth/session"),
  register: (payload: { email: string; password: string; username: string }) =>
    request<SessionResponse>("/api/auth/register", {
      method: "POST",
      headers: JSON_HEADERS,
      body: JSON.stringify(payload),
    }),
  login: (payload: { email: string; password: string }) =>
    request<SessionResponse>("/api/auth/login", {
      method: "POST",
      headers: JSON_HEADERS,
      body: JSON.stringify(payload),
    }),
  logout: () => request<void>("/api/auth/logout", { method: "POST" }),
  forgotPassword: (email: string) =>
    request<void>("/api/auth/forgot-password", {
      method: "POST",
      headers: JSON_HEADERS,
      body: JSON.stringify({ email }),
    }),
  resetPassword: (payload: { token: string; newPassword: string }) =>
    request<void>("/api/auth/reset-password", {
      method: "POST",
      headers: JSON_HEADERS,
      body: JSON.stringify(payload),
    }),
  verifyAccount: (token: string) =>
    request<void>(`/api/auth/verify?token=${encodeURIComponent(token)}`),
  searchMovies: (query: string) =>
    request<Movie[]>(`/api/movies/search?q=${encodeURIComponent(query)}`),
  getMovieConnections: (movieId: number) =>
    request<MovieConnections>(`/api/movies/${movieId}/connections`),
  getConnections: () => request<Connection[]>("/api/connections"),
  createConnection: (payload: {
    fromMovieId: number;
    toMovieId: number;
    reason: string;
    weight?: number;
    category?: string;
  }) =>
    request<Connection>("/api/connections", {
      method: "POST",
      headers: JSON_HEADERS,
      body: JSON.stringify(payload),
    }),
  updateConnection: (
    id: number,
    payload: { reason: string; weight?: number; category?: string },
  ) =>
    request<Connection>(`/api/connections/${id}`, {
      method: "PUT",
      headers: JSON_HEADERS,
      body: JSON.stringify(payload),
    }),
  deleteConnection: (id: number) =>
    request<void>(`/api/connections/${id}`, { method: "DELETE" }),
  getProfile: () => request<UserProfile>("/api/users/profile"),
  updateProfile: (payload: { username?: string; password?: string }) =>
    request<void>("/api/users/profile", {
      method: "PUT",
      headers: JSON_HEADERS,
      body: JSON.stringify(payload),
    }),
  deleteProfile: () => request<void>("/api/users/profile", { method: "DELETE" }),
};
