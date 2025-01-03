package com.archipelago.auth;

import com.archipelago.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    @Value("${jwt.secret}")
    private String secret;

    private SecretKey key;

    private final long EXPIRATION_TIME = 3600000;

    @PostConstruct
    public void init() {
        if (secret == null || secret.isEmpty()) {
            throw new IllegalStateException("JWT_SECRET_KEY was not set");
        }
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(User user) {
        logger.debug("Generating token attempt for user: {}", user.getEmail());
        String token = Jwts.builder()
                .setSubject(user.getEmail())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();
        logger.debug("Token generated: {}", token);
        return token;
    }

    public boolean validateToken(String token) {
        try {
            logger.debug("Validating token attempt: {}", token);
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            logger.info("Token is valid: {}", token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            logger.warn("Token validation failed for {}: {}", token, e.getMessage());
            return false;
        }
    }

    public String getEmailFromToken(String token) {
        logger.debug("Getting email from token: {}", token);
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        logger.debug("Email is: {}", claims.getSubject());
        return claims.getSubject();
    }

    public void invalidateToken(String token) {
        logger.info("Invalidating token attempt: {}", token);
        // work with blacklist tokens
    }

}
