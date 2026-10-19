package com.lawyus.snackstore.common.util;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 请求关联 id 的生成与消毒：只做纯字符串逻辑，不依赖任何 web 类型，
 * 因此网关（reactive）与各服务（servlet）可以共用同一套规则。
 */
public final class TraceIdUtil {

    /** 合法 id 形态：仅字母数字下划线连字符，长度 1-64 */
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    /** 生成 32 位十六进制 id，无连字符、无分隔符冲突 */
    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 采用上游传来的 id，但仅在形态合法时采用；缺失或形态非法则重新生成。
     *
     * <p>用途是服务被直连（内部 Feign 调用、运维端口、本地调试）时的兜底，
     * 同时阻断通过伪造请求头向日志注入换行、ANSI 转义或超长内容的可能。
     */
    public static String resolveOrGenerate(String inbound) {
        return (inbound != null && SAFE_ID.matcher(inbound).matches()) ? inbound : generate();
    }

    private TraceIdUtil() {
    }
}
