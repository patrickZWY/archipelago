package com.archipelago.model;

import com.archipelago.model.enums.Role;
//import jakarta.persistence.*;
import jakarta.persistence.OrderBy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

//@Entity
//@Table(name = "users", uniqueConstraints = {
//        @UniqueConstraint(columnNames = "username"),
//        @UniqueConstraint(columnNames = "email")
//})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

//    @Column(nullable = false, unique = true, length = 100)
    private String email;

//    @Column(nullable = false)
    private String password;

//    @Column(nullable = false, unique = true, length = 50)
    private String username;

//    @Column(nullable = false, updatable = false)
    private LocalDateTime creationTime;

//    @Column(nullable = false)
    private LocalDateTime updateTime;

//    @Column(nullable = true, unique = true)
    private String verificationToken;

//    @Column(nullable = false)
    @Builder.Default
    private boolean verified = false;

    @Builder.Default
    private boolean enabled = true;

    private String passwordResetToken;

    private LocalDateTime passwordResetTokenExpireTime;

    @Builder.Default
    private int failedLoginAttempts = 0;

    private LocalDateTime lockoutTime;

    @Builder.Default
    private boolean deleted = false;

//    @Enumerated(EnumType.STRING)
    private Role role = Role.USER;

//    @PreUpdate
    protected void setUpdate() {
        this.updateTime = LocalDateTime.now();
    }

//    @PrePersist
    protected void onCreate() {
        this.creationTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
    }




}
