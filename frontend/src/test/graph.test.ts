import { describe, expect, it } from "vitest";
import { buildGraphElements } from "../lib/graph";

describe("buildGraphElements", () => {
  it("creates nodes and edges from movie connections", () => {
    const elements = buildGraphElements(
      { id: 1, title: "Inception", releaseYear: 2010, director: "Christopher Nolan", pictureUrl: null, externalId: null },
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
});
