package com.lawyus.snackstore.order.feign.user;

import com.lawyus.snackstore.common.response.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

@Slf4j
public class UserClientFallbackFactory implements FallbackFactory<UserFeignClient> {
    @Override
    public UserFeignClient create(Throwable cause) {
        log.error("UserFeignClient fallback triggered: {}", cause.getMessage(), cause);
        return new UserFeignClient() {
            @Override
            public Result<UserFeignVO> getUserById(Long id) {
                return Result.failed(500, "UserService unavailable");
            }
        };
    }
}
