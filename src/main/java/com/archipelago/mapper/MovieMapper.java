package com.archipelago.mapper;

import com.archipelago.model.Movie;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface MovieMapper {
    Optional<Movie> findById(@Param("id") Long id);

    Optional<Movie> findByExternalId(@Param("externalId") String externalId);

    Optional<Movie> findByTitleAndReleaseYear(@Param("title") String title, @Param("releaseYear") int releaseYear);

    int countByTitle(@Param("title") String title);

    List<Movie> findAll();

    List<Movie> findDistinctByUserId(@Param("userId") Long userId);

    List<Movie> searchMovies(@Param("title") String title, @Param("limit") int limit);

    List<Movie> searchMoviesFiltered(
            @Param("query") String query,
            @Param("person") String person,
            @Param("genre") String genre,
            @Param("releaseYear") Integer releaseYear,
            @Param("graphStatus") String graphStatus,
            @Param("userId") Long userId,
            @Param("limit") int limit
    );

    void insert(Movie movie);

    void update(Movie movie);
}
