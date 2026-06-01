import * as d3 from "d3";
import { useEffect, useRef, useState } from "react";
import type { Connection, Movie } from "../lib/types";

type Props = {
  movie: Movie | null;
  movies?: Movie[];
  connections: Connection[];
  layoutMode?: "default" | "compact";
  dimMovieIds?: number[];
  dimConnectionIds?: number[];
  selectedMovieId?: number | null;
  selectedConnectionId?: number | null;
  onMovieSelect?: (movieId: number) => void;
  onConnectionSelect?: (connectionId: number) => void;
};

type GraphNode = d3.SimulationNodeDatum & {
  id: number;
  label: string;
  focus: boolean;
};

type GraphLink = d3.SimulationLinkDatum<GraphNode> & {
  id: number;
  source: number | GraphNode;
  target: number | GraphNode;
  weight: number;
};

type LayoutConfig = {
  linkDistance: number;
  linkStrength: number;
  chargeStrength: number;
  centeringStrength: number;
  collisionRadius: number;
  focusCollisionRadius: number;
};

const MIN_ZOOM = 0.4;
const MAX_ZOOM = 3.2;
const DEFAULT_WIDTH = 960;
const DEFAULT_HEIGHT = 720;

export function ConnectionGraph({
  movie,
  movies = [],
  connections,
  layoutMode = "default",
  dimMovieIds = [],
  dimConnectionIds = [],
  selectedMovieId,
  selectedConnectionId,
  onMovieSelect,
  onConnectionSelect,
}: Props) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const svgRef = useRef<SVGSVGElement | null>(null);
  const nodePositionsRef = useRef<Record<number, { x: number; y: number }>>({});
  const simulationRef = useRef<d3.Simulation<GraphNode, GraphLink> | null>(null);
  const nodeSelectionRef = useRef<d3.Selection<SVGGElement, GraphNode, SVGGElement, unknown> | null>(null);
  const linkSelectionRef = useRef<d3.Selection<SVGLineElement, GraphLink, SVGGElement, unknown> | null>(null);
  const zoomBehaviorRef = useRef<d3.ZoomBehavior<SVGSVGElement, unknown> | null>(null);
  const svgSelectionRef = useRef<d3.Selection<SVGSVGElement, unknown, null, undefined> | null>(null);
  const currentTransformRef = useRef(d3.zoomIdentity);
  const onMovieSelectRef = useRef(onMovieSelect);
  const onConnectionSelectRef = useRef(onConnectionSelect);
  const [zoom, setZoom] = useState(1);

  useEffect(() => {
    onMovieSelectRef.current = onMovieSelect;
  }, [onMovieSelect]);

  useEffect(() => {
    onConnectionSelectRef.current = onConnectionSelect;
  }, [onConnectionSelect]);

  useEffect(() => {
    if (!containerRef.current || !svgRef.current) {
      return;
    }

    const { width, height } = readViewport(containerRef.current);
    const graphNodes = buildGraphNodes(movie, movies, connections, nodePositionsRef.current);
    const graphLinks = buildGraphLinks(connections);
    const layoutConfig = resolveLayoutConfig(layoutMode, graphNodes.length, graphLinks.length);
    const svg = d3.select(svgRef.current);
    svgSelectionRef.current = svg;
    svg.selectAll("*").remove();
    svg.attr("viewBox", `0 0 ${width} ${height}`);

    const content = svg.append("g");
    const zoomBehavior = d3.zoom<SVGSVGElement, unknown>()
      .extent([[0, 0], [width, height]])
      .scaleExtent([MIN_ZOOM, MAX_ZOOM])
      .on("zoom", (event) => {
        currentTransformRef.current = event.transform;
        content.attr("transform", event.transform.toString());
        setZoom(event.transform.k);
      });
    zoomBehaviorRef.current = zoomBehavior;
    svg.call(zoomBehavior);
    svg.call(zoomBehavior.transform, currentTransformRef.current);

    const linkSelection = content.append("g")
      .selectAll<SVGLineElement, GraphLink>("line")
      .data(graphLinks, (entry) => String(entry.id))
      .join("line")
      .style("cursor", "pointer")
      .attr("stroke", "rgba(124, 224, 255, 0.5)")
      .attr("stroke-opacity", 0.46)
      .attr("stroke-linecap", "round")
      .on("click", (_event, datum) => onConnectionSelectRef.current?.(datum.id));

    const nodeSelection = content.append("g")
      .selectAll<SVGGElement, GraphNode>("g")
      .data(graphNodes, (entry) => String(entry.id))
      .join("g")
      .style("cursor", "grab")
      .on("click", (_event, datum) => onMovieSelectRef.current?.(datum.id));

    nodeSelection.append("circle")
      .attr("r", (datum) => datum.focus ? 16 : 12)
      .attr("stroke", "rgba(247,255,251,0.86)")
      .attr("stroke-width", 1.35)
      .attr("filter", "drop-shadow(0 0 14px rgba(124, 224, 255, 0.18))");

    nodeSelection.append("text")
      .attr("dy", 30)
      .attr("text-anchor", "middle")
      .style("fill", "#08111b")
      .style("fontFamily", "\"Space Grotesk\", \"Segoe UI\", sans-serif")
      .style("fontSize", "15px")
      .style("fontWeight", "800")
      .style("paintOrder", "normal")
      .style("stroke", "none")
      .style("letterSpacing", "0.015em")
      .text((datum) => datum.label);

    nodeSelection.each(function attachLabelBackdrop() {
      const group = d3.select(this);
      const text = group.select<SVGTextElement>("text");
      const textNode = text.node();
      const bounds = textNode && typeof textNode.getBBox === "function"
        ? textNode.getBBox()
        : null;
      if (!bounds) {
        return;
      }

      group.insert("rect", "text")
        .attr("x", bounds.x - 10)
        .attr("y", bounds.y - 5)
        .attr("width", bounds.width + 20)
        .attr("height", bounds.height + 10)
        .attr("rx", 10)
        .attr("ry", 10)
        .attr("fill", "#ffe44d")
        .attr("stroke", "rgba(8, 17, 27, 0.9)")
        .attr("stroke-width", 1.25)
        .attr("filter", "drop-shadow(0 3px 10px rgba(0, 0, 0, 0.35))");
    });

    const simulation = d3.forceSimulation(graphNodes)
      .force("link", d3.forceLink(graphLinks).id((datum) => datum.id).distance(layoutConfig.linkDistance).strength(layoutConfig.linkStrength))
      .force("charge", d3.forceManyBody().strength(layoutConfig.chargeStrength))
      .force("center", d3.forceCenter(width / 2, height / 2))
      .force("x", d3.forceX(width / 2).strength(layoutConfig.centeringStrength))
      .force("y", d3.forceY(height / 2).strength(layoutConfig.centeringStrength))
      .force("collide", d3.forceCollide<GraphNode>().radius((datum) => datum.focus ? layoutConfig.focusCollisionRadius : layoutConfig.collisionRadius))
      .velocityDecay(0.22)
      .on("tick", () => {
        linkSelection
          .attr("x1", (datum) => readNodeX(datum.source))
          .attr("y1", (datum) => readNodeY(datum.source))
          .attr("x2", (datum) => readNodeX(datum.target))
          .attr("y2", (datum) => readNodeY(datum.target));

        nodeSelection.attr("transform", (datum) => `translate(${datum.x ?? 0},${datum.y ?? 0})`);
        persistNodePositions(graphNodes, nodePositionsRef.current);
      });

    const dragBehavior = d3.drag<SVGGElement, GraphNode>()
      .on("start", (event, datum) => {
        if (!event.active) {
          simulation.alphaTarget(0.3).restart();
        }
        datum.fx = datum.x;
        datum.fy = datum.y;
      })
      .on("drag", (event, datum) => {
        datum.fx = event.x;
        datum.fy = event.y;
      })
      .on("end", (event, datum) => {
        if (!event.active) {
          simulation.alphaTarget(0);
        }
        datum.fx = null;
        datum.fy = null;
        nodePositionsRef.current[datum.id] = { x: datum.x ?? event.x, y: datum.y ?? event.y };
      });
    nodeSelection.call(dragBehavior);

    simulationRef.current = simulation;
    nodeSelectionRef.current = nodeSelection;
    linkSelectionRef.current = linkSelection;
    applyHighlightState(
      nodeSelection,
      linkSelection,
      selectedMovieId ?? null,
      selectedConnectionId ?? null,
      new Set(dimMovieIds),
      new Set(dimConnectionIds),
    );

    return () => {
      persistNodePositions(graphNodes, nodePositionsRef.current);
      simulation.stop();
      simulationRef.current = null;
      nodeSelectionRef.current = null;
      linkSelectionRef.current = null;
      zoomBehaviorRef.current = null;
      svgSelectionRef.current = null;
    };
  }, [movie, movies, connections, layoutMode, dimMovieIds, dimConnectionIds]);

  useEffect(() => {
    if (!nodeSelectionRef.current || !linkSelectionRef.current) {
      return;
    }
    applyHighlightState(
      nodeSelectionRef.current,
      linkSelectionRef.current,
      selectedMovieId ?? null,
      selectedConnectionId ?? null,
      new Set(dimMovieIds),
      new Set(dimConnectionIds),
    );
  }, [selectedMovieId, selectedConnectionId, dimMovieIds, dimConnectionIds]);

  useEffect(() => {
    const container = containerRef.current;
    const simulation = simulationRef.current;
    if (!container || !simulation) {
      return;
    }

    function handleResize() {
      const { width, height } = readViewport(container);
      const svgSelection = d3.select(svgRef.current);
      svgSelection.attr("viewBox", `0 0 ${width} ${height}`);
      zoomBehaviorRef.current?.extent([[0, 0], [width, height]]);
      if (zoomBehaviorRef.current) {
        svgSelection.call(zoomBehaviorRef.current.transform, currentTransformRef.current);
      }
      simulation.force("center", d3.forceCenter(width / 2, height / 2));
      simulation.force("x", d3.forceX(width / 2).strength(resolveLayoutConfig(layoutMode, movies.length || 0, connections.length).centeringStrength));
      simulation.force("y", d3.forceY(height / 2).strength(resolveLayoutConfig(layoutMode, movies.length || 0, connections.length).centeringStrength));
      simulation.alpha(0.3).restart();
    }

    window.addEventListener("resize", handleResize);
    return () => window.removeEventListener("resize", handleResize);
  }, [movie, movies, connections, layoutMode]);

  function handleZoomChange(nextZoom: number) {
    setZoom(nextZoom);
    if (!svgSelectionRef.current || !zoomBehaviorRef.current || !containerRef.current) {
      return;
    }

    const { width, height } = readViewport(containerRef.current);
    svgSelectionRef.current.call(
      zoomBehaviorRef.current.scaleTo,
      nextZoom,
      [width / 2, height / 2],
    );
  }

  return (
    <div className="graph-frame">
      <div className="graph-toolbar">
        <label className="graph-zoom-control">
          <span>Zoom</span>
          <input
            aria-label="Graph zoom"
            type="range"
            min={MIN_ZOOM}
            max={MAX_ZOOM}
            step={0.05}
            value={zoom}
            onChange={(event) => handleZoomChange(Number(event.target.value))}
          />
          <strong>{Math.round(zoom * 100)}%</strong>
        </label>
      </div>
      <div className="graph-canvas" ref={containerRef}>
        <div className="graph-hint">Drag nodes. Wheel to zoom. Drag canvas to pan.</div>
        <svg ref={svgRef} aria-label="Connection graph" />
      </div>
    </div>
  );
}

function buildGraphNodes(
  movie: Movie | null,
  movies: Movie[],
  connections: Connection[],
  positions: Record<number, { x: number; y: number }>,
) {
  const nodes = new Map<number, GraphNode>();

  for (const graphMovie of movies) {
    const position = positions[graphMovie.id];
    nodes.set(graphMovie.id, {
      id: graphMovie.id,
      label: graphMovie.title,
      focus: graphMovie.id === movie?.id,
      x: position?.x,
      y: position?.y,
    });
  }

  if (movie) {
    const position = positions[movie.id];
    nodes.set(movie.id, {
      id: movie.id,
      label: movie.title,
      focus: true,
      x: position?.x,
      y: position?.y,
    });
  }

  for (const connection of connections) {
    if (!nodes.has(connection.fromMovieId)) {
      const position = positions[connection.fromMovieId];
      nodes.set(connection.fromMovieId, {
        id: connection.fromMovieId,
        label: connection.fromMovieTitle,
        focus: connection.fromMovieId === movie?.id,
        x: position?.x,
        y: position?.y,
      });
    }
    if (!nodes.has(connection.toMovieId)) {
      const position = positions[connection.toMovieId];
      nodes.set(connection.toMovieId, {
        id: connection.toMovieId,
        label: connection.toMovieTitle,
        focus: connection.toMovieId === movie?.id,
        x: position?.x,
        y: position?.y,
      });
    }
  }

  return [...nodes.values()];
}

function buildGraphLinks(connections: Connection[]) {
  return connections.map((connection) => ({
    id: connection.id,
    source: connection.fromMovieId,
    target: connection.toMovieId,
    weight: connection.weight,
  }));
}

function persistNodePositions(nodes: GraphNode[], positions: Record<number, { x: number; y: number }>) {
  for (const node of nodes) {
    if (node.x === undefined || node.y === undefined) {
      continue;
    }
    positions[node.id] = { x: node.x, y: node.y };
  }
}

function applyHighlightState(
  nodeSelection: d3.Selection<SVGGElement, GraphNode, SVGGElement, unknown>,
  linkSelection: d3.Selection<SVGLineElement, GraphLink, SVGGElement, unknown>,
  selectedMovieId: number | null,
  selectedConnectionId: number | null,
  dimMovieIds: Set<number>,
  dimConnectionIds: Set<number>,
) {
  nodeSelection
    .style("opacity", (datum) => {
      if (selectedMovieId === datum.id) {
        return 1;
      }
      return dimMovieIds.has(datum.id) ? 0.18 : 1;
    });

  nodeSelection.select("circle")
    .attr("fill", (datum) => {
      if (selectedMovieId === datum.id) {
        return "#7ce0ff";
      }
      if (datum.focus) {
        return "#ff8f00";
      }
      return "#d4ff4f";
    })
    .attr("stroke-width", (datum) => selectedMovieId === datum.id ? 2.8 : 1.35)
    .attr("filter", (datum) => selectedMovieId === datum.id
      ? "drop-shadow(0 0 18px rgba(124, 224, 255, 0.34))"
      : datum.focus
        ? "drop-shadow(0 0 18px rgba(255, 143, 0, 0.26))"
        : "drop-shadow(0 0 12px rgba(212, 255, 79, 0.18))");

  linkSelection
    .attr("stroke-linecap", "round")
    .attr("stroke", (datum) => selectedConnectionId === datum.id ? "#c084fc" : "rgba(124, 224, 255, 0.5)")
    .attr("stroke-width", (datum) => {
      if (selectedConnectionId === datum.id) {
        return 4.4;
      }
      return dimConnectionIds.has(datum.id) ? Math.max(1, mapEdgeWidth(datum.weight) - 0.7) : mapEdgeWidth(datum.weight);
    })
    .attr("stroke-opacity", (datum) => {
      if (selectedConnectionId === datum.id) {
        return 0.98;
      }
      return dimConnectionIds.has(datum.id) ? 0.08 : 0.46;
    });
}

function mapEdgeWidth(weight: number) {
  const minWeight = 0.1;
  const maxWeight = 3;
  const minWidth = 1.4;
  const maxWidth = 3.6;
  const normalized = Math.max(0, Math.min(1, (weight - minWeight) / (maxWeight - minWeight)));
  return minWidth + normalized * (maxWidth - minWidth);
}

function resolveLayoutConfig(
  layoutMode: "default" | "compact",
  nodeCount: number,
  linkCount: number,
): LayoutConfig {
  const density = nodeCount > 1 ? linkCount / nodeCount : 0;
  if (layoutMode === "compact") {
    return {
      linkDistance: density > 1.1 ? 88 : 98,
      linkStrength: density > 1.1 ? 0.46 : 0.4,
      chargeStrength: density > 1.1 ? -360 : -430,
      centeringStrength: 0.075,
      collisionRadius: 24,
      focusCollisionRadius: 30,
    };
  }

  return {
    linkDistance: density > 1.1 ? 105 : 118,
    linkStrength: 0.36,
    chargeStrength: density > 1.1 ? -460 : -560,
    centeringStrength: 0.045,
    collisionRadius: 28,
    focusCollisionRadius: 36,
  };
}

function readNodeX(node: number | GraphNode) {
  return typeof node === "number" ? 0 : node.x ?? 0;
}

function readNodeY(node: number | GraphNode) {
  return typeof node === "number" ? 0 : node.y ?? 0;
}

function readViewport(container: HTMLDivElement) {
  return {
    width: container.clientWidth || DEFAULT_WIDTH,
    height: container.clientHeight || DEFAULT_HEIGHT,
  };
}
