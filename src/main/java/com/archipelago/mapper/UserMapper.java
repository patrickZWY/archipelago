package com.archipelago.mapper;

import com.archipelago.model.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface UserMapper {
    void insert(User user);

    @Select("SELECT * FROM users WHERE id = #{id}")
    User findById(@Param("id") Long id);

    int countByEmail(@Param("email") String email);

    int countByUsernameIgnoreCase(@Param("username") String username);

    Optional<User> findByEmail(@Param("email") String email);

    Optional<User> findByVerificationToken(@Param("token") String token);

    Optional<User> findByPasswordResetToken(@Param("token") String token);

    void update(User user);

    void updateProfile(@Param("email") String email,
                       @Param("username") String username,
                       @Param("password") String password);

}
