import { startTransition, useCallback, useDeferredValue, useEffect, useRef, useState, type FormEvent, type ReactNode } from "react";
import { ConnectionGraph } from "./components/ConnectionGraph";
import { ApiError, api } from "./lib/api";
import { CONNECTION_CATEGORY_OPTIONS, formatConnectionCategory, type ConnectionCategory } from "./lib/connection-categories";
import type {
  Connection,
  FriendProfile,
  FriendRequests,
  Movie,
  MoviePath,
  PublicUser,
  SessionResponse,
  SharedGraph,
  UserProfile,
} from "./lib/types";

type AuthMode = "login" | "register" | "forgot";
type AppPath = "/" | "/explore" | "/connections" | "/network" | "/friend" | "/verify" | "/reset-password" | "/shared";

type ConnectionForm = {
  fromMovieId: string;
  toMovieId: string;
  reason: string;
  weight: string;
  category: ConnectionCategory | "";
};

type MoviePickerState = {
  query: string;
  results: Movie[];
  selected: Movie | null;
};

const emptyConnectionForm: ConnectionForm = {
  fromMovieId: "",
  toMovieId: "",
  reason: "",
  weight: "1",
  category: "",
};

const emptyMoviePickerState: MoviePickerState = {
  query: "",
  results: [],
  selected: null,
};

export default function App() {
  const [session, setSession] = useState<SessionResponse | null>(null);
  const [status, setStatusState] = useState("Booting session...");
  const [loading, setLoading] = useState(true);
  const [pathname, setPathname] = useState<AppPath>(readPathname());
  const statusTimeoutRef = useRef<number | null>(null);

  const setStatus = useCallback((nextStatus: string, options?: { persist?: boolean }) => {
    if (statusTimeoutRef.current !== null) {
      window.clearTimeout(statusTimeoutRef.current);
      statusTimeoutRef.current = null;
    }

    setStatusState(nextStatus);

    if (!nextStatus || options?.persist) {
      return;
    }

    statusTimeoutRef.current = window.setTimeout(() => {
      setStatusState("");
      statusTimeoutRef.current = null;
    }, 5000);
  }, []);

  useEffect(() => {
    void api.getSession()
      .then((data) => {
        setSession(data);
        setStatus("");
      })
      .catch((error: Error) => {
        setStatus(error.message);
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => () => {
    if (statusTimeoutRef.current !== null) {
      window.clearTimeout(statusTimeoutRef.current);
    }
  }, []);

  useEffect(() => {
    const onPopState = () => setPathname(readPathname());
    window.addEventListener("popstate", onPopState);
    return () => window.removeEventListener("popstate", onPopState);
  }, []);

  useEffect(() => {
    if (loading || !session?.authenticated || !session.user) {
      return;
    }
    if (pathname === "/" || pathname === "/verify" || pathname === "/reset-password" || pathname === "/shared") {
      navigateTo("/explore", setPathname);
    }
  }, [loading, pathname, session]);

  if (loading) {
    return <div className="screen shell-center">{status}</div>;
  }

  if (pathname === "/verify") {
    return <VerifyPage />;
  }

  if (pathname === "/reset-password") {
    return <ResetPasswordPage />;
  }

  if (pathname === "/shared") {
    return <SharedGraphPage authenticatedUsername={session?.user?.username ?? null} />;
  }

  if (!session?.authenticated || !session.user) {
    return (
      <AuthPage
        onSession={setSession}
        onOpenWorkspace={() => navigateTo("/explore", setPathname)}
        status={status}
        setStatus={setStatus}
      />
    );
  }

  if (pathname === "/friend") {
    return (
      <FriendGraphPage
        authenticatedUsername={session.user.username}
        onLogout={async () => {
          await api.logout();
          setStatus("");
          setSession({ authenticated: false, user: null });
          navigateTo("/", setPathname);
        }}
        status={status}
        setStatus={setStatus}
      />
    );
  }

  const currentPath = pathname === "/connections"
    ? "/connections"
    : pathname === "/network"
      ? "/network"
      : "/explore";

  return (
    <AppLayout
      session={session}
      currentPath={currentPath}
      onNavigate={(nextPath) => navigateTo(nextPath, setPathname)}
      onLogout={async () => {
        await api.logout();
        setStatus("");
        setSession({ authenticated: false, user: null });
        navigateTo("/", setPathname);
      }}
      status={status}
    >
      {currentPath === "/connections" ? <ConnectionsPage setStatus={setStatus} /> : null}
      {currentPath === "/explore" ? <ExplorePage setStatus={setStatus} /> : null}
      {currentPath === "/network" ? <NetworkPage setStatus={setStatus} /> : null}
    </AppLayout>
  );
}

function AuthPage({
  onSession,
  onOpenWorkspace,
  status,
  setStatus,
}: {
  onSession: (session: SessionResponse) => void;
  onOpenWorkspace: () => void;
  status: string;
  setStatus: (status: string) => void;
}) {
  const [mode, setMode] = useState<AuthMode>("login");
  const [form, setForm] = useState({ email: "", password: "", username: "" });
  const [fieldErrors, setFieldErrors] = useState<{ email?: string; password?: string; username?: string }>({});
  const [formError, setFormError] = useState("");

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFieldErrors({});
    setFormError("");
    try {
      if (mode === "register") {
        const data = await api.register(form);
        onSession(data);
        onOpenWorkspace();
        setStatus("Session opened");
        return;
      }
      if (mode === "forgot") {
        await api.forgotPassword(form.email);
        setStatus("If the account exists, the reset email has been issued");
        return;
      }
      const data = await api.login({ email: form.email, password: form.password });
      onSession(data);
      onOpenWorkspace();
      setStatus("Session opened");
    } catch (error) {
      if (error instanceof ApiError && error.data && typeof error.data === "object" && mode === "register") {
        const data = error.data as Record<string, string>;
        setFieldErrors({
          email: data.email,
          password: data.password,
          username: data.username,
        });
        const message = data.email || data.password || data.username || error.message;
        setFormError(message);
        setStatus(message);
        return;
      }
      if (error instanceof ApiError && (mode === "login" || mode === "register" || mode === "forgot")) {
        setFormError(error.message);
        setStatus(error.message);
        return;
      }
      const message = (error as Error).message;
      setFormError(message);
      setStatus(message);
    }
  }

  async function openDemoSession() {
    setFormError("");
    try {
      const data = await api.loginDemo();
      onSession(data);
      onOpenWorkspace();
      setStatus("Demo session opened");
    } catch (error) {
      const message = (error as Error).message;
      setFormError(message);
      setStatus(message);
    }
  }

  return (
    <div className="screen auth-screen">
      <section className="auth-card">
        <p className="eyebrow">Archipelago</p>
        <h1>Map how films connect.</h1>
        <p className="lede">
          Search one movie at a time, inspect only your saved links for it, then create or revise
          two-movie connections in a separate workspace.
        </p>
        <div className="demo-banner">
          <strong>Demo graph available</strong>
          <span>Open the seeded account and curated graph without creating credentials first.</span>
          <button className="pill" type="button" onClick={() => void openDemoSession()}>Enter demo</button>
        </div>
        <div className="pill-row">
          <button className={mode === "login" ? "pill active" : "pill"} onClick={() => setMode("login")}>Login</button>
          <button className={mode === "register" ? "pill active" : "pill"} onClick={() => setMode("register")}>Register</button>
          <button className={mode === "forgot" ? "pill active" : "pill"} onClick={() => setMode("forgot")}>Forgot</button>
        </div>
        <form className="stack" onSubmit={handleSubmit}>
          <label>
            <span>Email</span>
            <input
              value={form.email}
              onChange={(event) => setForm({ ...form, email: event.target.value })}
              type="email"
              autoComplete="email"
              required
            />
            {mode === "register" && fieldErrors.email && <small className="field-error">{fieldErrors.email}</small>}
          </label>
          {mode !== "forgot" && (
            <label>
              <span>Password</span>
              <input
                value={form.password}
                onChange={(event) => setForm({ ...form, password: event.target.value })}
                type="password"
                autoComplete={mode === "register" ? "new-password" : "current-password"}
                required
              />
              {mode === "register" && (
                <small className="field-hint">Use at least 8 characters.</small>
              )}
              {mode === "register" && fieldErrors.password && <small className="field-error">{fieldErrors.password}</small>}
            </label>
          )}
          {mode === "register" && (
            <label>
              <span>Username</span>
              <input
                value={form.username}
                onChange={(event) => setForm({ ...form, username: event.target.value })}
                autoComplete="username"
                required
              />
              {fieldErrors.username && <small className="field-error">{fieldErrors.username}</small>}
            </label>
          )}
          <button className="primary" type="submit">
            {mode === "register" ? "Create account" : mode === "forgot" ? "Send reset link" : "Enter workspace"}
          </button>
          {formError && <p className="auth-error" role="alert">{formError}</p>}
        </form>
        {status && <p className="status">{status}</p>}
      </section>
    </div>
  );
}

function AppLayout({
  session,
  currentPath,
  onNavigate,
  onLogout,
  status,
  children,
}: {
  session: SessionResponse;
  currentPath: "/explore" | "/connections" | "/network";
  onNavigate: (path: "/explore" | "/connections" | "/network") => void;
  onLogout: () => Promise<void>;
  status: string;
  children: ReactNode;
}) {
  return (
    <div className="screen dashboard">
      <header className="masthead">
        <div>
          <p className="eyebrow">Session User</p>
          <h1>{session.user?.username}</h1>
        </div>
        <div className="shell-actions">
          <nav className="pill-row" aria-label="Workspace">
            <button
              className={currentPath === "/explore" ? "pill active" : "pill"}
              onClick={() => onNavigate("/explore")}
            >
              Explore
            </button>
            <button
              className={currentPath === "/connections" ? "pill active" : "pill"}
              onClick={() => onNavigate("/connections")}
            >
              Connections
            </button>
            <button
              className={currentPath === "/network" ? "pill active" : "pill"}
              onClick={() => onNavigate("/network")}
            >
              Network
            </button>
          </nav>
          <button className="ghost" onClick={() => void onLogout()}>Logout</button>
        </div>
      </header>

      {children}

      {status && <footer className="status-bar">{status}</footer>}
    </div>
  );
}

function ExplorePage({ setStatus }: { setStatus: (status: string) => void }) {
  const [query, setQuery] = useState("");
  const deferredQuery = useDeferredValue(query);
  const [searchResults, setSearchResults] = useState<Movie[]>([]);
  const [selectedMovie, setSelectedMovie] = useState<Movie | null>(null);
  const [componentMovies, setComponentMovies] = useState<Movie[]>([]);
  const [movieConnections, setMovieConnections] = useState<Connection[]>([]);
  const [selectedGraphMovieId, setSelectedGraphMovieId] = useState<number | null>(null);
  const [selectedConnectionId, setSelectedConnectionId] = useState<number | null>(null);
  const [categoryFilter, setCategoryFilter] = useState<ConnectionCategory | "all">("all");
  const [minimumWeight, setMinimumWeight] = useState("0");
  const [pathFromMovieId, setPathFromMovieId] = useState("");
  const [pathToMovieId, setPathToMovieId] = useState("");
  const [moviePath, setMoviePath] = useState<MoviePath | null>(null);
  const [shareUrl, setShareUrl] = useState("");

  useEffect(() => {
    if (!deferredQuery.trim()) {
      setSearchResults([]);
      return;
    }
    const timeout = window.setTimeout(() => {
      void api.searchMovies(deferredQuery.trim())
        .then((results) => startTransition(() => setSearchResults(results)))
        .catch((error: Error) => setStatus(error.message));
    }, 180);
    return () => window.clearTimeout(timeout);
  }, [deferredQuery, setStatus]);

  async function pickMovie(movie: Movie) {
    setSelectedMovie(movie);
    try {
      const data = await api.getMovieConnections(movie.id);
      setComponentMovies(data.movies ?? [movie]);
      setMovieConnections(data.connections);
      setSelectedGraphMovieId(movie.id);
      setSelectedConnectionId(null);
      setPathFromMovieId(String(movie.id));
      setPathToMovieId("");
      setMoviePath(null);
      setShareUrl("");
      setStatus(`Loaded your connections for ${movie.title}`);
    } catch (error) {
      setStatus((error as Error).message);
    }
  }

  const filteredConnections = movieConnections.filter((connection) => {
    const passesCategory = categoryFilter === "all" || connection.category === categoryFilter;
    const passesWeight = connection.weight >= Number(minimumWeight || "0");
    return passesCategory && passesWeight;
  });

  const selectedGraphMovie = componentMovies.find((movie) => movie.id === selectedGraphMovieId) ?? selectedMovie;
  const selectedConnection = filteredConnections.find((connection) => connection.id === selectedConnectionId)
    ?? movieConnections.find((connection) => connection.id === selectedConnectionId)
    ?? null;
  const selectedMovieConnectionCount = selectedGraphMovie
    ? filteredConnections.filter((connection) => connection.fromMovieId === selectedGraphMovie.id || connection.toMovieId === selectedGraphMovie.id).length
    : 0;
  const selectedMovieIsFilteredOut = Boolean(
    selectedGraphMovie
    && filteredConnections.length > 0
    && selectedMovieConnectionCount === 0,
  );

  async function loadShortestPath() {
    if (!pathFromMovieId || !pathToMovieId) {
      setStatus("Choose two movies from this component to explain the path");
      return;
    }

    try {
      const path = await api.getMoviePath(Number(pathFromMovieId), Number(pathToMovieId));
      setMoviePath(path);
      setSelectedConnectionId(path.connections[0]?.id ?? null);
      setStatus(`Explained the path from ${path.fromMovie.title} to ${path.toMovie.title}`);
    } catch (error) {
      setMoviePath(null);
      setStatus((error as Error).message);
    }
  }

  function openConnectionEditor(params: { connectionId?: number; fromMovieId?: number; toMovieId?: number }) {
    const search = new URLSearchParams();
    if (params.connectionId) {
      search.set("connectionId", String(params.connectionId));
    }
    if (params.fromMovieId) {
      search.set("from", String(params.fromMovieId));
    }
    if (params.toMovieId) {
      search.set("to", String(params.toMovieId));
    }
    window.history.pushState({}, "", `/connections${search.toString() ? `?${search.toString()}` : ""}`);
    window.dispatchEvent(new PopStateEvent("popstate"));
  }

  async function createShare() {
    if (!selectedMovie) {
      setStatus("Pick a movie before exporting a shared graph");
      return;
    }

    try {
      const exportResponse = await api.createShare({
        movieId: selectedMovie.id,
        title: `${selectedMovie.title} graph`,
      });
      setShareUrl(exportResponse.shareUrl);
      setStatus(`Created a read-only share for ${selectedMovie.title}`);
    } catch (error) {
      setStatus((error as Error).message);
    }
  }

  return (
    <main className="page-stack">
      <section className="panel page-panel">
        <div className="panel-header">
          <h2>Explore Movie Graphs</h2>
          <p>Search the catalog, pick one movie, and inspect the full connected component of your saved graph.</p>
        </div>
        <input
          className="search-input"
          placeholder="Search local catalog, even with rough spelling"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
        {deferredQuery.trim().length > 1 && searchResults.length === 0 && (
          <p className="empty-state">No close matches yet. Try another title, director, or rough spelling.</p>
        )}
        <div className="movie-grid">
          {searchResults.map((movie) => (
            <button key={movie.id} className="movie-card" onClick={() => void pickMovie(movie)}>
              <strong>{movie.title}</strong>
              <span>{movie.releaseYear}</span>
              <span>{movie.director}</span>
            </button>
          ))}
        </div>
      </section>

      <section className="panel page-panel graph-panel">
        <div className="panel-header">
          <h2>Selected Movie Graph</h2>
          <p>{selectedMovie ? selectedMovie.title : "Pick a movie to render your graph."}</p>
        </div>
        <div className="share-row">
          <button className="pill" type="button" onClick={() => void createShare()} disabled={!selectedMovie}>
            Create share link
          </button>
          {shareUrl && <a className="share-link" href={shareUrl}>{shareUrl}</a>}
        </div>
        <div className="graph-inspector-grid">
          <div className="graph-frame-stack">
            <div className="graph-controls">
              <label>
                <span>Category filter</span>
                <select value={categoryFilter} onChange={(event) => setCategoryFilter(event.target.value as ConnectionCategory | "all")}>
                  <option value="all">All categories</option>
                  {CONNECTION_CATEGORY_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </select>
              </label>
              <label>
                <span>Minimum weight</span>
                <input
                  type="number"
                  min="0"
                  step="0.1"
                  value={minimumWeight}
                  onChange={(event) => setMinimumWeight(event.target.value)}
                />
              </label>
            </div>
            <ConnectionGraph
              movie={selectedMovie}
              connections={filteredConnections}
              selectedMovieId={selectedGraphMovieId}
              selectedConnectionId={selectedConnectionId}
              onMovieSelect={(movieId) => {
                setSelectedGraphMovieId(movieId);
                setSelectedConnectionId(null);
              }}
              onConnectionSelect={(connectionId) => {
                setSelectedConnectionId(connectionId);
              }}
            />
          </div>

          <div className="graph-detail-stack">
            <section className="panel inset-panel">
              <div className="panel-header">
                <h3>Movie detail</h3>
                <p>{selectedGraphMovie ? selectedGraphMovie.title : "Select a movie node in the graph."}</p>
              </div>
              {selectedGraphMovie ? (
                <div className="stack">
                  <p className="detail-stat"><strong>{selectedGraphMovie.releaseYear}</strong> / {selectedGraphMovie.director}</p>
                  {selectedGraphMovie.tagline && <p className="detail-copy">{selectedGraphMovie.tagline}</p>}
                  {selectedGraphMovie.synopsis && <p className="detail-copy">{selectedGraphMovie.synopsis}</p>}
                  {(selectedGraphMovie.genres ?? []).length > 0 && <p className="detail-stat">Genres: {(selectedGraphMovie.genres ?? []).join(", ")}</p>}
                  {selectedGraphMovie.runtimeMinutes && <p className="detail-stat">Runtime: {selectedGraphMovie.runtimeMinutes} minutes</p>}
                  {(selectedGraphMovie.castMembers ?? []).length > 0 && <p className="detail-stat">Cast: {(selectedGraphMovie.castMembers ?? []).join(", ")}</p>}
                  {selectedGraphMovie.directorNotes && <p className="detail-copy">{selectedGraphMovie.directorNotes}</p>}
                  <p className="detail-stat">{selectedMovieConnectionCount} visible connections in this view</p>
                  {selectedMovieIsFilteredOut ? (
                    <p className="empty-state">The current filters isolate this movie. Pick another node or relax the filters.</p>
                  ) : null}
                  <div className="pill-row">
                    <button
                      className="pill"
                      onClick={() => {
                        setPathFromMovieId(String(selectedGraphMovie.id));
                        setSelectedConnectionId(null);
                      }}
                    >
                      Use as path start
                    </button>
                    <button
                      className="pill"
                      onClick={() => {
                        setPathToMovieId(String(selectedGraphMovie.id));
                        setSelectedConnectionId(null);
                      }}
                    >
                      Use as path end
                    </button>
                  </div>
                </div>
              ) : (
                <p className="empty-state">The drawer updates when you click a node.</p>
              )}
            </section>

            <section className="panel inset-panel">
              <div className="panel-header">
                <h3>Connection detail</h3>
                <p>{selectedConnection ? `${selectedConnection.fromMovieTitle} -> ${selectedConnection.toMovieTitle}` : "Select an edge to inspect why the films connect."}</p>
              </div>
              {selectedConnection ? (
                <div className="stack">
                  <p>{selectedConnection.reason}</p>
                  <small>{formatConnectionCategory(selectedConnection.category)} / weight {selectedConnection.weight}</small>
                  <div className="pill-row">
                    <button className="pill" onClick={() => openConnectionEditor({ connectionId: selectedConnection.id })}>
                      Edit in Connections
                    </button>
                    <button
                      className="pill"
                      onClick={() => openConnectionEditor({
                        fromMovieId: selectedConnection.fromMovieId,
                        toMovieId: selectedConnection.toMovieId,
                      })}
                    >
                      Duplicate pair in editor
                    </button>
                  </div>
                </div>
              ) : (
                <p className="empty-state">Pick an edge to see its explanation.</p>
              )}
            </section>

            <section className="panel inset-panel">
              <div className="panel-header">
                <h3>Path explanation</h3>
                <p>Choose two movies in this component and inspect the shortest saved path.</p>
              </div>
              <div className="stack">
                <label>
                  <span>From movie</span>
                  <select value={pathFromMovieId} onChange={(event) => setPathFromMovieId(event.target.value)}>
                    <option value="">Select a movie</option>
                    {componentMovies.map((movie) => (
                      <option key={`path-from-${movie.id}`} value={movie.id}>{movie.title}</option>
                    ))}
                  </select>
                </label>
                <label>
                  <span>To movie</span>
                  <select value={pathToMovieId} onChange={(event) => setPathToMovieId(event.target.value)}>
                    <option value="">Select a movie</option>
                    {componentMovies.map((movie) => (
                      <option key={`path-to-${movie.id}`} value={movie.id}>{movie.title}</option>
                    ))}
                  </select>
                </label>
                <button className="primary" type="button" onClick={() => void loadShortestPath()}>
                  Explain shortest path
                </button>
                {moviePath && (
                  <div className="path-card">
                    <strong>{moviePath.movies.map((movie) => movie.title).join(" -> ")}</strong>
                    <div className="list dense-list">
                      {moviePath.connections.map((connection, index) => (
                        <button
                          key={`path-connection-${connection.id}`}
                          className="picker-result"
                          type="button"
                          onClick={() => setSelectedConnectionId(connection.id)}
                        >
                          <strong>{`${moviePath.movies[index]?.title} -> ${moviePath.movies[index + 1]?.title}`}</strong>
                          <span>{formatConnectionCategory(connection.category)} / weight {connection.weight}</span>
                          <span>{connection.reason}</span>
                        </button>
                      ))}
                    </div>
                    <button
                      className="pill"
                      type="button"
                      onClick={() => openConnectionEditor({
                        fromMovieId: moviePath.fromMovie.id,
                        toMovieId: moviePath.toMovie.id,
                      })}
                    >
                      Open endpoints in Connections
                    </button>
                  </div>
                )}
              </div>
            </section>
          </div>
        </div>
      </section>
    </main>
  );
}

function ConnectionsPage({ setStatus }: { setStatus: (status: string) => void }) {
  const [allConnections, setAllConnections] = useState<Connection[]>([]);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [profileDraft, setProfileDraft] = useState({ username: "", password: "" });
  const [connectionForm, setConnectionForm] = useState<ConnectionForm>(emptyConnectionForm);
  const [fromMoviePicker, setFromMoviePicker] = useState<MoviePickerState>(emptyMoviePickerState);
  const [toMoviePicker, setToMoviePicker] = useState<MoviePickerState>(emptyMoviePickerState);
  const [editingId, setEditingId] = useState<number | null>(null);
  const isSelfConnection = Boolean(
    connectionForm.fromMovieId &&
    connectionForm.toMovieId &&
    connectionForm.fromMovieId === connectionForm.toMovieId,
  );

  useEffect(() => {
    void refreshPanels();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function refreshPanels() {
    try {
      const [connections, userProfile] = await Promise.all([api.getConnections(), api.getProfile()]);
      setAllConnections(connections);
      setProfile(userProfile);
      setProfileDraft((current) => ({
        ...current,
        username: userProfile.username,
      }));
      await seedEditorFromLocation(connections);
    } catch (error) {
      setStatus((error as Error).message);
    }
  }

  async function seedEditorFromLocation(connections: Connection[]) {
    const params = new URLSearchParams(window.location.search);
    const connectionId = params.get("connectionId");
    if (connectionId) {
      const connection = connections.find((entry) => entry.id === Number(connectionId));
      if (connection) {
        startEditing(connection);
        setStatus(`Loaded ${connection.fromMovieTitle} -> ${connection.toMovieTitle} into the editor`);
      }
      clearConnectionsRouteSearch();
      return;
    }

    const fromMovieId = params.get("from");
    const toMovieId = params.get("to");
    if (!fromMovieId || !toMovieId) {
      return;
    }

    try {
      const [fromMovie, toMovie] = await Promise.all([api.getMovie(Number(fromMovieId)), api.getMovie(Number(toMovieId))]);
      setFromMoviePicker({ query: fromMovie.title, results: [], selected: fromMovie });
      setToMoviePicker({ query: toMovie.title, results: [], selected: toMovie });
      setConnectionForm((current) => ({
        ...current,
        fromMovieId: String(fromMovie.id),
        toMovieId: String(toMovie.id),
      }));
      setStatus(`Loaded ${fromMovie.title} and ${toMovie.title} into the editor`);
    } catch (error) {
      setStatus((error as Error).message);
    } finally {
      clearConnectionsRouteSearch();
    }
  }

  async function submitConnection(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isSelfConnection) {
      setStatus("Choose two different movies for a connection");
      return;
    }
    try {
      if (editingId) {
        await api.updateConnection(editingId, {
          reason: connectionForm.reason,
          weight: Number(connectionForm.weight),
          category: connectionForm.category || undefined,
        });
        setStatus("Connection updated");
      } else {
        await api.createConnection({
          fromMovieId: Number(connectionForm.fromMovieId),
          toMovieId: Number(connectionForm.toMovieId),
          reason: connectionForm.reason,
          weight: Number(connectionForm.weight),
          category: connectionForm.category || undefined,
        });
        setStatus("Connection created");
      }
      resetEditor();
      await refreshPanels();
    } catch (error) {
      setStatus((error as Error).message);
    }
  }

  async function removeConnection(id: number) {
    try {
      await api.deleteConnection(id);
      setStatus("Connection deleted");
      if (editingId === id) {
        resetEditor();
      }
      await refreshPanels();
    } catch (error) {
      setStatus((error as Error).message);
    }
  }

  async function updateProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    try {
      await api.updateProfile({
        username: profileDraft.username,
        password: profileDraft.password || undefined,
      });
      setProfileDraft((current) => ({ ...current, password: "" }));
      await refreshPanels();
      setStatus("Profile updated");
    } catch (error) {
      setStatus((error as Error).message);
    }
  }

  async function updateMoviePicker(side: "from" | "to", nextQuery: string) {
    const setter = side === "from" ? setFromMoviePicker : setToMoviePicker;
    setter((current) => ({
      ...current,
      query: nextQuery,
      selected: nextQuery === current.selected?.title ? current.selected : null,
    }));

    if (nextQuery.trim().length < 2) {
      setter((current) => ({ ...current, results: [] }));
      return;
    }

    try {
      const results = await api.searchMovies(nextQuery.trim());
      setter((current) => ({ ...current, results }));
    } catch (error) {
      setStatus((error as Error).message);
    }
  }

  function chooseMovie(side: "from" | "to", movie: Movie) {
    const oppositeMovieId = side === "from" ? connectionForm.toMovieId : connectionForm.fromMovieId;
    if (oppositeMovieId && oppositeMovieId === String(movie.id)) {
      setStatus("Choose two different movies for a connection");
      return;
    }
    const setter = side === "from" ? setFromMoviePicker : setToMoviePicker;
    setter({
      query: movie.title,
      results: [],
      selected: movie,
    });
    setConnectionForm((current) => ({
      ...current,
      fromMovieId: side === "from" ? String(movie.id) : current.fromMovieId,
      toMovieId: side === "to" ? String(movie.id) : current.toMovieId,
    }));
  }

  function clearMovie(side: "from" | "to") {
    const setter = side === "from" ? setFromMoviePicker : setToMoviePicker;
    setter(emptyMoviePickerState);
    setConnectionForm((current) => ({
      ...current,
      fromMovieId: side === "from" ? "" : current.fromMovieId,
      toMovieId: side === "to" ? "" : current.toMovieId,
    }));
  }

  function startEditing(connection: Connection) {
    setEditingId(connection.id);
    setConnectionForm({
      fromMovieId: String(connection.fromMovieId),
      toMovieId: String(connection.toMovieId),
      reason: connection.reason,
      weight: String(connection.weight),
      category: connection.category ?? "",
    });
    setFromMoviePicker({
      query: connection.fromMovieTitle,
      results: [],
      selected: {
        id: connection.fromMovieId,
        title: connection.fromMovieTitle,
        releaseYear: 0,
        director: "",
        pictureUrl: null,
        externalId: null,
        tagline: null,
        synopsis: null,
        genres: [],
        runtimeMinutes: null,
        castMembers: [],
        directorNotes: null,
      },
    });
    setToMoviePicker({
      query: connection.toMovieTitle,
      results: [],
      selected: {
        id: connection.toMovieId,
        title: connection.toMovieTitle,
        releaseYear: 0,
        director: "",
        pictureUrl: null,
        externalId: null,
        tagline: null,
        synopsis: null,
        genres: [],
        runtimeMinutes: null,
        castMembers: [],
        directorNotes: null,
      },
    });
  }

  function resetEditor() {
    setEditingId(null);
    setConnectionForm(emptyConnectionForm);
    setFromMoviePicker(emptyMoviePickerState);
    setToMoviePicker(emptyMoviePickerState);
  }

  async function syncCuratedCatalog() {
    try {
      const result = await api.importCuratedCatalog();
      setStatus(`Catalog sync completed: ${result.insertedCount} inserted, ${result.updatedCount} updated from ${result.source}`);
    } catch (error) {
      setStatus((error as Error).message);
    }
  }

  return (
    <main className="layout">
      <section className="panel">
        <div className="panel-header">
          <h2>Connection Editor</h2>
          <p>Search each movie inline, then write the relationship once the pair feels right.</p>
        </div>
        <form className="stack" onSubmit={submitConnection}>
          <MovieSearchField
            label="From movie"
            helper={fromMoviePicker.selected ? "Selected movie" : "Type at least two characters"}
            value={fromMoviePicker.query}
            results={fromMoviePicker.results.filter((movie) => movie.id !== Number(connectionForm.toMovieId || 0))}
            selected={fromMoviePicker.selected}
            onChange={(value) => void updateMoviePicker("from", value)}
            onSelect={(movie) => chooseMovie("from", movie)}
            onClear={() => clearMovie("from")}
          />
          <MovieSearchField
            label="To movie"
            helper={toMoviePicker.selected ? "Selected movie" : "Search by title or director"}
            value={toMoviePicker.query}
            results={toMoviePicker.results.filter((movie) => movie.id !== Number(connectionForm.fromMovieId || 0))}
            selected={toMoviePicker.selected}
            onChange={(value) => void updateMoviePicker("to", value)}
            onSelect={(movie) => chooseMovie("to", movie)}
            onClear={() => clearMovie("to")}
          />
          {isSelfConnection && <p className="picker-error">A movie cannot connect to itself.</p>}
          <label>
            <span>Reason</span>
            <textarea
              value={connectionForm.reason}
              onChange={(event) => setConnectionForm({ ...connectionForm, reason: event.target.value })}
              required
            />
          </label>
          <label>
            <span>Weight</span>
            <input
              type="number"
              min="0.1"
              step="0.1"
              value={connectionForm.weight}
              onChange={(event) => setConnectionForm({ ...connectionForm, weight: event.target.value })}
            />
          </label>
          <label>
            <span>Category</span>
            <select
              value={connectionForm.category}
              onChange={(event) => setConnectionForm({ ...connectionForm, category: event.target.value as ConnectionCategory | "" })}
            >
              <option value="">Select a graph category</option>
              {CONNECTION_CATEGORY_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
          </label>
          <button className="primary" type="submit" disabled={isSelfConnection}>
            {editingId ? "Update connection" : "Create connection"}
          </button>
        </form>
      </section>

      <section className="rail">
        <div className="panel">
          <div className="panel-header">
            <h2>Your Connections</h2>
            <p>{allConnections.length} total saved links</p>
          </div>
          <div className="list">
            {allConnections.map((connection) => (
              <article className="connection-card" key={connection.id}>
                <strong>{connection.fromMovieTitle} {"->"} {connection.toMovieTitle}</strong>
                <p>{connection.reason}</p>
                <small>{formatConnectionCategory(connection.category)} / weight {connection.weight}</small>
                <div className="pill-row">
                  <button className="pill" onClick={() => startEditing(connection)}>Edit</button>
                  <button className="pill" onClick={() => void removeConnection(connection.id)}>Delete</button>
                </div>
              </article>
            ))}
          </div>
        </div>

        <div className="panel">
          <div className="panel-header">
            <h2>Catalog Sync</h2>
            <p>Load the curated expansion dataset for richer search and movie detail cards.</p>
          </div>
          <button className="primary" type="button" onClick={() => void syncCuratedCatalog()}>
            Sync curated catalog
          </button>
        </div>

        <div className="panel">
          <div className="panel-header">
            <h2>Profile</h2>
            <p>{profile?.email}</p>
          </div>
          <form className="stack" onSubmit={updateProfile}>
            <label>
              <span>Username</span>
              <input
                value={profileDraft.username}
                onChange={(event) => setProfileDraft({ ...profileDraft, username: event.target.value })}
              />
            </label>
            <label>
              <span>New password</span>
              <input
                type="password"
                value={profileDraft.password}
                onChange={(event) => setProfileDraft({ ...profileDraft, password: event.target.value })}
              />
            </label>
            <button className="primary" type="submit">Save profile</button>
          </form>
        </div>
      </section>
    </main>
  );
}

function NetworkPage({ setStatus }: { setStatus: (status: string) => void }) {
  const [query, setQuery] = useState("");
  const deferredQuery = useDeferredValue(query);
  const [searchResults, setSearchResults] = useState<PublicUser[]>([]);
  const [friends, setFriends] = useState<PublicUser[]>([]);
  const [requests, setRequests] = useState<FriendRequests>({ incoming: [], outgoing: [] });

  useEffect(() => {
    void refreshSocial();
  }, []);

  useEffect(() => {
    if (!deferredQuery.trim()) {
      setSearchResults([]);
      return;
    }
    const timeout = window.setTimeout(() => {
      void api.searchUsers(deferredQuery.trim())
        .then((results) => setSearchResults(results))
        .catch((error: Error) => setStatus(error.message));
    }, 180);
    return () => window.clearTimeout(timeout);
  }, [deferredQuery, setStatus]);

  async function refreshSocial() {
    try {
      const [nextFriends, nextRequests] = await Promise.all([api.getFriends(), api.getFriendRequests()]);
      setFriends(nextFriends);
      setRequests(nextRequests);
    } catch (error) {
      setStatus((error as Error).message);
    }
  }

  return (
    <main className="page-stack">
      <section className="panel page-panel">
        <div className="panel-header">
          <h2>Network</h2>
          <p>Search by username, manage mutual friendships, and launch a separate read-only friend graph browser when needed.</p>
        </div>
        <input
          className="search-input"
          placeholder="Find a username"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
        <div className="list">
          {searchResults.map((user) => (
            <article className="connection-card" key={`search-${user.id}`}>
              <strong>{user.username}</strong>
              <div className="pill-row">
                <button className="pill" type="button" onClick={() => void api.sendFriendRequest(user.id).then(refreshSocial).then(() => setStatus(`Sent a friend request to ${user.username}`)).catch((error: Error) => setStatus(error.message))}>
                  Add friend
                </button>
              </div>
            </article>
          ))}
        </div>
      </section>

      <main className="layout">
        <section className="rail">
          <div className="panel">
            <div className="panel-header">
              <h2>Incoming Requests</h2>
              <p>{requests.incoming.length} pending</p>
            </div>
            <div className="list">
              {requests.incoming.map((request) => (
                <article className="connection-card" key={`incoming-${request.id}`}>
                  <strong>{request.otherUser.username}</strong>
                  <div className="pill-row">
                    <button className="pill" onClick={() => void api.acceptFriendRequest(request.id).then(refreshSocial).then(() => setStatus(`Accepted ${request.otherUser.username}`)).catch((error: Error) => setStatus(error.message))}>Accept</button>
                    <button className="pill" onClick={() => void api.declineFriendRequest(request.id).then(refreshSocial).then(() => setStatus(`Declined ${request.otherUser.username}`)).catch((error: Error) => setStatus(error.message))}>Decline</button>
                  </div>
                </article>
              ))}
            </div>
          </div>

          <div className="panel">
            <div className="panel-header">
              <h2>Outgoing Requests</h2>
              <p>{requests.outgoing.length} pending</p>
            </div>
            <div className="list">
              {requests.outgoing.map((request) => (
                <article className="connection-card" key={`outgoing-${request.id}`}>
                  <strong>{request.otherUser.username}</strong>
                </article>
              ))}
            </div>
          </div>

          <div className="panel">
            <div className="panel-header">
              <h2>Friends</h2>
              <p>{friends.length} accepted</p>
            </div>
            <div className="list">
              {friends.map((friend) => (
                <article className="connection-card" key={`friend-${friend.id}`}>
                  <strong>{friend.username}</strong>
                  <div className="pill-row">
                    <button className="pill" type="button" onClick={() => navigateWithEvent(`/friend?user=${friend.id}`)}>Browse graphs</button>
                    <button className="pill" type="button" onClick={() => void api.removeFriend(friend.id).then(refreshSocial).then(() => setStatus(`Removed ${friend.username}`)).catch((error: Error) => setStatus(error.message))}>Remove</button>
                  </div>
                </article>
              ))}
            </div>
          </div>
        </section>

        <section className="panel">
          <div className="panel-header">
            <h2>Friend Graphs</h2>
            <p>Friend browsing lives on its own page so requests, search, and graph inspection do not compete for space.</p>
          </div>
          <div className="stack">
            <p className="detail-copy">Use the friend list to open a dedicated graph browser. That page lets you choose which graph anchor to inspect before rendering anything.</p>
            <p className="empty-state">Network is now only for search, requests, and friend management.</p>
          </div>
        </section>
      </main>
    </main>
  );
}

function MovieSearchField({
  label,
  helper,
  value,
  results,
  selected,
  onChange,
  onSelect,
  onClear,
}: {
  label: string;
  helper: string;
  value: string;
  results: Movie[];
  selected: Movie | null;
  onChange: (value: string) => void;
  onSelect: (movie: Movie) => void;
  onClear: () => void;
}) {
  return (
    <label className="movie-picker">
      <span>{label}</span>
      <input
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder="Find a movie"
        required
      />
      <small className="picker-helper">{helper}</small>
      {selected && (
        <div className="picker-selection">
          <div>
            <strong>{selected.title}</strong>
            <span>{selected.releaseYear || "Year unknown"} / {selected.director || "Director unknown"}</span>
          </div>
          <button className="pill" type="button" onClick={onClear}>Change</button>
        </div>
      )}
      {results.length > 0 && (
        <div className="picker-results">
          {results.map((movie) => (
            <button key={`${label}-${movie.id}`} className="picker-result" type="button" onClick={() => onSelect(movie)}>
              <strong>{movie.title}</strong>
              <span>{movie.releaseYear} / {movie.director}</span>
            </button>
          ))}
        </div>
      )}
      {!selected && value.trim().length > 1 && results.length === 0 && (
        <p className="picker-empty">No close matches yet.</p>
      )}
    </label>
  );
}

function FriendGraphPage({
  authenticatedUsername,
  onLogout,
  status,
  setStatus,
}: {
  authenticatedUsername: string;
  onLogout: () => Promise<void>;
  status: string;
  setStatus: (status: string) => void;
}) {
  const [profile, setProfile] = useState<FriendProfile | null>(null);
  const [collectionQuery, setCollectionQuery] = useState("");
  const deferredCollectionQuery = useDeferredValue(collectionQuery);
  const [selectedMovie, setSelectedMovie] = useState<Movie | null>(null);
  const [componentMovies, setComponentMovies] = useState<Movie[]>([]);
  const [movieConnections, setMovieConnections] = useState<Connection[]>([]);
  const [selectedGraphMovieId, setSelectedGraphMovieId] = useState<number | null>(null);
  const [selectedConnectionId, setSelectedConnectionId] = useState<number | null>(null);
  const [pathFromMovieId, setPathFromMovieId] = useState("");
  const [pathToMovieId, setPathToMovieId] = useState("");
  const [moviePath, setMoviePath] = useState<MoviePath | null>(null);
  const friendUserId = readFriendUserId();

  useEffect(() => {
    if (!friendUserId) {
      setStatus("Missing friend selection");
      return;
    }
    void api.getFriendProfile(friendUserId)
      .then((nextProfile) => {
        setProfile(nextProfile);
        setSelectedMovie(null);
        setComponentMovies([]);
        setMovieConnections([]);
        setSelectedGraphMovieId(null);
        setSelectedConnectionId(null);
        setPathFromMovieId("");
        setPathToMovieId("");
        setMoviePath(null);
        setCollectionQuery("");
        if (nextProfile.movies.length === 0) {
          setStatus(`Opened ${nextProfile.username}. No shared graph components yet.`);
        } else {
          setStatus(`Opened ${nextProfile.username}. Pick a movie to load one of their graph components.`);
        }
      })
      .catch((error: Error) => setStatus(error.message));
  }, [friendUserId, setStatus]);

  async function loadFriendMovie(movie: Movie) {
    if (!friendUserId) {
      setStatus("Missing friend selection");
      return;
    }
    try {
      const data = await api.getFriendMovieConnections(friendUserId, movie.id);
      setSelectedMovie(data.movie);
      setComponentMovies(data.movies ?? [movie]);
      setMovieConnections(data.connections);
      setSelectedGraphMovieId(data.movie.id);
      setSelectedConnectionId(null);
      setPathFromMovieId(String(data.movie.id));
      setPathToMovieId("");
      setMoviePath(null);
      setStatus(`Loaded ${movie.title} from ${profile?.username ?? "your friend"}'s graph`);
    } catch (error) {
      setStatus((error as Error).message);
    }
  }

  async function explainFriendPath() {
    if (!friendUserId || !pathFromMovieId || !pathToMovieId) {
      setStatus("Choose two movies from this friend graph first");
      return;
    }
    try {
      const path = await api.getFriendMoviePath(friendUserId, Number(pathFromMovieId), Number(pathToMovieId));
      setMoviePath(path);
      setSelectedConnectionId(path.connections[0]?.id ?? null);
      setStatus(`Explained the path from ${path.fromMovie.title} to ${path.toMovie.title}`);
    } catch (error) {
      setMoviePath(null);
      setStatus((error as Error).message);
    }
  }

  const selectedGraphMovie = componentMovies.find((movie) => movie.id === selectedGraphMovieId) ?? selectedMovie;
  const selectedConnection = movieConnections.find((connection) => connection.id === selectedConnectionId) ?? null;
  const normalizedCollectionQuery = deferredCollectionQuery.trim().toLowerCase();
  const trimmedCollectionQuery = deferredCollectionQuery.trim();
  const filteredCollectionMovies = (profile?.movies ?? []).filter((movie) => {
    if (!normalizedCollectionQuery) {
      return true;
    }

    return [
      movie.title,
      movie.director,
      String(movie.releaseYear),
    ].some((value) => value.toLowerCase().includes(normalizedCollectionQuery));
  });

  return (
    <div className="screen dashboard friend-shell">
      <header className="masthead friend-masthead">
        <div>
          <p className="eyebrow friend-eyebrow">Friend Archive</p>
          <h1>{profile?.username ?? "Loading friend graph..."}</h1>
          <p className="lede">{profile ? `Browsing ${profile.username}'s movies and saved connections in read-only mode.` : "Preparing a dedicated view for this friend's movie collections and connections."}</p>
        </div>
        <div className="shell-actions">
          <div className="pill-row">
            <button className="pill" type="button" onClick={() => navigateWithEvent("/network")}>Back to network</button>
            <button className="pill" type="button" onClick={() => navigateWithEvent("/explore")}>Return to {authenticatedUsername}</button>
          </div>
          <button className="ghost" onClick={() => void onLogout()}>Logout</button>
        </div>
      </header>

      <main className="page-stack">
        <section className="panel page-panel friend-intro-panel">
          <div className="panel-header">
            <h2>Read-Only Collection</h2>
            <p>Start from a movie in this collection, then inspect the component and explanation trail without mixing it into your own workspace.</p>
          </div>
        </section>
        {profile ? (
          <main className="page-stack">
            <section className="panel friend-collection-panel">
              <div className="panel-header">
                <h2>Movie Collection</h2>
                <p>Choose a movie from this friend's collection to open the connected component around it.</p>
              </div>
              <input
                className="search-input"
                placeholder="Search this collection"
                value={collectionQuery}
                onChange={(event) => setCollectionQuery(event.target.value)}
              />
              {profile.movies.length > 0 ? (
                filteredCollectionMovies.length > 0 ? (
                  <div className="movie-grid">
                    {filteredCollectionMovies.map((movie) => (
                      <button key={`friend-movie-${movie.id}`} className="movie-card" onClick={() => void loadFriendMovie(movie)}>
                        <strong>{movie.title}</strong>
                        <span>{movie.releaseYear}</span>
                        <span>{movie.director}</span>
                      </button>
                    ))}
                  </div>
                ) : (
                  <p className="empty-state">
                    {`No existing connection for "${trimmedCollectionQuery}" in ${profile.username}'s collection.`}
                  </p>
                )
              ) : (
                <p className="empty-state">This friend has no connected movies to browse yet.</p>
              )}
            </section>

            <section className="panel friend-viewer-panel">
              <div className="panel-header">
                <h2>Connections</h2>
                <p>{selectedMovie ? `${selectedMovie.title} and its connected component` : "Choose a movie from the collection to load this friend's connections."}</p>
              </div>
              {selectedMovie ? (
                <div className="graph-inspector-grid">
                  <div className="graph-frame-stack">
                    <ConnectionGraph
                      movie={selectedMovie}
                      connections={movieConnections}
                      selectedMovieId={selectedGraphMovieId}
                      selectedConnectionId={selectedConnectionId}
                      onMovieSelect={(movieId) => {
                        setSelectedGraphMovieId(movieId);
                        setSelectedConnectionId(null);
                      }}
                      onConnectionSelect={(connectionId) => setSelectedConnectionId(connectionId)}
                    />
                  </div>
                  <div className="graph-detail-stack">
                    <section className="panel inset-panel">
                      <div className="panel-header">
                        <h3>Movie Detail</h3>
                        <p>{selectedGraphMovie?.title ?? "Select a node"}</p>
                      </div>
                      {selectedGraphMovie ? (
                        <div className="stack">
                          <p className="detail-stat"><strong>{selectedGraphMovie.releaseYear}</strong> / {selectedGraphMovie.director}</p>
                          {selectedGraphMovie.tagline && <p className="detail-copy">{selectedGraphMovie.tagline}</p>}
                          {selectedGraphMovie.synopsis && <p className="detail-copy">{selectedGraphMovie.synopsis}</p>}
                          <div className="pill-row">
                            <button className="pill" onClick={() => setPathFromMovieId(String(selectedGraphMovie.id))}>Use as path start</button>
                            <button className="pill" onClick={() => setPathToMovieId(String(selectedGraphMovie.id))}>Use as path end</button>
                          </div>
                        </div>
                      ) : <p className="empty-state">Click a node to inspect it.</p>}
                    </section>

                    <section className="panel inset-panel">
                      <div className="panel-header">
                        <h3>Connection Detail</h3>
                        <p>{selectedConnection ? `${selectedConnection.fromMovieTitle} -> ${selectedConnection.toMovieTitle}` : "Select an edge"}</p>
                      </div>
                      {selectedConnection ? (
                        <div className="stack">
                          <p>{selectedConnection.reason}</p>
                          <small>{formatConnectionCategory(selectedConnection.category)} / weight {selectedConnection.weight}</small>
                        </div>
                      ) : <p className="empty-state">This page is read-only. Select an edge to inspect why those films connect.</p>}
                    </section>

                    <section className="panel inset-panel">
                      <div className="panel-header">
                        <h3>Path Explanation</h3>
                        <p>Explain the shortest path inside this friend graph.</p>
                      </div>
                      <div className="stack">
                        <label>
                          <span>From movie</span>
                          <select value={pathFromMovieId} onChange={(event) => setPathFromMovieId(event.target.value)}>
                            <option value="">Select a movie</option>
                            {componentMovies.map((movie) => (
                              <option key={`friend-path-from-${movie.id}`} value={movie.id}>{movie.title}</option>
                            ))}
                          </select>
                        </label>
                        <label>
                          <span>To movie</span>
                          <select value={pathToMovieId} onChange={(event) => setPathToMovieId(event.target.value)}>
                            <option value="">Select a movie</option>
                            {componentMovies.map((movie) => (
                              <option key={`friend-path-to-${movie.id}`} value={movie.id}>{movie.title}</option>
                            ))}
                          </select>
                        </label>
                        <button className="primary" type="button" onClick={() => void explainFriendPath()}>Explain shortest path</button>
                        {moviePath ? <strong>{moviePath.movies.map((movie) => movie.title).join(" -> ")}</strong> : null}
                      </div>
                    </section>
                  </div>
                </div>
              ) : (
                <p className="empty-state">Choose one of this friend's movies to load their connections.</p>
              )}
            </section>
          </main>
        ) : (
          <section className="panel">
            <p className="empty-state">Loading friend data...</p>
          </section>
        )}
      </main>

      {status && <footer className="status-bar">{status}</footer>}
    </div>
  );
}

function SharedGraphPage({ authenticatedUsername }: { authenticatedUsername: string | null }) {
  const [graph, setGraph] = useState<SharedGraph | null>(null);
  const [status, setStatus] = useState("Loading shared graph...");
  const [selectedMovieId, setSelectedMovieId] = useState<number | null>(null);
  const [selectedConnectionId, setSelectedConnectionId] = useState<number | null>(null);

  useEffect(() => {
    const shareToken = readShareToken();
    if (!shareToken) {
      setStatus("Missing shared graph token");
      return;
    }
    void api.getSharedGraph(shareToken)
      .then((data) => {
        setGraph(data);
        setSelectedMovieId(data.graph.movie.id);
        setStatus("");
      })
      .catch((error: Error) => setStatus(error.message));
  }, []);

  const selectedMovie = graph?.graph.movies.find((movie) => movie.id === selectedMovieId) ?? graph?.graph.movie ?? null;
  const selectedConnection = graph?.graph.connections.find((connection) => connection.id === selectedConnectionId) ?? null;

  return (
    <div className="screen dashboard">
      <header className="masthead">
        <div>
          <p className="eyebrow">Shared Graph</p>
          <h1>{graph?.title ?? "Shared view"}</h1>
        </div>
        <div className="shell-actions">
          {authenticatedUsername ? (
            <button className="pill" onClick={() => navigateWithEvent("/explore")}>Return to workspace</button>
          ) : (
            <button className="pill" onClick={() => navigateWithEvent("/")}>Back to auth</button>
          )}
        </div>
      </header>

      <main className="page-stack">
        <section className="panel page-panel graph-panel">
          <div className="panel-header">
            <h2>{graph?.graph.movie.title ?? "Shared graph"}</h2>
            <p>Read-only export of a saved movie component.</p>
          </div>
          {graph ? (
            <div className="graph-inspector-grid">
              <div className="graph-frame-stack">
                <ConnectionGraph
                  movie={graph.graph.movie}
                  connections={graph.graph.connections}
                  selectedMovieId={selectedMovieId}
                  selectedConnectionId={selectedConnectionId}
                  onMovieSelect={(movieId) => {
                    setSelectedMovieId(movieId);
                    setSelectedConnectionId(null);
                  }}
                  onConnectionSelect={(connectionId) => setSelectedConnectionId(connectionId)}
                />
              </div>
              <div className="graph-detail-stack">
                <section className="panel inset-panel">
                  <div className="panel-header">
                    <h3>Movie detail</h3>
                    <p>{selectedMovie?.title ?? "Select a node"}</p>
                  </div>
                  {selectedMovie && (
                    <div className="stack">
                      <p className="detail-stat"><strong>{selectedMovie.releaseYear}</strong> / {selectedMovie.director}</p>
                      {selectedMovie.tagline && <p className="detail-copy">{selectedMovie.tagline}</p>}
                      {selectedMovie.synopsis && <p className="detail-copy">{selectedMovie.synopsis}</p>}
                      {(selectedMovie.genres ?? []).length > 0 && <p className="detail-stat">Genres: {(selectedMovie.genres ?? []).join(", ")}</p>}
                      {(selectedMovie.castMembers ?? []).length > 0 && <p className="detail-stat">Cast: {(selectedMovie.castMembers ?? []).join(", ")}</p>}
                    </div>
                  )}
                </section>
                <section className="panel inset-panel">
                  <div className="panel-header">
                    <h3>Connection detail</h3>
                    <p>{selectedConnection ? `${selectedConnection.fromMovieTitle} -> ${selectedConnection.toMovieTitle}` : "Select an edge"}</p>
                  </div>
                  {selectedConnection ? (
                    <div className="stack">
                      <p>{selectedConnection.reason}</p>
                      <small>{formatConnectionCategory(selectedConnection.category)} / weight {selectedConnection.weight}</small>
                    </div>
                  ) : (
                    <p className="empty-state">This view is read-only, but you can still inspect why films connect.</p>
                  )}
                </section>
              </div>
            </div>
          ) : (
            <p className="empty-state">{status}</p>
          )}
        </section>
      </main>

      {status && <footer className="status-bar">{status}</footer>}
    </div>
  );
}

function VerifyPage() {
  const [status, setStatus] = useState("Verifying account...");

  useEffect(() => {
    const token = new URLSearchParams(window.location.search).get("token");
    if (!token) {
      setStatus("Missing verification token");
      return;
    }
    void api.verifyAccount(token)
      .then(() => setStatus("Account verified. You can return to login."))
      .catch((error: Error) => setStatus(error.message));
  }, []);

  return <div className="screen shell-center">{status}</div>;
}

function ResetPasswordPage() {
  const [status, setStatus] = useState("");
  const [password, setPassword] = useState("");

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const token = new URLSearchParams(window.location.search).get("token");
    if (!token) {
      setStatus("Missing reset token");
      return;
    }
    try {
      await api.resetPassword({ token, newPassword: password });
      setStatus("Password reset. Return to login.");
    } catch (error) {
      setStatus((error as Error).message);
    }
  }

  return (
    <div className="screen auth-screen">
      <section className="auth-card">
        <h1>Reset password</h1>
        <form className="stack" onSubmit={handleSubmit}>
          <label>
            <span>New password</span>
            <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} />
          </label>
          <button className="primary" type="submit">Reset</button>
        </form>
        {status && <p className="status">{status}</p>}
      </section>
    </div>
  );
}

function readPathname(): AppPath {
  const pathname = window.location.pathname;
  if (pathname.startsWith("/shared/")) {
    return "/shared";
  }
  if (pathname === "/explore" || pathname === "/connections" || pathname === "/network" || pathname === "/friend" || pathname === "/verify" || pathname === "/reset-password") {
    return pathname;
  }
  return "/";
}

function navigateTo(path: AppPath, setPathname: (path: AppPath) => void) {
  if (window.location.pathname !== path) {
    window.history.pushState({}, "", path);
  }
  setPathname(path);
}

function clearConnectionsRouteSearch() {
  if (window.location.pathname !== "/connections" || !window.location.search) {
    return;
  }
  window.history.replaceState({}, "", "/connections");
}

function readShareToken() {
  const match = window.location.pathname.match(/^\/shared\/([^/]+)$/);
  return match?.[1] ?? "";
}

function readFriendUserId() {
  const value = new URLSearchParams(window.location.search).get("user");
  const id = Number(value);
  return Number.isFinite(id) && id > 0 ? id : null;
}

function navigateWithEvent(path: string) {
  window.history.pushState({}, "", path);
  window.dispatchEvent(new PopStateEvent("popstate"));
}
