package com.lawyus.snackstore.gateway.config;

import com.lawyus.snackstore.common.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

    @Bean
    public JwtUtil jwtUtil(@Value("${jwt.secret}") String secret,
                           @Value("${jwt.expiration}") long expiration) {
        return new JwtUtil(secret, expiration);
    }
}
