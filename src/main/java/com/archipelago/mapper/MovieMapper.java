package com.archipelago.mapper;

import com.archipelago.model.Movie;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface MovieMapper {
    Optional<Movie> findById(@Param("id") Long id);

    int countByTitle(@Param("title") String title);

    List<Movie> searchMovies(@Param("title") String title);
}
