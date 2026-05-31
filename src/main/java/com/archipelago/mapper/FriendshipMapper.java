package com.archipelago.mapper;

import com.archipelago.model.Friendship;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface FriendshipMapper {
    void insert(Friendship friendship);

    void update(Friendship friendship);

    Optional<Friendship> findById(@Param("id") Long id);

    Optional<Friendship> findByUserPair(@Param("userIdA") Long userIdA, @Param("userIdB") Long userIdB);

    List<Friendship> findAcceptedForUser(@Param("userId") Long userId);

    List<Friendship> findIncomingPendingForUser(@Param("userId") Long userId);

    List<Friendship> findOutgoingPendingForUser(@Param("userId") Long userId);

    int countAcceptedBetweenUsers(@Param("userIdA") Long userIdA, @Param("userIdB") Long userIdB);

    void deleteAcceptedByUserPair(@Param("userIdA") Long userIdA, @Param("userIdB") Long userIdB);
}
