import { describe, expect, it } from "vitest";
import { buildGraphElements, buildGraphLayout } from "../lib/graph";

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

describe("buildGraphElements", () => {
  it("creates nodes and edges from movie connections", () => {
    const elements = buildGraphElements(
      movie,
      [
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
      ],
    );

    expect(elements).toHaveLength(3);
    expect(elements.find((item) => item.data?.id === "movie-1")).toBeTruthy();
    expect(elements.find((item) => item.data?.id === "connection-9")).toBeTruthy();
  });

  it("keeps the selected movie focused across multi-hop graph data", () => {
    const elements = buildGraphElements(
      movie,
      [
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
    );

    expect(elements).toHaveLength(5);
    expect(elements.find((item) => item.data?.id === "movie-1")?.data?.focus).toBe(true);
    expect(elements.find((item) => item.data?.id === "movie-3")?.data?.focus).toBe(false);
    expect(elements.find((item) => item.data?.id === "connection-10")).toBeTruthy();
  });

  it("renders the selected movie even when there are no connections", () => {
    const elements = buildGraphElements(
      { ...movie, id: 5, title: "Arrival", releaseYear: 2016, director: "Denis Villeneuve" },
      [],
    );

    expect(elements).toHaveLength(1);
    expect(elements[0]).toMatchObject({
      data: {
        id: "movie-5",
        label: "Arrival",
        focus: true,
      },
    });
  });

  it("uses a force-directed layout for graphs with more than two nodes", () => {
    const layout = buildGraphLayout(
      movie,
      [
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
    );

    expect(layout?.name).toBe("cose");
    expect(layout).toMatchObject({
      animate: false,
      fit: true,
      randomize: false,
    });
  });

  it("keeps force-directed component spacing enabled for disconnected graphs", () => {
    const layout = buildGraphLayout(
      movie,
      [
        {
          id: 9,
          fromMovieId: 2,
          fromMovieTitle: "Interstellar",
          toMovieId: 3,
          toMovieTitle: "Memento",
          reason: "Memory and time",
          weight: 1.2,
          category: "structure",
        },
        {
          id: 10,
          fromMovieId: 5,
          fromMovieTitle: "Arrival",
          toMovieId: 6,
          toMovieTitle: "Blade Runner 2049",
          reason: "Controlled Villeneuve sci-fi",
          weight: 2.3,
          category: "director",
        },
      ],
    );

    expect(layout?.name).toBe("cose");
    expect(layout).toMatchObject({
      componentSpacing: 180,
      idealEdgeLength: 130,
    });
  });
});
