package com.lawyus.snackstore.common.constant;

/**
 * 请求关联（trace）常量：网关为每个入站请求生成一次请求 id，经请求头透传到下游，
 * 下游服务把它放入 MDC，由日志 pattern 输出，用于串联一次跨服务请求的日志。
 *
 * <p>信任模型与 {@link AuthConstants} 的 X-User-* 一致：请求 id 只能由网关注入，
 * 客户端自带的同名头一律被网关丢弃，下游不得直接采信。
 */
public final class TraceConstants {

    /** 网关注入下游的请求 id 头（同时作为网关回写给客户端的响应头） */
    public static final String HEADER_REQUEST_ID = "X-Request-Id";

    /** MDC 键，需与各服务 logging.pattern 中的 %X{requestId} 保持一致 */
    public static final String MDC_REQUEST_ID = "requestId";

    private TraceConstants() {
    }
}
