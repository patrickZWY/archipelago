package com.archipelago.mapper;

import com.archipelago.model.SharedGraphExport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;
import java.util.List;

@Mapper
public interface SharedGraphExportMapper {
    void insert(SharedGraphExport export);

    Optional<SharedGraphExport> findByShareToken(@Param("shareToken") String shareToken);

    List<SharedGraphExport> findByOwnerUserId(@Param("ownerUserId") Long ownerUserId);

    int deleteByShareTokenAndOwnerUserId(@Param("shareToken") String shareToken, @Param("ownerUserId") Long ownerUserId);
}
