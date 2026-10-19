package com.lawyus.snackstore.gateway.config;

import com.lawyus.snackstore.common.constant.TraceConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    /**
     * 跨域配置
     * 在 Nacos 配置文件中配置的跨域内容不生效，在这里手动配置 Bean 解决不生效问题
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("*");
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        // 暴露请求 id，否则浏览器脚本读不到，前端上报错误码时带不上可关联日志的 id
        config.addExposedHeader(TraceConstants.HEADER_REQUEST_ID);
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}
