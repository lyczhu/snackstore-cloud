package com.lawyus.snackstore.user.domain.user.port;

public interface VerificationCodePort {

    String get(String phone);

    void invalidate(String phone);
}