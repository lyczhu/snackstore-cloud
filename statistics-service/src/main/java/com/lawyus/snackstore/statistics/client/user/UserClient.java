package com.lawyus.snackstore.statistics.client.user;

import com.lawyus.snackstore.common.response.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "user-service", fallbackFactory = UserClientFallbackFactory.class)
public interface UserClient {

    @GetMapping("/internal/users/count")
    Result<Long> countUsers();
}
