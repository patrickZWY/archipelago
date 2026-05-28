import type cytoscape from "cytoscape";
import type { Connection, Movie } from "./types";

export function buildGraphElements(movie: Movie | null, connections: Connection[]): cytoscape.ElementDefinition[] {
  if (!movie) {
    return [];
  }

  const nodes = new Map<number, cytoscape.ElementDefinition>();
  nodes.set(movie.id, {
    data: { id: `movie-${movie.id}`, label: movie.title, focus: true },
  });

  const edges = connections.map((connection) => {
    nodes.set(connection.fromMovieId, {
      data: {
        id: `movie-${connection.fromMovieId}`,
        label: connection.fromMovieTitle,
        focus: connection.fromMovieId === movie.id,
      },
    });
    nodes.set(connection.toMovieId, {
      data: {
        id: `movie-${connection.toMovieId}`,
        label: connection.toMovieTitle,
        focus: connection.toMovieId === movie.id,
      },
    });

    return {
      data: {
        id: `connection-${connection.id}`,
        source: `movie-${connection.fromMovieId}`,
        target: `movie-${connection.toMovieId}`,
        label: connection.reason,
        category: connection.category ?? "uncategorized",
      },
    };
  });

  return [...nodes.values(), ...edges];
}
