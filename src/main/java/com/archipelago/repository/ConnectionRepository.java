package com.archipelago.repository;

import com.archipelago.model.Connection;
import com.archipelago.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConnectionRepository extends JpaRepository<Connection, Long> {
    List<Connection> findByUser(User user);
}
