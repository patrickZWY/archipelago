import cytoscape from "cytoscape";
import { useEffect, useRef } from "react";
import { buildGraphElements } from "../lib/graph";
import type { Connection, Movie } from "../lib/types";

type Props = {
  movie: Movie | null;
  connections: Connection[];
};

export function ConnectionGraph({ movie, connections }: Props) {
  const containerRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!containerRef.current) {
      return;
    }

    const graph = cytoscape({
      container: containerRef.current,
      elements: buildGraphElements(movie, connections),
      layout: { name: "cose", animate: false },
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

    return () => {
      graph.destroy();
    };
  }, [movie, connections]);

  return <div className="graph-canvas" ref={containerRef} aria-label="Connection graph" />;
}
