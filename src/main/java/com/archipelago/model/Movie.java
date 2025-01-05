package com.archipelago.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "movies")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    @NotBlank(message = "Title is required")
    private String title;

    @Column(nullable = false)
    private int releaseYear;

    @Column(nullable = false)
    @NotBlank(message = "Director is required")
    private String director;

    @Column
    private String pictureUrl;

    @Column
    private String externalId;

}
