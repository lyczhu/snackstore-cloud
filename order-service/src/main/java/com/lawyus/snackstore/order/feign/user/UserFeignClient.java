package com.lawyus.snackstore.order.feign.user;

import com.lawyus.snackstore.common.response.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", fallbackFactory = UserClientFallbackFactory.class)
public interface UserFeignClient {

    @GetMapping("/user/{id}")
    Result<UserFeignVO> getUserById(@PathVariable("id") Long id);
}
