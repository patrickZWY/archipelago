package com.archipelago.mapper;

import com.archipelago.model.Connection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ConnectionMapper {
    List<Connection> findByUserId(@Param("userId") Long userId);

    List<Connection> findByUserIdAndMovieId(@Param("userId") Long userId, @Param("movieId") Long movieId);

    void insert(Connection connection);

    Optional<Connection> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    void update(Connection connection);

    void deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
