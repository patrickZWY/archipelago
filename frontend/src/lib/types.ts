export type UserSummary = {
  id: number;
  email: string;
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
};

export type Connection = {
  id: number;
  fromMovieId: number;
  fromMovieTitle: string;
  toMovieId: number;
  toMovieTitle: string;
  reason: string;
  weight: number;
  category: string | null;
};

export type MovieConnections = {
  movie: Movie;
  connections: Connection[];
};

export type UserProfile = {
  id: number;
  username: string;
  email: string;
  enabled: boolean;
  verified: boolean;
};
