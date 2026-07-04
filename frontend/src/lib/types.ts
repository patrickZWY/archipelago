import type { ConnectionCategory } from "./connection-categories";

export type UserSummary = {
  id: number;
  email: string;
  username: string;
};

export type PublicUser = {
  id: number;
  username: string;
};

export type SessionResponse = {
  authenticated: boolean;
  user: UserSummary | null;
};

export type ApiEnvelope<T> = {
  success: boolean;
  data: T;
  message: string;
};

export type Movie = {
  id: number;
  title: string;
  releaseYear: number;
  director: string;
  pictureUrl: string | null;
  externalId: string | null;
  tagline: string | null;
  synopsis: string | null;
  genres: string[];
  runtimeMinutes: number | null;
  castMembers: string[];
  directorNotes: string | null;
};

export type Connection = {
  id: number;
  fromMovieId: number;
  fromMovieTitle: string;
  toMovieId: number;
  toMovieTitle: string;
  reason: string;
  weight: number;
  category: ConnectionCategory | null;
};

export type GlobalGraphConnection = Connection & {
  aggregate: boolean;
  contributors: PublicUser[];
  contributorCount: number;
};

export type MovieConnections = {
  movie: Movie;
  movies: Movie[];
  connections: Connection[];
};

export type MoviePath = {
  fromMovie: Movie;
  toMovie: Movie;
  movies: Movie[];
  connections: Connection[];
};

export type GlobalGraph = {
  movies: Movie[];
  connections: GlobalGraphConnection[];
};

export type GlobalGraphPath = {
  fromMovie: Movie;
  toMovie: Movie;
  movies: Movie[];
  connections: GlobalGraphConnection[];
};

export type CatalogImport = {
  source: string;
  insertedCount: number;
  updatedCount: number;
  totalProcessed: number;
};

export type SharedGraphExport = {
  shareToken: string;
  shareUrl: string;
  title: string;
  graph: MovieConnections;
};

export type SharedGraphExportSummary = {
  shareToken: string;
  shareUrl: string;
  title: string;
  rootMovieId: number;
  rootMovieTitle: string;
  creationTime: string;
};

export type SharedGraph = {
  shareToken: string;
  title: string;
  graph: MovieConnections;
};

export type UserProfile = {
  id: number;
  username: string;
  email: string;
  enabled: boolean;
  verified: boolean;
  accountStatus: "PENDING_VERIFICATION" | "PENDING_APPROVAL" | "ACTIVE" | "DISABLED" | "DELETED";
};

export type FriendRequest = {
  id: number;
  status: "PENDING" | "ACCEPTED" | "DECLINED";
  requester: PublicUser;
  recipient: PublicUser;
  otherUser: PublicUser;
};

export type FriendRequests = {
  incoming: FriendRequest[];
  outgoing: FriendRequest[];
};

export type FriendProfile = {
  id: number;
  username: string;
  movies: Movie[];
};
