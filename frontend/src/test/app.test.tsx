import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "../App";

const graphProps = vi.hoisted(() => ({
  last: null as null | { movie: { id: number; title: string } | null; connections: Array<{ id: number }> },
}));

vi.mock("../components/ConnectionGraph", () => ({
  ConnectionGraph: (props: { movie: { id: number; title: string } | null; connections: Array<{ id: number }> }) => {
    graphProps.last = props;
    return <div>Graph Stub</div>;
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
});
