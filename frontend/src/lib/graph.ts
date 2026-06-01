import { formatConnectionCategory } from "./connection-categories";
import type cytoscape from "cytoscape";
import type { Connection, Movie } from "./types";

export function buildGraphElements(movie: Movie | null, connections: Connection[], movies: Movie[] = []): cytoscape.ElementDefinition[] {
  if (!movie && movies.length === 0) {
    return [];
  }

  const nodes = new Map<number, cytoscape.ElementDefinition>();
  for (const graphMovie of movies) {
    upsertNode(nodes, graphMovie.id, graphMovie.title, graphMovie.id === movie?.id);
  }
  if (movie) {
    upsertNode(nodes, movie.id, movie.title, true);
  }

  const edges = connections.map((connection) => {
    upsertNode(nodes, connection.fromMovieId, connection.fromMovieTitle, connection.fromMovieId === movie.id);
    upsertNode(nodes, connection.toMovieId, connection.toMovieTitle, connection.toMovieId === movie.id);

    return {
      data: {
        id: `connection-${connection.id}`,
        source: `movie-${connection.fromMovieId}`,
        target: `movie-${connection.toMovieId}`,
        label: formatConnectionCategory(connection.category),
        category: connection.category ?? "uncategorized",
        weight: connection.weight,
      },
    };
  });

  return [...nodes.values(), ...edges];
}

export function buildGraphLayout(
  movie: Movie | null,
  connections: Connection[],
  movies: Movie[] = [],
): cytoscape.LayoutOptions | null {
  const anchorMovie = movie ?? movies[0] ?? null;
  if (!anchorMovie) {
    return null;
  }

  const nodeCount = countGraphNodes(anchorMovie.id, connections, movies);

  if (nodeCount <= 1) {
    return null;
  }

  if (nodeCount === 2) {
    return {
      name: "breadthfirst",
      roots: [`#movie-${anchorMovie.id}`],
      directed: false,
      fit: true,
      padding: 56,
      animate: false,
      spacingFactor: 1.2,
      avoidOverlap: true,
    };
  }

  return {
    name: "cose",
    padding: 56,
    animate: false,
    fit: true,
    randomize: false,
    componentSpacing: 180,
    nodeRepulsion: 150000,
    idealEdgeLength: 130,
    edgeElasticity: 120,
    gravity: 0.35,
    numIter: 1200,
  };
}

function upsertNode(
  nodes: Map<number, cytoscape.ElementDefinition>,
  id: number,
  label: string,
  focus: boolean,
) {
  const existing = nodes.get(id);
  nodes.set(id, {
    data: {
      id: `movie-${id}`,
      label,
      focus: existing?.data?.focus === true || focus,
    },
  });
}

function countGraphNodes(movieId: number, connections: Connection[], movies: Movie[] = []) {
  const nodeIds = new Set<number>([movieId, ...movies.map((entry) => entry.id)]);

  for (const connection of connections) {
    nodeIds.add(connection.fromMovieId);
    nodeIds.add(connection.toMovieId);
  }

  return nodeIds.size;
}
