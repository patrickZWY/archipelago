package com.archipelago.mapper;

import com.archipelago.catalog.CatalogMovieExternalId;
import com.archipelago.catalog.CatalogMovieGenre;
import com.archipelago.catalog.CatalogMoviePerson;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CatalogMetadataMapper {
    List<CatalogMovieExternalId> findExternalIdsByMovieId(@Param("movieId") Long movieId);

    List<CatalogMovieGenre> findGenresByMovieId(@Param("movieId") Long movieId);

    List<CatalogMoviePerson> findPeopleByMovieId(@Param("movieId") Long movieId);
}
