package com.lawyus.snackstore.common.constant;

/**
 * 认证相关常量：网关注入下游服务的请求头名称与角色标识。
 */
public final class AuthConstants {

    /** 管理员角色标识（X-User-Role 头值，比较时大小写不敏感） */
    public static final String ROLE_ADMIN = "admin";

    /** 网关注入的用户 ID 头 */
    public static final String HEADER_USER_ID = "X-User-Id";
    /** 网关注入的用户名头 */
    public static final String HEADER_USERNAME = "X-Username";
    /** 网关注入的用户角色头 */
    public static final String HEADER_USER_ROLE = "X-User-Role";

    private AuthConstants() {
    }
}
