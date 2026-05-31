import { formatConnectionCategory } from "./connection-categories";
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
): cytoscape.LayoutOptions | null {
  if (!movie) {
    return null;
  }

  const distances = buildNodeDistances(movie, connections);
  const nodeCount = countGraphNodes(movie.id, connections);

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

  const positions = buildComponentLayout(movie.id, connections);

  return {
    name: "preset",
    padding: 56,
    animate: false,
    fit: true,
    positions,
  };
}

type GraphComponent = {
  anchorId: number;
  nodeIds: number[];
};

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
  const nodeIds = new Set<number>([movie.id]);

  for (const connection of connections) {
    nodeIds.add(connection.fromMovieId);
    nodeIds.add(connection.toMovieId);
    addNeighbor(adjacency, connection.fromMovieId, connection.toMovieId);
    addNeighbor(adjacency, connection.toMovieId, connection.fromMovieId);
  }

  return buildDistancesFromAnchor(movie.id, adjacency, nodeIds);
}

function countGraphNodes(movieId: number, connections: Connection[]) {
  const nodeIds = new Set<number>([movieId]);

  for (const connection of connections) {
    nodeIds.add(connection.fromMovieId);
    nodeIds.add(connection.toMovieId);
  }

  return nodeIds.size;
}

function addNeighbor(adjacency: Map<number, Set<number>>, fromMovieId: number, toMovieId: number) {
  let neighbors = adjacency.get(fromMovieId);
  if (!neighbors) {
    neighbors = new Set<number>();
    adjacency.set(fromMovieId, neighbors);
  }
  neighbors.add(toMovieId);
}

function buildComponentLayout(movieId: number, connections: Connection[]) {
  const adjacency = new Map<number, Set<number>>();
  const nodeIds = new Set<number>([movieId]);

  for (const connection of connections) {
    nodeIds.add(connection.fromMovieId);
    nodeIds.add(connection.toMovieId);
    addNeighbor(adjacency, connection.fromMovieId, connection.toMovieId);
    addNeighbor(adjacency, connection.toMovieId, connection.fromMovieId);
  }

  const components = buildGraphComponents(movieId, adjacency, nodeIds);
  const positions: Record<string, { x: number; y: number }> = {};

  components.forEach((component, index) => {
    const componentNodeIds = new Set(component.nodeIds);
    const componentDistances = buildDistancesFromAnchor(component.anchorId, adjacency, componentNodeIds);
    const componentPositions = buildConstellationPositions(component.anchorId, componentDistances);
    const offset = index === 0 ? { x: 0, y: 0 } : buildComponentOffset(index);

    for (const [nodeKey, position] of Object.entries(componentPositions)) {
      positions[nodeKey] = {
        x: position.x + offset.x,
        y: position.y + offset.y,
      };
    }
  });

  return positions;
}

function buildGraphComponents(
  rootMovieId: number,
  adjacency: Map<number, Set<number>>,
  nodeIds: Set<number>,
) {
  const visited = new Set<number>();
  const components: GraphComponent[] = [];

  function visitComponent(anchorId: number) {
    if (visited.has(anchorId)) {
      return;
    }

    const queue = [anchorId];
    const componentNodeIds: number[] = [];
    visited.add(anchorId);

    while (queue.length > 0) {
      const current = queue.shift();
      if (current === undefined) {
        continue;
      }
      componentNodeIds.push(current);
      for (const neighbor of adjacency.get(current) ?? []) {
        if (visited.has(neighbor)) {
          continue;
        }
        visited.add(neighbor);
        queue.push(neighbor);
      }
    }

    componentNodeIds.sort((left, right) => left - right);
    components.push({ anchorId, nodeIds: componentNodeIds });
  }

  visitComponent(rootMovieId);
  [...nodeIds]
    .filter((nodeId) => !visited.has(nodeId))
    .sort((left, right) => left - right)
    .forEach((nodeId) => visitComponent(nodeId));

  return components;
}

function buildDistancesFromAnchor(
  anchorId: number,
  adjacency: Map<number, Set<number>>,
  nodeIds: Set<number>,
) {
  const distances = new Map<number, number>();
  const queue: number[] = [anchorId];

  distances.set(anchorId, 0);

  while (queue.length > 0) {
    const current = queue.shift();
    if (current === undefined) {
      continue;
    }

    const currentDistance = distances.get(current) ?? 0;
    for (const neighbor of adjacency.get(current) ?? []) {
      if (!nodeIds.has(neighbor) || distances.has(neighbor)) {
        continue;
      }
      distances.set(neighbor, currentDistance + 1);
      queue.push(neighbor);
    }
  }

  return distances;
}

function buildComponentOffset(index: number) {
  const slots = [
    { x: 380, y: 0 },
    { x: -380, y: 0 },
    { x: 0, y: 300 },
    { x: 0, y: -300 },
    { x: 340, y: 260 },
    { x: -340, y: 260 },
    { x: 340, y: -260 },
    { x: -340, y: -260 },
  ];
  const slot = slots[(index - 1) % slots.length];
  const ring = Math.floor((index - 1) / slots.length) + 1;

  return {
    x: slot.x * ring,
    y: slot.y * ring,
  };
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
