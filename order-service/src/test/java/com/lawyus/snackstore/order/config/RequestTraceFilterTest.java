package com.lawyus.snackstore.order.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import com.lawyus.snackstore.common.constant.TraceConstants;
import com.lawyus.snackstore.common.util.TraceIdUtil;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 下游侧请求关联测试：过滤器把 id 送进 MDC，且 nacos-config 里下发的日志格式
 * 确实能把这个 MDC 值渲染出来——两件事分开看都成立，合起来才是"跨服务能串日志"。
 */
@DisplayName("RequestTraceFilter 下游 MDC 与日志格式")
class RequestTraceFilterTest {

    private static final Pattern GENERATED_ID = Pattern.compile("[0-9a-f]{32}");
    private static final Logger LOG = (Logger) org.slf4j.LoggerFactory.getLogger(RequestTraceFilterTest.class);

    private final RequestTraceFilter filter = new RequestTraceFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("网关注入的 id 在请求期间可见于 MDC，请求结束后被清理")
    void gatewayRequestIdIsExposedToMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader(TraceConstants.HEADER_REQUEST_ID, "abc123DEF-456");
        AtomicReference<String> duringRequest = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), recordingChain(duringRequest));

        assertThat(duringRequest.get()).isEqualTo("abc123DEF-456");
        assertThat(MDC.get(TraceConstants.MDC_REQUEST_ID)).isNull();
    }

    @Test
    @DisplayName("没有该头时自行生成（内部 Feign 直连、运维端口也有可关联 id）")
    void missingHeaderFallsBackToGeneratedId() throws Exception {
        AtomicReference<String> duringRequest = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/orders"),
                new MockHttpServletResponse(), recordingChain(duringRequest));

        assertThat(duringRequest.get()).matches(GENERATED_ID);
    }

    @Test
    @DisplayName("伪造的非法 id 不会进入日志上下文")
    void illegalHeaderIsRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader(TraceConstants.HEADER_REQUEST_ID, "evil\r\nFAKE 200 OK");
        AtomicReference<String> duringRequest = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), recordingChain(duringRequest));

        assertThat(duringRequest.get()).doesNotContain("evil").matches(GENERATED_ID);
    }

    @Test
    @DisplayName("超长 id 同样被丢弃，避免日志行被撑爆")
    void oversizedHeaderIsRejected() throws Exception {
        assertThat(TraceIdUtil.resolveOrGenerate("x".repeat(65))).doesNotStartWith("xxx");
        assertThat(TraceIdUtil.resolveOrGenerate("x".repeat(64))).isEqualTo("x".repeat(64));
        assertThat(TraceIdUtil.resolveOrGenerate(null)).matches(GENERATED_ID);
        assertThat(TraceIdUtil.resolveOrGenerate("带中文的id")).doesNotContain("中文");
    }

    @Test
    @DisplayName("nacos-config 下发的 console 日志格式能把该 id 渲染进日志行")
    void sharedLoggingPatternRendersRequestId() throws Exception {
        String shipped = consolePatternFromNacosConfig();
        // 配置契约：id 字段与 MDC 键同名（允许 %X{requestId} 与带默认值的 %X{requestId:-}）
        assertThat(shipped).contains("%X{" + TraceConstants.MDC_REQUEST_ID).contains("%m%n");
        // 必须保持 logback 原生写法：%clr / %wEx 是 Spring Boot 注册的，
        // 混进来说明离线渲染与真实启动不等价（上一版就是靠 %clr{...} 把服务启动搞坏的）
        assertThat(shipped).doesNotContain("%clr").doesNotContain("%wEx");

        // 必须用默认上下文：MDC 写入的是 SLF4J 绑定的那个 LoggerContext，另起一个拿不到 id
        LoggerContext context = (LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        // 原文渲染，不做任何改写：离线能渲染 == 运行期能渲染
        encoder.setPattern(shipped);
        encoder.setCharset(StandardCharsets.UTF_8);
        encoder.start();

        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        OutputStreamAppender<ILoggingEvent> appender = new OutputStreamAppender<>();
        appender.setContext(context);
        appender.setEncoder(encoder);
        appender.setOutputStream(sink);
        appender.start();

        Logger logger = context.getLogger(RequestTraceFilterTest.class.getName());
        logger.setLevel(Level.INFO);
        logger.setAdditive(false);
        logger.addAppender(appender);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader(TraceConstants.HEADER_REQUEST_ID, "cafebabe0011");
        try {
            filter.doFilter(request, new MockHttpServletResponse(), (req, resp) ->
                    LOG.info("下单校验开始"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            encoder.stop();
        }

        String rendered = sink.toString(StandardCharsets.UTF_8);
        // 格式串非法时 encoder.start() 会回退成不含字段的输出，因此断言内容即可覆盖格式有效性
        assertThat(rendered).contains("[cafebabe0011]").contains("下单校验开始");
    }

    @SuppressWarnings("unchecked")
    private static String consolePatternFromNacosConfig() throws Exception {
        // surefire 的 user.dir 为模块目录，共享配置位于仓库根的 nacos-config/
        Path config = Path.of("..", "nacos-config", "application.yml").toAbsolutePath();
        assertThat(Files.exists(config)).as("共享配置 %s 应存在", config).isTrue();
        try (var in = Files.newInputStream(config)) {
            Map<String, Object> root = new Yaml().load(in);
            Map<String, Object> logging = (Map<String, Object>) root.get("logging");
            Map<String, Object> pattern = (Map<String, Object>) logging.get("pattern");
            return (String) pattern.get("console");
        }
    }

    private static FilterChain recordingChain(AtomicReference<String> target) {
        return (req, resp) -> target.set(MDC.get(TraceConstants.MDC_REQUEST_ID));
    }
}
