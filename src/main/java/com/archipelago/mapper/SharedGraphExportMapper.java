package com.archipelago.mapper;

import com.archipelago.model.SharedGraphExport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface SharedGraphExportMapper {
    void insert(SharedGraphExport export);

    Optional<SharedGraphExport> findByShareToken(@Param("shareToken") String shareToken);
}
