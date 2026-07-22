package com.lawyus.snackstore.statistics.client.user;

import com.lawyus.snackstore.common.response.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserClientFallbackFactory implements FallbackFactory<UserClient> {

    @Override
    public UserClient create(Throwable cause) {
        log.error("UserClient fallback triggered: {}", cause.getMessage(), cause);
        return () -> Result.failed(500, "UserService unavailable");
    }
}
