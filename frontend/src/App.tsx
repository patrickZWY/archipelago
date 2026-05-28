import { startTransition, useDeferredValue, useEffect, useState, type FormEvent, type ReactNode } from "react";
import { ConnectionGraph } from "./components/ConnectionGraph";
import { ApiError, api } from "./lib/api";
import type { Connection, Movie, SessionResponse, UserProfile } from "./lib/types";

type AuthMode = "login" | "register" | "forgot";
type AppPath = "/" | "/explore" | "/connections" | "/verify" | "/reset-password";

type ConnectionForm = {
  fromMovieId: string;
  toMovieId: string;
  reason: string;
  weight: string;
  category: string;
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
  const [status, setStatus] = useState("Booting session...");
  const [loading, setLoading] = useState(true);
  const [pathname, setPathname] = useState<AppPath>(readPathname());

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

  useEffect(() => {
    const onPopState = () => setPathname(readPathname());
    window.addEventListener("popstate", onPopState);
    return () => window.removeEventListener("popstate", onPopState);
  }, []);

  useEffect(() => {
    if (loading || !session?.authenticated || !session.user) {
      return;
    }
    if (pathname === "/" || pathname === "/verify" || pathname === "/reset-password") {
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

  const currentPath = pathname === "/connections" ? "/connections" : "/explore";

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
      {currentPath === "/connections" ? (
        <ConnectionsPage setStatus={setStatus} />
      ) : (
        <ExplorePage setStatus={setStatus} />
      )}
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

  return (
    <div className="screen auth-screen">
      <section className="auth-card">
        <p className="eyebrow">Archipelago</p>
        <h1>Map how films connect.</h1>
        <p className="lede">
          Search one movie at a time, inspect only your saved links for it, then create or revise
          two-movie connections in a separate workspace.
        </p>
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
  currentPath: "/explore" | "/connections";
  onNavigate: (path: "/explore" | "/connections") => void;
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
  const [movieConnections, setMovieConnections] = useState<Connection[]>([]);

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
      setMovieConnections(data.connections);
      setStatus(`Loaded your connections for ${movie.title}`);
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
        <ConnectionGraph movie={selectedMovie} connections={movieConnections} />
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
    } catch (error) {
      setStatus((error as Error).message);
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
      },
    });
  }

  function resetEditor() {
    setEditingId(null);
    setConnectionForm(emptyConnectionForm);
    setFromMoviePicker(emptyMoviePickerState);
    setToMoviePicker(emptyMoviePickerState);
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
            <input
              value={connectionForm.category}
              onChange={(event) => setConnectionForm({ ...connectionForm, category: event.target.value })}
            />
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
                <small>{connection.category ?? "uncategorized"} / weight {connection.weight}</small>
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
  if (pathname === "/explore" || pathname === "/connections" || pathname === "/verify" || pathname === "/reset-password") {
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
