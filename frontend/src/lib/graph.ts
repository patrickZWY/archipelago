import type cytoscape from "cytoscape";
import type { Connection, Movie } from "./types";

export function buildGraphElements(movie: Movie | null, connections: Connection[]): cytoscape.ElementDefinition[] {
  if (!movie) {
    return [];
  }

  const nodes = new Map<number, cytoscape.ElementDefinition>();
  upsertNode(nodes, movie.id, movie.title, true);

  const edges = connections.map((connection) => {
    upsertNode(nodes, connection.fromMovieId, connection.fromMovieTitle, connection.fromMovieId === movie.id);
    upsertNode(nodes, connection.toMovieId, connection.toMovieTitle, connection.toMovieId === movie.id);

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

export function buildGraphLayout(
  movie: Movie | null,
  connections: Connection[],
): cytoscape.LayoutOptions | null {
  if (!movie) {
    return null;
  }

  const distances = buildNodeDistances(movie, connections);
  const nodeCount = distances.size;

  if (nodeCount <= 1) {
    return null;
  }

  if (nodeCount === 2) {
    return {
      name: "breadthfirst",
      roots: [`#movie-${movie.id}`],
      directed: false,
      fit: true,
      padding: 56,
      animate: false,
      spacingFactor: 1.2,
      avoidOverlap: true,
    };
  }

  const positions = buildConstellationPositions(movie.id, distances);

  return {
    name: "preset",
    padding: 56,
    animate: false,
    fit: true,
    positions,
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

function buildNodeDistances(movie: Movie, connections: Connection[]) {
  const adjacency = new Map<number, Set<number>>();
  const distances = new Map<number, number>();

  for (const connection of connections) {
    addNeighbor(adjacency, connection.fromMovieId, connection.toMovieId);
    addNeighbor(adjacency, connection.toMovieId, connection.fromMovieId);
  }

  const queue: number[] = [movie.id];
  distances.set(movie.id, 0);

  while (queue.length > 0) {
    const current = queue.shift();
    if (current === undefined) {
      continue;
    }

    const currentDistance = distances.get(current) ?? 0;
    for (const neighbor of adjacency.get(current) ?? []) {
      if (distances.has(neighbor)) {
        continue;
      }
      distances.set(neighbor, currentDistance + 1);
      queue.push(neighbor);
    }
  }

  return distances;
}

function addNeighbor(adjacency: Map<number, Set<number>>, fromMovieId: number, toMovieId: number) {
  let neighbors = adjacency.get(fromMovieId);
  if (!neighbors) {
    neighbors = new Set<number>();
    adjacency.set(fromMovieId, neighbors);
  }
  neighbors.add(toMovieId);
}

function buildConstellationPositions(movieId: number, distances: Map<number, number>) {
  const positions: Record<string, { x: number; y: number }> = {};
  const levels = new Map<number, number[]>();

  for (const [id, distance] of distances.entries()) {
    let ids = levels.get(distance);
    if (!ids) {
      ids = [];
      levels.set(distance, ids);
    }
    ids.push(id);
  }

  positions[`movie-${movieId}`] = { x: 0, y: 0 };

  const sortedLevels = [...levels.entries()]
    .filter(([distance]) => distance > 0)
    .sort((left, right) => left[0] - right[0]);

  for (const [distance, ids] of sortedLevels) {
    ids.sort((left, right) => left - right);

    const radius = 150 * distance;
    const baseAngle = ((distance - 1) * Math.PI) / 3 - Math.PI / 2;

    if (ids.length === 1) {
      positions[`movie-${ids[0]}`] = {
        x: Math.cos(baseAngle) * radius,
        y: Math.sin(baseAngle) * radius,
      };
      continue;
    }

    const spread = Math.min(Math.PI * 1.45, Math.PI / 2 + ids.length * (Math.PI / 7));
    const startAngle = baseAngle - spread / 2;
    const step = spread / (ids.length - 1);

    ids.forEach((id, index) => {
      const angle = startAngle + step * index;
      positions[`movie-${id}`] = {
        x: Math.cos(angle) * radius,
        y: Math.sin(angle) * radius,
      };
    });
  }

  return positions;
}
