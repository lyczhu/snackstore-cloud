package com.lawyus.snackstore.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 网关安全配置，从 Nacos(api-gateway.yml) 加载，运行时可通过 Nacos 刷新调整。
 * 未配置时使用代码内默认值（等价于历史硬编码行为）。
 */
@Component
@RefreshScope
@ConfigurationProperties(prefix = "gateway.security")
public class GatewaySecurityProperties {

    /** 白名单条目格式为 "HTTP方法:ANT路径"，方法与路径同时匹配才放行 */
    private Set<String> whitelist = Set.of(
            "POST:/api/auth/sms-code",
            "POST:/api/auth/register",
            "POST:/api/auth/login",
            "POST:/api/auth/admin/login",
            "GET:/api/products/**",
            "GET:/api/products/categories/**",
            "GET:/api-docs/**"
    );

    /** 仅管理员可访问的前缀路径 */
    private Set<String> adminPrefixPatterns = Set.of(
            "/api/admin/**",
            "/api/auth/admin/**"
    );

    /** 仅管理员可访问的敏感接口，格式同白名单 "HTTP方法:ANT路径" */
    private Set<String> adminOnlyPatterns = Set.of(
            "POST:/api/products",
            "PUT:/api/products/*",
            "DELETE:/api/products/*",
            "PATCH:/api/products/*/status",
            "POST:/api/products/*/stock/deductions",
            "POST:/api/products/*/stock/rollbacks",
            "POST:/api/products/stock/batch-deductions",
            "POST:/api/products/stock/batch-rollbacks",
            "POST:/api/products/categories",
            "PUT:/api/products/categories/**",
            "DELETE:/api/products/categories/**",
            "GET:/api/statistics/**"
    );

    public Set<String> getWhitelist() {
        return whitelist;
    }

    public void setWhitelist(Set<String> whitelist) {
        this.whitelist = whitelist;
    }

    public Set<String> getAdminPrefixPatterns() {
        return adminPrefixPatterns;
    }

    public void setAdminPrefixPatterns(Set<String> adminPrefixPatterns) {
        this.adminPrefixPatterns = adminPrefixPatterns;
    }

    public Set<String> getAdminOnlyPatterns() {
        return adminOnlyPatterns;
    }

    public void setAdminOnlyPatterns(Set<String> adminOnlyPatterns) {
        this.adminOnlyPatterns = adminOnlyPatterns;
    }
}
