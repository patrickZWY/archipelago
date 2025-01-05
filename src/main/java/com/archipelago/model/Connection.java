package com.archipelago.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "connection")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Connection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "from_moive_id", nullable = false)
    private Movie fromMovie;

    @ManyToOne(optional = false)
    @JoinColumn(name = "to_movie_id", nullable = false)
    private Movie toMovie;

    @Column(nullable = false, length = 500)
    private String reason;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;























}
