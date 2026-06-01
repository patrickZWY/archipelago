import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ConnectionGraph } from "../components/ConnectionGraph";

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

describe("ConnectionGraph", () => {
  it("renders an interactive svg graph surface", () => {
    render(
      <ConnectionGraph
        movie={movie}
        movies={[
          movie,
          { ...movie, id: 2, title: "Interstellar" },
          { ...movie, id: 3, title: "Memento" },
        ]}
        connections={[
          {
            id: 1,
            fromMovieId: 1,
            fromMovieTitle: "Inception",
            toMovieId: 2,
            toMovieTitle: "Interstellar",
            reason: "Shared Nolan themes",
            weight: 2,
            category: "director",
          },
          {
            id: 2,
            fromMovieId: 2,
            fromMovieTitle: "Interstellar",
            toMovieId: 3,
            toMovieTitle: "Memento",
            reason: "Memory and time",
            weight: 1.2,
            category: "structure",
          },
        ]}
      />,
    );

    expect(screen.getByLabelText("Graph zoom")).toBeInTheDocument();
    expect(screen.getByLabelText("Connection graph")).toBeInTheDocument();
  });

  it("updates the visible zoom label from the slider", () => {
    render(
      <ConnectionGraph
        movie={movie}
        connections={[
          {
            id: 1,
            fromMovieId: 1,
            fromMovieTitle: "Inception",
            toMovieId: 2,
            toMovieTitle: "Interstellar",
            reason: "Shared Nolan themes",
            weight: 2,
            category: "director",
          },
        ]}
      />,
    );

    fireEvent.change(screen.getByLabelText("Graph zoom"), { target: { value: "1.4" } });

    expect(screen.getByText("140%")).toBeInTheDocument();
  });

  it("preserves zoom across rerenders", () => {
    const { rerender } = render(
      <ConnectionGraph
        movie={movie}
        movies={[movie, { ...movie, id: 2, title: "Interstellar" }]}
        connections={[
          {
            id: 1,
            fromMovieId: 1,
            fromMovieTitle: "Inception",
            toMovieId: 2,
            toMovieTitle: "Interstellar",
            reason: "Shared Nolan themes",
            weight: 2,
            category: "director",
          },
        ]}
        selectedMovieId={1}
      />,
    );

    fireEvent.change(screen.getByLabelText("Graph zoom"), { target: { value: "1.4" } });

    rerender(
      <ConnectionGraph
        movie={movie}
        movies={[movie, { ...movie, id: 2, title: "Interstellar" }]}
        connections={[
          {
            id: 1,
            fromMovieId: 1,
            fromMovieTitle: "Inception",
            toMovieId: 2,
            toMovieTitle: "Interstellar",
            reason: "Shared Nolan themes",
            weight: 2,
            category: "director",
          },
        ]}
        selectedMovieId={2}
      />,
    );

    expect(screen.getByText("140%")).toBeInTheDocument();
  });
});
