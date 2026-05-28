import cytoscape from "cytoscape";
import { useEffect, useRef, useState } from "react";
import { buildGraphElements, buildGraphLayout } from "../lib/graph";
import type { Connection, Movie } from "../lib/types";

type Props = {
  movie: Movie | null;
  connections: Connection[];
};

const MIN_ZOOM = 0.4;
const MAX_ZOOM = 2.2;

export function ConnectionGraph({ movie, connections }: Props) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const graphRef = useRef<cytoscape.Core | null>(null);
  const [zoom, setZoom] = useState(1);

  useEffect(() => {
    if (!containerRef.current) {
      return;
    }

    const elements = buildGraphElements(movie, connections);
    const graph = cytoscape({
      container: containerRef.current,
      elements,
      minZoom: MIN_ZOOM,
      maxZoom: MAX_ZOOM,
      userZoomingEnabled: false,
      style: [
        {
          selector: "node",
          style: {
            "background-color": "#d4ff4f",
            color: "#f7fffb",
            label: "data(label)",
            "font-family": "IBM Plex Mono",
            "font-size": 11,
            "text-wrap": "wrap",
            "text-max-width": "90px",
            "text-valign": "top",
            "text-margin-y": -14,
            "text-outline-width": 2,
            "text-outline-color": "#041113",
          },
        },
        {
          selector: 'node[focus = "true"]',
          style: {
            "background-color": "#ff8f00",
          },
        },
        {
          selector: "edge",
          style: {
            width: 2,
            "line-color": "#7ce0ff",
            "target-arrow-color": "#7ce0ff",
            "target-arrow-shape": "triangle",
            "curve-style": "bezier",
            label: "data(label)",
            color: "#dfe8eb",
            "font-family": "IBM Plex Mono",
            "font-size": 9,
            "text-rotation": "autorotate",
            "text-margin-y": -12,
            "text-background-opacity": 1,
            "text-background-color": "#07131b",
            "text-background-padding": "3px",
            "text-border-width": 1,
            "text-border-color": "#7ce0ff",
          },
        },
      ],
    });
    graphRef.current = graph;

    const selectedNodeId = movie ? `movie-${movie.id}` : undefined;
    const nodeCount = graph.nodes().length;
    if (nodeCount === 1) {
      graph.fit(graph.nodes(), 56);
      graph.center(graph.nodes());
    } else {
      const layout = buildGraphLayout(movie, connections);
      if (layout) {
        graph.layout(layout).run();
      } else if (selectedNodeId) {
        graph.center(graph.$id(selectedNodeId));
      }
    }
    setZoom(graph.zoom());

    return () => {
      graphRef.current = null;
      graph.destroy();
    };
  }, [movie, connections]);

  function handleZoomChange(nextZoom: number) {
    setZoom(nextZoom);
    const graph = graphRef.current;
    if (!graph) {
      return;
    }

    const extent = graph.extent();
    graph.zoom({
      level: nextZoom,
      renderedPosition: {
        x: containerRef.current?.clientWidth ? containerRef.current.clientWidth / 2 : (extent.x1 + extent.x2) / 2,
        y: containerRef.current?.clientHeight ? containerRef.current.clientHeight / 2 : (extent.y1 + extent.y2) / 2,
      },
    });
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
      <div className="graph-canvas" ref={containerRef} aria-label="Connection graph" />
    </div>
  );
}
