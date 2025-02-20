package com.archipelago.mapper;

import com.archipelago.model.Connection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ConnectionMapper {
    List<Connection> findByUserId(@Param("userId") Long userId);

    void insert(Connection connection);

    Optional<Connection> findById(@Param("id") Long id);

    void update(Connection connection);

    void delete(@Param("id") Long id);

}
