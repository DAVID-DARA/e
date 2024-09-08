package com.project.ecommerce_api.entities;

import com.project.ecommerce_api.utilities.TokenType;
import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;


import java.time.LocalDateTime;

import java.util.UUID;

@Getter
@Setter
@ToString
@Entity
@Table(name = "tokens")
public class Token {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, unique = true)
    private UUID tokenId;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(nullable = false, updatable = false)
    private String token;

    @Enumerated(EnumType.STRING)
    private TokenType type;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime expiredAt;

    private Boolean isUsed;
}
