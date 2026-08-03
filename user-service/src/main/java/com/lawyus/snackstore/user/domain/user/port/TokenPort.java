package com.lawyus.snackstore.user.domain.user.port;

public interface TokenPort {

    String generate(Long userId, String phone, String role);

    void store(Long userId, String token);

    void revoke(Long userId);
}