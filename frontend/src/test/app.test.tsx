import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "../App";

const graphProps = vi.hoisted(() => ({
  last: null as null | {
    movie: { id: number; title: string } | null;
    connections: Array<{ id: number; fromMovieTitle?: string; toMovieTitle?: string }>;
    dimMovieIds?: number[];
    dimConnectionIds?: number[];
    onConnectionSelect?: (connectionId: number) => void;
    onMovieSelect?: (movieId: number) => void;
  },
}));

vi.mock("../components/ConnectionGraph", () => ({
  ConnectionGraph: (props: {
    movie: { id: number; title: string } | null;
    connections: Array<{ id: number; fromMovieTitle?: string; toMovieTitle?: string }>;
    dimMovieIds?: number[];
    dimConnectionIds?: number[];
    onConnectionSelect?: (connectionId: number) => void;
    onMovieSelect?: (movieId: number) => void;
  }) => {
    graphProps.last = props;
    return (
      <div>
        <div>Graph Stub</div>
        {props.movie ? <button onClick={() => props.onMovieSelect?.(props.movie!.id)}>Select Current Movie</button> : null}
        {props.connections.map((connection) => (
          <button key={`stub-connection-${connection.id}`} onClick={() => props.onConnectionSelect?.(connection.id)}>
            {`Select connection ${connection.id}`}
          </button>
        ))}
      </div>
    );
  },
}));

describe("App", () => {
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=test-token";
    window.history.pushState({}, "", "/");
  });

  afterEach(() => {
    vi.restoreAllMocks();
    graphProps.last = null;
  });

  it("boots into auth mode when there is no authenticated session", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => ({
      ok: true,
      json: async () => ({ success: true, data: { authenticated: false, user: null }, message: "ok" }),
    })));

    render(<App />);

    expect(await screen.findByText("Map how films connect.")).toBeInTheDocument();
  });

  it("opens the seeded demo session from the auth screen", async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith("/api/auth/session")) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: { authenticated: false, user: null },
            message: "ok",
          }),
        };
      }
      if (url.endsWith("/api/auth/demo") && init?.method === "POST") {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: { authenticated: true, user: { id: 99, email: "demo@archipelago.local", username: "demo" } },
            message: "Demo session opened",
          }),
        };
      }
      throw new Error(`Unexpected fetch: ${url}`);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);

    await screen.findByText("Map how films connect.");
    await userEvent.click(screen.getByRole("button", { name: "Enter demo" }));

    expect(await screen.findByText("Explore Movie Graphs")).toBeInTheDocument();
    expect(screen.getByText("demo")).toBeInTheDocument();
  });

  it("shows login errors inline when credentials are invalid", async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith("/api/auth/session")) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: { authenticated: false, user: null },
            message: "ok",
          }),
        };
      }
      if (url.endsWith("/api/auth/login") && init?.method === "POST") {
        return {
          ok: false,
          json: async () => ({
            success: false,
            data: null,
            message: "Invalid email or password",
          }),
        };
      }
      throw new Error("Unexpected fetch");
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);

    await screen.findByText("Map how films connect.");
    await userEvent.type(screen.getByLabelText("Email"), "missing@example.com");
    await userEvent.type(screen.getByLabelText(/Password/i), "wrongpass");
    await userEvent.click(screen.getByRole("button", { name: "Enter workspace" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Invalid email or password");
  });

  it("shows validation feedback on register failures", async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/api/auth/session")) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: { authenticated: false, user: null },
            message: "ok",
          }),
        };
      }
      if (url.endsWith("/api/auth/register")) {
        return {
          ok: false,
          json: async () => ({
            success: false,
            data: {
              password: "Password must be between 8 and 128 characters long",
            },
            message: "Validation failed",
          }),
        };
      }
      throw new Error("Unexpected fetch");
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);

    await screen.findByText("Map how films connect.");
    await userEvent.click(screen.getByRole("button", { name: "Register" }));
    await userEvent.type(screen.getByLabelText("Email"), "new@example.com");
    await userEvent.type(screen.getByLabelText(/Password/i), "short");
    await userEvent.type(screen.getByLabelText(/Username/i), "new-user");
    await userEvent.click(screen.getByRole("button", { name: "Create account" }));

    expect(screen.getByText("Password must be between 8 and 128 characters long", { selector: "small" })).toBeInTheDocument();
  });

  it("shows duplicate register errors inline", async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/api/auth/session")) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: { authenticated: false, user: null },
            message: "ok",
          }),
        };
      }
      if (url.endsWith("/api/auth/register")) {
        return {
          ok: false,
          json: async () => ({
            success: false,
            data: null,
            message: "Email already exists",
          }),
        };
      }
      throw new Error("Unexpected fetch");
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);

    await screen.findByText("Map how films connect.");
    await userEvent.click(screen.getByRole("button", { name: "Register" }));
    await userEvent.type(screen.getByLabelText("Email"), "helloworld@gmail.com");
    await userEvent.type(screen.getByLabelText(/Password/i), "helloworld");
    await userEvent.type(screen.getByLabelText(/Username/i), "helloworld");
    await userEvent.click(screen.getByRole("button", { name: "Create account" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Email already exists");
  });

  it("shows forgot-password field errors inline", async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/api/auth/session")) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: { authenticated: false, user: null },
            message: "ok",
          }),
        };
      }
      if (url.endsWith("/api/auth/forgot-password")) {
        return {
          ok: false,
          json: async () => ({
            success: false,
            data: { email: "Email must be valid" },
            message: "Validation failed",
          }),
        };
      }
      throw new Error("Unexpected fetch");
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);

    await screen.findByText("Map how films connect.");
    await userEvent.click(screen.getByRole("button", { name: "Forgot" }));
    await userEvent.type(screen.getByLabelText("Email"), "missing@example.com");
    await userEvent.click(screen.getByRole("button", { name: "Send reset link" }));

    expect(await screen.findByText("Email must be valid", { selector: "small" })).toBeInTheDocument();
  });

  it("shows reset-password token and password failures inline", async () => {
    window.history.pushState({}, "", "/reset-password?token=expired-token");
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith("/api/auth/session")) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: { authenticated: false, user: null },
            message: "ok",
          }),
        };
      }
      if (url.endsWith("/api/auth/reset-password") && init?.method === "POST") {
        const body = JSON.parse(String(init.body));
        if (body.newPassword === "short") {
          return {
            ok: false,
            json: async () => ({
              success: false,
              data: {
                newPassword: "New password must be between 8 and 128 characters long",
              },
              message: "Validation failed",
            }),
          };
        }
        return {
          ok: false,
          json: async () => ({
            success: false,
            data: null,
            message: "Reset token has expired",
          }),
        };
      }
      throw new Error(`Unexpected fetch: ${url}`);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);

    await screen.findByText("Reset password");
    const resetPasswordInput = screen.getByText("New password").closest("label")?.querySelector("input");
    expect(resetPasswordInput).not.toBeNull();
    await userEvent.type(resetPasswordInput as HTMLInputElement, "short");
    await userEvent.click(screen.getByRole("button", { name: "Reset" }));
    expect(await screen.findByText("New password must be between 8 and 128 characters long", { selector: "small" })).toBeInTheDocument();

    await userEvent.clear(resetPasswordInput as HTMLInputElement);
    await userEvent.type(resetPasswordInput as HTMLInputElement, "newsecret123");
    await userEvent.click(screen.getByRole("button", { name: "Reset" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Reset token has expired");
  });

  it("submits register even when the csrf cookie is not URI-decodable", async () => {
    document.cookie = "XSRF-TOKEN=bad%token";
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/api/auth/session")) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: { authenticated: false, user: null },
            message: "ok",
          }),
        };
      }
      if (url.endsWith("/api/auth/register")) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: {
              authenticated: true,
              user: { id: 1, email: "helloworld@gmail.com", username: "helloworld" },
            },
            message: "Registration successful",
          }),
        };
      }
      throw new Error("Unexpected fetch");
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);

    await screen.findByText("Map how films connect.");
    await userEvent.click(screen.getByRole("button", { name: "Register" }));
    await userEvent.type(screen.getByLabelText("Email"), "helloworld@gmail.com");
    await userEvent.type(screen.getByLabelText(/Password/i), "helloworld");
    await userEvent.type(screen.getByLabelText(/Username/i), "helloworld");
    await userEvent.click(screen.getByRole("button", { name: "Create account" }));

    expect(await screen.findByText("Explore Movie Graphs")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/auth/register",
      expect.objectContaining({
        method: "POST",
        headers: expect.any(Headers),
      }),
    );
  });

  it("renders explore after session bootstrap", async () => {
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/api/auth/session")) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: { authenticated: true, user: { id: 1, email: "a@example.com", username: "atlas" } },
            message: "ok",
          }),
        };
      }
      throw new Error(`Unexpected fetch: ${url}`);
    }));

    render(<App />);

    expect(await screen.findByText("Explore Movie Graphs")).toBeInTheDocument();
    expect(screen.getByText("atlas")).toBeInTheDocument();
  });

  it("submits the profile form", async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith("/api/auth/session")) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: { authenticated: true, user: { id: 1, email: "a@example.com", username: "atlas" } },
            message: "ok",
          }),
        };
      }
      if (url.endsWith("/api/connections")) {
        return { ok: true, json: async () => ({ success: true, data: [], message: "ok" }) };
      }
      if (url.endsWith("/api/users/profile") && init?.method === "PUT") {
        return { ok: true, json: async () => ({ success: true, data: null, message: "updated" }) };
      }
      return {
        ok: true,
        json: async () => ({
          success: true,
          data: { id: 1, username: "atlas", email: "a@example.com", enabled: true, verified: true },
          message: "ok",
        }),
      };
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);

    await userEvent.click(await screen.findByRole("button", { name: "Connections" }));
    await screen.findByText("Profile");
    const usernameInput = screen.getByDisplayValue("atlas");
    await userEvent.clear(usernameInput);
    await userEvent.type(usernameInput, "atlas-updated");
    await userEvent.click(screen.getByText("Save profile"));

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        "/api/users/profile",
        expect.objectContaining({ method: "PUT", credentials: "include" }),
      ),
    );
  });

  it("renders the network workspace and sends a friend request", async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith("/api/auth/session")) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: { authenticated: true, user: { id: 1, email: "a@example.com", username: "atlas" } },
            message: "ok",
          }),
        };
      }
      if (url.endsWith("/api/friends")) {
        return { ok: true, json: async () => ({ success: true, data: [], message: "ok" }) };
      }
      if (url.endsWith("/api/friends/requests") && !init?.method) {
        return { ok: true, json: async () => ({ success: true, data: { incoming: [], outgoing: [] }, message: "ok" }) };
      }
      if (url.endsWith("/api/users/search?q=arch")) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: [{ id: 7, username: "arch-friend" }],
            message: "ok",
          }),
        };
      }
      if (url.endsWith("/api/friends/requests") && init?.method === "POST") {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: {
              id: 1,
              status: "PENDING",
              requester: { id: 1, username: "atlas" },
              recipient: { id: 7, username: "arch-friend" },
              otherUser: { id: 7, username: "arch-friend" },
            },
            message: "ok",
          }),
        };
      }
      throw new Error(`Unexpected fetch: ${url}`);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);

    await userEvent.click(await screen.findByRole("button", { name: "Network" }));
    await userEvent.type(screen.getByPlaceholderText("Find a username"), "arch");
    expect(await screen.findByText("arch-friend")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Add friend" }));

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        "/api/friends/requests",
        expect.objectContaining({ method: "POST", credentials: "include" }),
      ),
    );
  });

  it("adds the global graphs workspace and switches scope-specific API calls", async () => {
    const meMovie = {
      id: 1,
      title: "Inception",
      releaseYear: 2010,
      director: "Christopher Nolan",
      pictureUrl: null,
      externalId: null,
      tagline: null,
      synopsis: null,
      genres: [],
      runtimeMinutes: null,
      castMembers: [],
      directorNotes: null,
    };
    const friendMovie = {
      ...meMovie,
      id: 2,
      title: "Interstellar",
    };
    const aggregateMovie = {
      ...meMovie,
      id: 3,
      title: "Arrival",
      director: "Denis Villeneuve",
      releaseYear: 2016,
    };
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/api/auth/session")) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: { authenticated: true, user: { id: 1, email: "a@example.com", username: "atlas" } },
            message: "ok",
          }),
        };
      }
      if (url.endsWith("/api/global-graphs/friends")) {
        return {
          ok: true,
          json: async () => ({ success: true, data: [{ id: 7, username: "arch-friend" }], message: "ok" }),
        };
      }
      if (url.endsWith("/api/global-graphs/me")) {
        return {
          ok: true,
          json: async () => ({ success: true, data: { movies: [meMovie], connections: [] }, message: "ok" }),
        };
      }
      if (url.endsWith("/api/global-graphs/friends/7")) {
        return {
          ok: true,
          json: async () => ({ success: true, data: { movies: [friendMovie], connections: [] }, message: "ok" }),
        };
      }
      if (url.endsWith("/api/global-graphs/friends/aggregate")) {
        return {
          ok: true,
          json: async () => ({ success: true, data: { movies: [aggregateMovie], connections: [] }, message: "ok" }),
        };
      }
      throw new Error(`Unexpected fetch: ${url}`);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);

    await userEvent.click(await screen.findByRole("button", { name: "Global Graphs" }));
    expect(await screen.findByText("Full Graph Canvas")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith("/api/global-graphs/me", expect.anything());
    expect(screen.queryByLabelText("Friend picker")).not.toBeInTheDocument();

    await userEvent.selectOptions(screen.getByLabelText("Graph scope"), "friend");
    expect(await screen.findByLabelText("Friend picker")).toBeInTheDocument();
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith("/api/global-graphs/friends/7", expect.anything()));

    await userEvent.selectOptions(screen.getByLabelText("Graph scope"), "all-friends");
    expect(screen.queryByLabelText("Friend picker")).not.toBeInTheDocument();
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith("/api/global-graphs/friends/aggregate", expect.anything()));
  });

  it("renders merged friend contributor metadata in the global graphs workspace", async () => {
    const aggregateMovies = [
      {
        id: 1,
        title: "Inception",
        releaseYear: 2010,
        director: "Christopher Nolan",
        pictureUrl: null,
        externalId: null,
        tagline: null,
        synopsis: null,
        genres: [],
        runtimeMinutes: null,
        castMembers: [],
        directorNotes: null,
      },
      {
        id: 2,
        title: "Arrival",
        releaseYear: 2016,
        director: "Denis Villeneuve",
        pictureUrl: null,
        externalId: null,
        tagline: null,
        synopsis: null,
        genres: [],
        runtimeMinutes: null,
        castMembers: [],
        directorNotes: null,
      },
    ];
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/api/auth/session")) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: { authenticated: true, user: { id: 1, email: "a@example.com", username: "atlas" } },
            message: "ok",
          }),
        };
      }
      if (url.endsWith("/api/global-graphs/friends")) {
        return {
          ok: true,
          json: async () => ({ success: true, data: [{ id: 7, username: "arch-friend" }], message: "ok" }),
        };
      }
      if (url.endsWith("/api/global-graphs/me")) {
        return { ok: true, json: async () => ({ success: true, data: { movies: [], connections: [] }, message: "ok" }) };
      }
      if (url.endsWith("/api/global-graphs/friends/aggregate")) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: {
              movies: aggregateMovies,
              connections: [{
                id: 99,
                fromMovieId: 1,
                fromMovieTitle: "Inception",
                toMovieId: 2,
                toMovieTitle: "Arrival",
                reason: "Merged from 2 friend graphs",
                weight: 2.4,
                category: "director",
                aggregate: true,
                contributors: [{ id: 7, username: "arch-friend" }, { id: 8, username: "cine-pal" }],
                contributorCount: 2,
              }],
            },
            message: "ok",
          }),
        };
      }
      throw new Error(`Unexpected fetch: ${url}`);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);

    await userEvent.click(await screen.findByRole("button", { name: "Global Graphs" }));
    await userEvent.selectOptions(screen.getByLabelText("Graph scope"), "all-friends");
    await waitFor(() => expect(graphProps.last?.connections).toHaveLength(1));
    await userEvent.click(screen.getByRole("button", { name: "Select connection 99" }));

    expect(await screen.findByText("Contributors: 2")).toBeInTheDocument();
    expect(screen.getByText("Friend sources: arch-friend, cine-pal")).toBeInTheDocument();
  });

  it("dims non-focused contributor edges in the merged global graph view", async () => {
    const aggregateMovies = [
      {
        id: 1,
        title: "Inception",
        releaseYear: 2010,
        director: "Christopher Nolan",
        pictureUrl: null,
        externalId: null,
        tagline: null,
        synopsis: null,
        genres: [],
        runtimeMinutes: null,
        castMembers: [],
        directorNotes: null,
      },
      {
        id: 2,
        title: "Arrival",
        releaseYear: 2016,
        director: "Denis Villeneuve",
        pictureUrl: null,
        externalId: null,
        tagline: null,
        synopsis: null,
        genres: [],
        runtimeMinutes: null,
        castMembers: [],
        directorNotes: null,
      },
      {
        id: 3,
        title: "Heat",
        releaseYear: 1995,
        director: "Michael Mann",
        pictureUrl: null,
        externalId: null,
        tagline: null,
        synopsis: null,
        genres: [],
        runtimeMinutes: null,
        castMembers: [],
        directorNotes: null,
      },
    ];
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/api/auth/session")) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: { authenticated: true, user: { id: 1, email: "a@example.com", username: "atlas" } },
            message: "ok",
          }),
        };
      }
      if (url.endsWith("/api/global-graphs/friends")) {
        return {
          ok: true,
          json: async () => ({ success: true, data: [{ id: 7, username: "arch-friend" }], message: "ok" }),
        };
      }
      if (url.endsWith("/api/global-graphs/me")) {
        return { ok: true, json: async () => ({ success: true, data: { movies: [], connections: [] }, message: "ok" }) };
      }
      if (url.endsWith("/api/global-graphs/friends/aggregate")) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: {
              movies: aggregateMovies,
              connections: [
                {
                  id: 99,
                  fromMovieId: 1,
                  fromMovieTitle: "Inception",
                  toMovieId: 2,
                  toMovieTitle: "Arrival",
                  reason: "Merged from 2 friend graphs",
                  weight: 2.4,
                  category: "director",
                  aggregate: true,
                  contributors: [{ id: 7, username: "arch-friend" }, { id: 8, username: "cine-pal" }],
                  contributorCount: 2,
                },
                {
                  id: 100,
                  fromMovieId: 2,
                  fromMovieTitle: "Arrival",
                  toMovieId: 3,
                  toMovieTitle: "Heat",
                  reason: "Only cine-pal contributed this edge",
                  weight: 1.2,
                  category: "tone",
                  aggregate: true,
                  contributors: [{ id: 8, username: "cine-pal" }],
                  contributorCount: 1,
                },
              ],
            },
            message: "ok",
          }),
        };
      }
      throw new Error(`Unexpected fetch: ${url}`);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);

    await userEvent.click(await screen.findByRole("button", { name: "Global Graphs" }));
    await userEvent.selectOptions(screen.getByLabelText("Graph scope"), "all-friends");
    await waitFor(() => expect(graphProps.last?.connections).toHaveLength(2));

    await userEvent.click(screen.getByRole("button", { name: "arch-friend" }));

    await waitFor(() => expect(graphProps.last?.dimConnectionIds).toEqual([100]));
    expect(graphProps.last?.dimMovieIds).toEqual([3]);
  });

  it("opens a dedicated friend graph page from the network workspace", async () => {
    const friendMovie = {
      id: 2,
      title: "Interstellar",
      releaseYear: 2014,
      director: "Christopher Nolan",
      pictureUrl: null,
      externalId: null,
      tagline: null,
      synopsis: null,
      genres: [],
      runtimeMinutes: null,
      castMembers: [],
      directorNotes: null,
    };
    const otherFriendMovie = {
      id: 5,
      title: "Arrival",
      releaseYear: 2016,
      director: "Denis Villeneuve",
      pictureUrl: null,
      externalId: null,
      tagline: null,
      synopsis: null,
      genres: [],
      runtimeMinutes: null,
      castMembers: [],
      directorNotes: null,
    };
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith("/api/auth/session")) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: { authenticated: true, user: { id: 1, email: "a@example.com", username: "atlas" } },
            message: "ok",
          }),
        };
      }
      if (url.endsWith("/api/friends")) {
        return {
          ok: true,
          json: async () => ({ success: true, data: [{ id: 7, username: "arch-friend" }], message: "ok" }),
        };
      }
      if (url.endsWith("/api/friends/requests") && !init?.method) {
        return { ok: true, json: async () => ({ success: true, data: { incoming: [], outgoing: [] }, message: "ok" }) };
      }
      if (url.endsWith("/api/friends/7/profile")) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: { id: 7, username: "arch-friend", movies: [friendMovie, otherFriendMovie] },
            message: "ok",
          }),
        };
      }
      if (url.endsWith("/api/friends/7/movies/2/connections")) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: { movie: friendMovie, movies: [friendMovie], connections: [] },
            message: "ok",
          }),
        };
      }
      throw new Error(`Unexpected fetch: ${url}`);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);

    await userEvent.click(await screen.findByRole("button", { name: "Network" }));
    await userEvent.click(await screen.findByRole("button", { name: "Browse graphs" }));

    expect(await screen.findByText("arch-friend")).toBeInTheDocument();
    expect(await screen.findByText("Browsing arch-friend's movies and saved connections in read-only mode.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Return to atlas" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Explore" })).not.toBeInTheDocument();
    await userEvent.type(screen.getByPlaceholderText("Search this collection"), "Inter");
    expect(screen.getByRole("button", { name: /Interstellar/i })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Arrival/i })).not.toBeInTheDocument();
    await userEvent.clear(screen.getByPlaceholderText("Search this collection"));
    await userEvent.type(screen.getByPlaceholderText("Search this collection"), "Stalker");
    expect(screen.getByText('No existing connection for "Stalker" in arch-friend\'s collection.')).toBeInTheDocument();

    await userEvent.clear(screen.getByPlaceholderText("Search this collection"));
    await userEvent.type(screen.getByPlaceholderText("Search this collection"), "Inter");
    await userEvent.click(screen.getByRole("button", { name: /Interstellar/i }));

    await waitFor(() => {
      expect(graphProps.last?.movie?.id).toBe(2);
      expect(window.location.pathname).toBe("/friend");
      expect(window.location.search).toBe("?user=7");
    });
  });

  it("blocks choosing the same movie on both sides of a connection", async () => {
    const movie = {
      id: 7,
      title: "Interstellar",
      releaseYear: 2014,
      director: "Christopher Nolan",
      pictureUrl: null,
      externalId: null,
    };
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/api/auth/session")) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: { authenticated: true, user: { id: 1, email: "a@example.com", username: "atlas" } },
            message: "ok",
          }),
        };
      }
      if (url.includes("/api/movies/search")) {
        return { ok: true, json: async () => ({ success: true, data: [movie], message: "ok" }) };
      }
      if (url.endsWith("/api/connections")) {
        return { ok: true, json: async () => ({ success: true, data: [], message: "ok" }) };
      }
      return {
        ok: true,
        json: async () => ({
          success: true,
          data: { id: 1, username: "atlas", email: "a@example.com", enabled: true, verified: true },
          message: "ok",
        }),
      };
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);

    await userEvent.click(await screen.findByRole("button", { name: "Connections" }));
    await screen.findByText("Connection Editor");
    const [fromInput, toInput] = screen.getAllByPlaceholderText("Find a movie");
    await userEvent.type(fromInput, "Inter");
    const fromButtons = await screen.findAllByRole("button", { name: /Interstellar/i });
    await userEvent.click(fromButtons[0]);
    await userEvent.type(toInput, "Inter");

    expect(await screen.findByText("No close matches yet.")).toBeInTheDocument();
    expect(screen.getByText("Selected movie")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Create connection" })).toBeEnabled();
    expect(fetchMock).not.toHaveBeenCalledWith(
      "/api/connections",
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("supports keyboard navigation in the connection movie picker", async () => {
    const movies = [
      {
        id: 7,
        title: "Interstellar",
        releaseYear: 2014,
        director: "Christopher Nolan",
        pictureUrl: null,
        externalId: null,
      },
      {
        id: 8,
        title: "Internal Affairs",
        releaseYear: 1990,
        director: "Mike Figgis",
        pictureUrl: null,
        externalId: null,
      },
    ];
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/api/auth/session")) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: { authenticated: true, user: { id: 1, email: "a@example.com", username: "atlas" } },
            message: "ok",
          }),
        };
      }
      if (url.includes("/api/movies/search")) {
        return { ok: true, json: async () => ({ success: true, data: movies, message: "ok" }) };
      }
      if (url.endsWith("/api/connections")) {
        return { ok: true, json: async () => ({ success: true, data: [], message: "ok" }) };
      }
      return {
        ok: true,
        json: async () => ({
          success: true,
          data: { id: 1, username: "atlas", email: "a@example.com", enabled: true, verified: true },
          message: "ok",
        }),
      };
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);

    await userEvent.click(await screen.findByRole("button", { name: "Connections" }));
    const [fromInput] = await screen.findAllByPlaceholderText("Find a movie");
    await userEvent.type(fromInput, "Inter");
    await userEvent.keyboard("{ArrowDown}{Enter}");

    expect(await screen.findByText("Internal Affairs")).toBeInTheDocument();
  });

  it("loads the selected movie graph from the movie connections endpoint", async () => {
    const movie = {
      id: 1,
      title: "Inception",
      releaseYear: 2010,
      director: "Christopher Nolan",
      pictureUrl: null,
      externalId: null,
    };
    const graphPayload = {
      movie,
      connections: [
        {
          id: 9,
          fromMovieId: 1,
          fromMovieTitle: "Inception",
          toMovieId: 2,
          toMovieTitle: "Interstellar",
          reason: "Shared Nolan themes",
          weight: 2,
          category: "director",
        },
        {
          id: 10,
          fromMovieId: 2,
          fromMovieTitle: "Interstellar",
          toMovieId: 3,
          toMovieTitle: "Memento",
          reason: "Memory and time",
          weight: 1.2,
          category: "structure",
        },
      ],
    };

    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/api/auth/session")) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: { authenticated: true, user: { id: 1, email: "a@example.com", username: "atlas" } },
            message: "ok",
          }),
        };
      }
      if (url.endsWith("/api/connections")) {
        return { ok: true, json: async () => ({ success: true, data: [], message: "ok" }) };
      }
      if (url.endsWith("/api/users/profile")) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: { id: 1, username: "atlas", email: "a@example.com", enabled: true, verified: true },
            message: "ok",
          }),
        };
      }
      if (url.includes("/api/movies/search")) {
        return { ok: true, json: async () => ({ success: true, data: [movie], message: "ok" }) };
      }
      if (url.endsWith("/api/movies/1/connections")) {
        return { ok: true, json: async () => ({ success: true, data: graphPayload, message: "Movie graph retrieved" }) };
      }
      throw new Error(`Unexpected fetch: ${url}`);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);

    await screen.findByText("Explore Movie Graphs");
    await userEvent.type(screen.getByPlaceholderText("Search local catalog, even with rough spelling"), "Incep");
    await userEvent.click(await screen.findByRole("button", { name: /Inception/i }));

    await waitFor(() => {
      expect(graphProps.last?.movie?.id).toBe(1);
      expect(graphProps.last?.connections).toHaveLength(2);
    });
  });

  it("lists shares, refreshes after create, and revokes an existing share", async () => {
    const movie = {
      id: 1,
      title: "Inception",
      releaseYear: 2010,
      director: "Christopher Nolan",
      pictureUrl: null,
      externalId: null,
      tagline: null,
      synopsis: null,
      genres: [],
      runtimeMinutes: null,
      castMembers: [],
      directorNotes: null,
    };
    let shareListCalls = 0;
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith("/api/auth/session")) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: { authenticated: true, user: { id: 1, email: "a@example.com", username: "atlas" } },
            message: "ok",
          }),
        };
      }
      if (url === "/api/shares" && !init?.method) {
        shareListCalls += 1;
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: shareListCalls === 1
              ? [{
                shareToken: "existing-share",
                shareUrl: "https://archipelago.test/shared/existing-share",
                title: "Existing share",
                rootMovieId: 2,
                rootMovieTitle: "Interstellar",
                creationTime: "2026-05-31T12:00:00",
              }]
              : [
                {
                  shareToken: "new-share",
                  shareUrl: "https://archipelago.test/shared/new-share",
                  title: "Inception graph",
                  rootMovieId: 1,
                  rootMovieTitle: "Inception",
                  creationTime: "2026-05-31T12:05:00",
                },
                {
                  shareToken: "existing-share",
                  shareUrl: "https://archipelago.test/shared/existing-share",
                  title: "Existing share",
                  rootMovieId: 2,
                  rootMovieTitle: "Interstellar",
                  creationTime: "2026-05-31T12:00:00",
                },
              ],
            message: "ok",
          }),
        };
      }
      if (url.includes("/api/movies/search")) {
        return { ok: true, json: async () => ({ success: true, data: [movie], message: "ok" }) };
      }
      if (url.endsWith("/api/movies/1/connections")) {
        return { ok: true, json: async () => ({ success: true, data: { movie, movies: [movie], connections: [] }, message: "ok" }) };
      }
      if (url === "/api/shares" && init?.method === "POST") {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: {
              shareToken: "new-share",
              shareUrl: "https://archipelago.test/shared/new-share",
              title: "Inception graph",
              graph: { movie, movies: [movie], connections: [] },
            },
            message: "Shared graph created",
          }),
        };
      }
      if (url.endsWith("/api/shares/existing-share") && init?.method === "DELETE") {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: null,
            message: "Shared graph revoked",
          }),
        };
      }
      throw new Error(`Unexpected fetch: ${url}`);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);

    await screen.findByText("Existing share");
    await userEvent.type(screen.getByPlaceholderText("Search local catalog, even with rough spelling"), "Inception");
    await userEvent.click(await screen.findByRole("button", { name: /Inception/i }));
    await userEvent.click(screen.getByRole("button", { name: "Create share link" }));

    expect(await screen.findByText("Inception graph")).toBeInTheDocument();

    await userEvent.click(within(screen.getByText("Existing share").closest("article") as HTMLElement).getByRole("button", { name: "Revoke" }));
    await waitFor(() => expect(screen.queryByText("Existing share")).not.toBeInTheDocument());
  });

  it("applies explore-style filters in the friend graph viewer without blocking path details", async () => {
    const friendMovie = {
      id: 2,
      title: "Interstellar",
      releaseYear: 2014,
      director: "Christopher Nolan",
      pictureUrl: null,
      externalId: null,
      tagline: null,
      synopsis: null,
      genres: [],
      runtimeMinutes: null,
      castMembers: [],
      directorNotes: null,
    };
    const otherFriendMovie = {
      id: 5,
      title: "Arrival",
      releaseYear: 2016,
      director: "Denis Villeneuve",
      pictureUrl: null,
      externalId: null,
      tagline: null,
      synopsis: null,
      genres: [],
      runtimeMinutes: null,
      castMembers: [],
      directorNotes: null,
    };
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith("/api/auth/session")) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: { authenticated: true, user: { id: 1, email: "a@example.com", username: "atlas" } },
            message: "ok",
          }),
        };
      }
      if (url.endsWith("/api/friends")) {
        return {
          ok: true,
          json: async () => ({ success: true, data: [{ id: 7, username: "arch-friend" }], message: "ok" }),
        };
      }
      if (url.endsWith("/api/friends/requests") && !init?.method) {
        return { ok: true, json: async () => ({ success: true, data: { incoming: [], outgoing: [] }, message: "ok" }) };
      }
      if (url.endsWith("/api/friends/7/profile")) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: { id: 7, username: "arch-friend", movies: [friendMovie, otherFriendMovie] },
            message: "ok",
          }),
        };
      }
      if (url.endsWith("/api/friends/7/movies/2/connections")) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: {
              movie: friendMovie,
              movies: [friendMovie, otherFriendMovie],
              connections: [
                {
                  id: 1,
                  fromMovieId: 2,
                  fromMovieTitle: "Interstellar",
                  toMovieId: 5,
                  toMovieTitle: "Arrival",
                  reason: "Shared first-contact spectacle",
                  weight: 1.2,
                  category: "theme",
                },
                {
                  id: 2,
                  fromMovieId: 5,
                  fromMovieTitle: "Arrival",
                  toMovieId: 2,
                  toMovieTitle: "Interstellar",
                  reason: "Time-bending structure",
                  weight: 2.1,
                  category: "structure",
                },
              ],
            },
            message: "ok",
          }),
        };
      }
      if (url.endsWith("/api/friends/7/movies/path?from=2&to=5")) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: {
              fromMovie: friendMovie,
              toMovie: otherFriendMovie,
              movies: [friendMovie, otherFriendMovie],
              connections: [{
                id: 2,
                fromMovieId: 5,
                fromMovieTitle: "Arrival",
                toMovieId: 2,
                toMovieTitle: "Interstellar",
                reason: "Time-bending structure",
                weight: 2.1,
                category: "structure",
              }],
            },
            message: "ok",
          }),
        };
      }
      throw new Error(`Unexpected fetch: ${url}`);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);

    await userEvent.click(await screen.findByRole("button", { name: "Network" }));
    await userEvent.click(await screen.findByRole("button", { name: "Browse graphs" }));
    await userEvent.click(await screen.findByRole("button", { name: /Interstellar/i }));

    await waitFor(() => expect(graphProps.last?.connections).toHaveLength(2));

    await userEvent.selectOptions(screen.getByLabelText("Category filter"), "structure");
    await userEvent.clear(screen.getByLabelText("Minimum weight"));
    await userEvent.type(screen.getByLabelText("Minimum weight"), "2");

    await waitFor(() => expect(graphProps.last?.connections).toHaveLength(1));

    await userEvent.selectOptions(screen.getByLabelText("From movie"), "2");
    await userEvent.selectOptions(screen.getByLabelText("To movie"), "5");
    await userEvent.click(screen.getByRole("button", { name: "Explain shortest path" }));

    expect((await screen.findAllByText("Interstellar -> Arrival")).length).toBeGreaterThan(0);
  });

  it("clears authenticated status text after logout", async () => {
    const movie = {
      id: 1,
      title: "The Godfather",
      releaseYear: 1972,
      director: "Francis Ford Coppola",
      pictureUrl: null,
      externalId: null,
    };
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith("/api/auth/session")) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: { authenticated: true, user: { id: 1, email: "a@example.com", username: "atlas" } },
            message: "ok",
          }),
        };
      }
      if (url.includes("/api/movies/search")) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: [movie],
            message: "ok",
          }),
        };
      }
      if (url.endsWith("/api/movies/1/connections")) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: { movie, connections: [] },
            message: "Movie graph retrieved",
          }),
        };
      }
      if (url.endsWith("/api/auth/logout") && init?.method === "POST") {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: null,
            message: "Logged out",
          }),
        };
      }
      throw new Error(`Unexpected fetch: ${url}`);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);

    await screen.findByText("Explore Movie Graphs");
    await userEvent.type(screen.getByPlaceholderText("Search local catalog, even with rough spelling"), "Godfather");
    await userEvent.click(await screen.findByRole("button", { name: /The Godfather/i }));
    expect(await screen.findByText("Loaded your connections for The Godfather")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Logout" }));

    expect(await screen.findByText("Map how films connect.")).toBeInTheDocument();
    expect(screen.queryByText("Loaded your connections for The Godfather")).not.toBeInTheDocument();
  });

  it("renders a public shared graph view without requiring auth", async () => {
    window.history.pushState({}, "", "/shared/share-token");
    const sharedMovie = {
      id: 1,
      title: "Inception",
      releaseYear: 2010,
      director: "Christopher Nolan",
      pictureUrl: null,
      externalId: null,
      tagline: "Your mind is the scene of the crime.",
      synopsis: "Dreams inside dreams.",
      genres: ["Science Fiction"],
      runtimeMinutes: 148,
      castMembers: ["Leonardo DiCaprio"],
      directorNotes: "Practical spectacle.",
    };
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/api/auth/session")) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: { authenticated: false, user: null },
            message: "ok",
          }),
        };
      }
      if (url.endsWith("/api/shares/share-token")) {
        return {
          ok: true,
          json: async () => ({
            success: true,
            data: {
              shareToken: "share-token",
              title: "Demo Nolan cluster",
              graph: {
                movie: sharedMovie,
                movies: [sharedMovie],
                connections: [],
              },
            },
            message: "Shared graph retrieved",
          }),
        };
      }
      throw new Error(`Unexpected fetch: ${url}`);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<App />);

    expect(await screen.findByText("Demo Nolan cluster")).toBeInTheDocument();
    expect(await screen.findByText("Read-only export of a saved movie component.")).toBeInTheDocument();
    await waitFor(() => expect(graphProps.last?.movie?.id).toBe(1));
  });
});
