import type {
  ApiEnvelope,
  CatalogImport,
  Connection,
  FriendProfile,
  FriendRequest,
  FriendRequests,
  GlobalGraph,
  GlobalGraphPath,
  GraphSuggestion,
  Movie,
  MovieConnections,
  MoviePath,
  MovieSearchFilters,
  PublicUser,
  SessionResponse,
  SharedGraph,
  SharedGraphExport,
  SharedGraphExportSummary,
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

function movieSearchUrl(queryOrFilters: string | MovieSearchFilters) {
  const filters = typeof queryOrFilters === "string" ? { query: queryOrFilters } : queryOrFilters;
  const search = new URLSearchParams();
  if (filters.query?.trim()) {
    search.set("q", filters.query.trim());
  }
  if (filters.person?.trim()) {
    search.set("person", filters.person.trim());
  }
  if (filters.genre?.trim()) {
    search.set("genre", filters.genre.trim());
  }
  if (filters.year?.trim()) {
    search.set("year", filters.year.trim());
  }
  if (filters.graphStatus && filters.graphStatus !== "all") {
    search.set("graphStatus", filters.graphStatus);
  }
  return `/api/movies/search?${search.toString()}`;
}

export const api = {
  getSession: () => request<SessionResponse>("/api/auth/session"),
  register: (payload: { email: string; password: string; username: string }) =>
    request<SessionResponse>("/api/auth/register", {
      method: "POST",
      headers: JSON_HEADERS,
      body: JSON.stringify(payload),
    }),
  loginDemo: () =>
    request<SessionResponse>("/api/auth/demo", {
      method: "POST",
      headers: JSON_HEADERS,
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
  resendVerification: (email: string) =>
    request<void>("/api/auth/resend-verification", {
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
  searchMovies: (queryOrFilters: string | MovieSearchFilters) =>
    request<Movie[]>(movieSearchUrl(queryOrFilters)),
  getGraphSuggestions: (
    movieId: number,
    options: { limit?: number; categories?: Array<NonNullable<Connection["category"]>>; includeExisting?: boolean } = {},
  ) => {
    const search = new URLSearchParams({ movieId: String(movieId) });
    if (options.limit !== undefined) {
      search.set("limit", String(options.limit));
    }
    if (options.categories?.length) {
      search.set("categories", options.categories.join(","));
    }
    if (options.includeExisting) {
      search.set("includeExisting", "true");
    }
    return request<GraphSuggestion[]>(`/api/graph-suggestions?${search.toString()}`);
  },
  importCuratedCatalog: (source = "curated-spring-2026") =>
    request<CatalogImport>(`/api/movies/imports/curated?source=${encodeURIComponent(source)}`, {
      method: "POST",
      headers: JSON_HEADERS,
    }),
  previewCatalogImport: (provider = "curated", source = "curated-spring-2026") =>
    request<CatalogImport>(`/api/movies/imports/preview?provider=${encodeURIComponent(provider)}&source=${encodeURIComponent(source)}`, {
      method: "POST",
      headers: JSON_HEADERS,
    }),
  applyCatalogImport: (provider = "curated", source = "curated-spring-2026") =>
    request<CatalogImport>(`/api/movies/imports/apply?provider=${encodeURIComponent(provider)}&source=${encodeURIComponent(source)}`, {
      method: "POST",
      headers: JSON_HEADERS,
    }),
  getMovie: (movieId: number) =>
    request<Movie>(`/api/movies/${movieId}`),
  getMovieConnections: (movieId: number) =>
    request<MovieConnections>(`/api/movies/${movieId}/connections`),
  getMoviePath: (fromMovieId: number, toMovieId: number) =>
    request<MoviePath>(`/api/movies/path?from=${encodeURIComponent(fromMovieId)}&to=${encodeURIComponent(toMovieId)}`),
  createShare: (payload: { movieId: number; title?: string }) =>
    request<SharedGraphExport>("/api/shares", {
      method: "POST",
      headers: JSON_HEADERS,
      body: JSON.stringify(payload),
    }),
  getShares: () => request<SharedGraphExportSummary[]>("/api/shares"),
  revokeShare: (shareToken: string) =>
    request<void>(`/api/shares/${encodeURIComponent(shareToken)}`, { method: "DELETE" }),
  getSharedGraph: (shareToken: string) =>
    request<SharedGraph>(`/api/shares/${encodeURIComponent(shareToken)}`),
  getConnections: () => request<Connection[]>("/api/connections"),
  createConnection: (payload: {
    fromMovieId: number;
    toMovieId: number;
    reason: string;
    weight?: number;
    category?: Connection["category"];
  }) =>
    request<Connection>("/api/connections", {
      method: "POST",
      headers: JSON_HEADERS,
      body: JSON.stringify(payload),
    }),
  updateConnection: (
    id: number,
    payload: { reason: string; weight?: number; category?: Connection["category"] },
  ) =>
    request<Connection>(`/api/connections/${id}`, {
      method: "PUT",
      headers: JSON_HEADERS,
      body: JSON.stringify(payload),
    }),
  deleteConnection: (id: number) =>
    request<void>(`/api/connections/${id}`, { method: "DELETE" }),
  getProfile: () => request<UserProfile>("/api/users/profile"),
  searchUsers: (query: string) =>
    request<PublicUser[]>(`/api/users/search?q=${encodeURIComponent(query)}`),
  getFriends: () => request<PublicUser[]>("/api/friends"),
  getFriendRequests: () => request<FriendRequests>("/api/friends/requests"),
  sendFriendRequest: (recipientUserId: number) =>
    request<FriendRequest>("/api/friends/requests", {
      method: "POST",
      headers: JSON_HEADERS,
      body: JSON.stringify({ recipientUserId }),
    }),
  acceptFriendRequest: (requestId: number) =>
    request<FriendRequest>(`/api/friends/requests/${requestId}/accept`, {
      method: "POST",
      headers: JSON_HEADERS,
    }),
  declineFriendRequest: (requestId: number) =>
    request<FriendRequest>(`/api/friends/requests/${requestId}/decline`, {
      method: "POST",
      headers: JSON_HEADERS,
    }),
  removeFriend: (friendUserId: number) =>
    request<void>(`/api/friends/${friendUserId}`, { method: "DELETE" }),
  getFriendProfile: (friendUserId: number) =>
    request<FriendProfile>(`/api/friends/${friendUserId}/profile`),
  getFriendMovieConnections: (friendUserId: number, movieId: number) =>
    request<MovieConnections>(`/api/friends/${friendUserId}/movies/${movieId}/connections`),
  getFriendMoviePath: (friendUserId: number, fromMovieId: number, toMovieId: number) =>
    request<MoviePath>(`/api/friends/${friendUserId}/movies/path?from=${encodeURIComponent(fromMovieId)}&to=${encodeURIComponent(toMovieId)}`),
  getMyGlobalGraph: () => request<GlobalGraph>("/api/global-graphs/me"),
  getGlobalGraphFriends: () => request<PublicUser[]>("/api/global-graphs/friends"),
  getFriendGlobalGraph: (friendUserId: number) =>
    request<GlobalGraph>(`/api/global-graphs/friends/${friendUserId}`),
  getAllFriendsGlobalGraph: () =>
    request<GlobalGraph>("/api/global-graphs/friends/aggregate"),
  getGlobalGraphPath: (scope: "me" | "friend" | "all-friends", fromMovieId: number, toMovieId: number, friendUserId?: number) => {
    const search = new URLSearchParams({
      scope,
      from: String(fromMovieId),
      to: String(toMovieId),
    });
    if (friendUserId) {
      search.set("friendUserId", String(friendUserId));
    }
    return request<GlobalGraphPath>(`/api/global-graphs/path?${search.toString()}`);
  },
  updateProfile: (payload: { username?: string; password?: string }) =>
    request<void>("/api/users/profile", {
      method: "PUT",
      headers: JSON_HEADERS,
      body: JSON.stringify(payload),
    }),
  deleteProfile: () => request<void>("/api/users/profile", { method: "DELETE" }),
};
