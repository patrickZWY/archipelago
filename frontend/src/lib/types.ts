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
  catalogGenres?: MovieCatalogGenre[];
  people?: MoviePerson[];
  externalIds?: MovieExternalId[];
};

export type MovieCatalogGenre = {
  provider: string;
  source: string;
  name: string;
};

export type MoviePerson = {
  provider: string;
  source: string;
  name: string;
  role: string;
  billingOrder: number;
};

export type MovieExternalId = {
  provider: string;
  source: string;
  type: string;
  value: string;
};

export type MovieGraphStatus = "all" | "in_graph" | "not_in_graph";

export type MovieSearchFilters = {
  query?: string;
  person?: string;
  genre?: string;
  year?: string;
  graphStatus?: MovieGraphStatus;
};

export type GraphSuggestionEvidence = {
  type: "SHARED_DIRECTOR" | "SHARED_CAST" | "SHARED_GENRE" | "SAME_DECADE";
  label: string;
  values: string[];
};

export type GraphSuggestion = {
  candidateMovie: Movie;
  category: ConnectionCategory;
  confidence: number;
  evidence: GraphSuggestionEvidence[];
  existingEdge: boolean;
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

export type CatalogImportAction = "INSERTED" | "UPDATED" | "SKIPPED" | "FAILED";

export type CatalogErrorKind =
  | "INVALID_INPUT"
  | "UNSUPPORTED_PROVIDER_CAPABILITY"
  | "PROVIDER_UNAVAILABLE"
  | "RATE_LIMITED_RETRYABLE_EXTERNAL_FAILURE"
  | "PERMANENT_PROVIDER_DATA_ERROR"
  | "IMPORT_CONFLICT";

export type CatalogImportItem = {
  action: CatalogImportAction;
  movieId: number | null;
  title: string | null;
  releaseYear: number | null;
  externalId: string | null;
  errorKind: CatalogErrorKind | null;
  message: string | null;
};

export type CatalogImport = {
  source: string;
  insertedCount: number;
  updatedCount: number;
  totalProcessed: number;
  provider: string;
  runId: string;
  operation: "PREVIEW" | "APPLY";
  skippedCount: number;
  failedCount: number;
  results: CatalogImportItem[];
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
