import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
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

const cytoscapeTestState = vi.hoisted(() => {
  const zoomMock = vi.fn();
  const fitMock = vi.fn();
  const centerMock = vi.fn();
  const destroyMock = vi.fn();
  const layoutRunMock = vi.fn();
  const layoutMock = vi.fn(() => ({ run: layoutRunMock }));
  const makeCollectionEntry = () => ({ data: vi.fn() });
  const cytoscapeMock = vi.fn(() => ({
    nodes: () => [makeCollectionEntry(), makeCollectionEntry(), makeCollectionEntry()],
    edges: () => [],
    on: vi.fn(),
    $id: vi.fn(() => ({ data: vi.fn() })),
    layout: layoutMock,
    fit: fitMock,
    center: centerMock,
    zoom: zoomMock,
    destroy: destroyMock,
    extent: () => ({ x1: 0, x2: 100, y1: 0, y2: 100 }),
  }));

  return {
    zoomMock,
    fitMock,
    centerMock,
    destroyMock,
    layoutRunMock,
    layoutMock,
    cytoscapeMock,
  };
});

vi.mock("cytoscape", () => ({
  default: cytoscapeTestState.cytoscapeMock,
}));

describe("ConnectionGraph", () => {
  it("renders a zoom slider and disables wheel zoom", () => {
    cytoscapeTestState.zoomMock.mockReset();
    cytoscapeTestState.zoomMock.mockReturnValue(1);
    cytoscapeTestState.cytoscapeMock.mockClear();

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
    expect(cytoscapeTestState.cytoscapeMock).toHaveBeenCalledWith(expect.objectContaining({
      userZoomingEnabled: false,
    }));
  });

  it("updates graph zoom from the slider", () => {
    cytoscapeTestState.zoomMock.mockReset();
    cytoscapeTestState.zoomMock.mockImplementation((arg?: unknown) => (arg === undefined ? 1 : undefined));

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

    fireEvent.change(screen.getByLabelText("Graph zoom"), { target: { value: "1.4" } });

    expect(cytoscapeTestState.zoomMock).toHaveBeenCalledWith(expect.objectContaining({
      level: 1.4,
    }));
  });
});
