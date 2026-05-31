package com.archipelago.mapper;

import com.archipelago.model.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;
import java.util.List;

@Mapper
public interface UserMapper {
    void insert(User user);

    @Select("SELECT * FROM users WHERE id = #{id} AND deleted = FALSE")
    Optional<User> findActiveById(@Param("id") Long id);

    int countByEmail(@Param("email") String email);

    int countByUsernameIgnoreCase(@Param("username") String username);

    int countByUsernameIgnoreCaseExcludingId(@Param("username") String username, @Param("id") Long id);

    Optional<User> findActiveByEmail(@Param("email") String email);

    Optional<User> findByVerificationToken(@Param("token") String token);

    Optional<User> findByPasswordResetToken(@Param("token") String token);

    List<User> searchByUsername(@Param("query") String query,
                                @Param("excludeUserId") Long excludeUserId,
                                @Param("limit") int limit);

    void update(User user);

    void updateProfile(@Param("id") Long id,
                       @Param("username") String username,
                       @Param("password") String password);

    void softDeleteById(@Param("id") Long id);
}
