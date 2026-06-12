package com.lawyus.snackstore.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.system.SystemBlockException;
import com.lawyus.snackstore.common.response.Result;
import com.lawyus.snackstore.common.response.ResultCode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Configuration
public class SentinelGatewayConfig {

    private static final Logger log = LoggerFactory.getLogger(SentinelGatewayConfig.class);

    @PostConstruct
    public void init() {
        GatewayCallbackManager.setBlockHandler(createBlockRequestHandler());
        log.info("Sentinel 网关自定义 BlockRequestHandler 已注册");
    }

    private BlockRequestHandler createBlockRequestHandler() {
        return (exchange, ex) -> {
            String path = exchange.getRequest().getURI().getPath();
            HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
            Result<Void> result;

            switch (ex) {
                case FlowException _ -> result = Result.failed(ResultCode.RATE_LIMIT_EXCEEDED);
                case DegradeException _ -> {
                    status = HttpStatus.SERVICE_UNAVAILABLE;
                    result = Result.failed(ResultCode.SERVICE_DEGRADED);
                }
                case SystemBlockException _ ->
                        result = Result.failed(ResultCode.RATE_LIMIT_EXCEEDED, "系统保护触发");
                case BlockException blockException -> result = Result.failed(ResultCode.RATE_LIMIT_EXCEEDED,
                        "被 Sentinel 拦截: " + blockException.getClass().getSimpleName());
                case null, default -> {
                    status = HttpStatus.INTERNAL_SERVER_ERROR;
                    result = Result.failed(ResultCode.INTERNAL_SERVER_ERROR);
                }
            }

            log.warn("网关限流/降级: path={}, type={}, message={}",
                    path, ex.getClass().getSimpleName(), result.getMessage());

            return ServerResponse.status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Mono.just(result), Result.class);
        };
    }
}

