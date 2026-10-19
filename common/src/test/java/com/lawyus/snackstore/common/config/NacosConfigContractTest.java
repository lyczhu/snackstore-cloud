package com.lawyus.snackstore.common.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * nacos-config 下发配置的最小契约检查：每份 YAML/JSON 能被解析、必需键存在、
 * 日志 pattern 只用 logback 原生写法。这些配置不参与编译，改坏了要到服务启动才崩，
 * 本测试把它们拉到合入前的机械检查里——不依赖 MySQL/Nacos 等任何外部服务。
 *
 * <p>surefire 的 user.dir 为模块目录（common），共享配置位于仓库根的 nacos-config/。
 */
@DisplayName("nacos-config 下发配置契约")
class NacosConfigContractTest {

    private static final Path NACOS_DIR = Path.of("..", "nacos-config").toAbsolutePath().normalize();

    /** 每份配置必需的顶层键，以及是否要求 spring.application.name 非空。 */
    private static final Map<String, Required> REQUIRED_KEYS = Map.of(
            "application.yml", new Required(Set.of("spring", "logging"), false),
            "api-gateway.yml", new Required(Set.of("server", "spring", "logging", "management"), true),
            "api-gateway-routes.yml", new Required(Set.of("routes"), false),
            "user-service.yml", new Required(Set.of("server", "spring", "logging"), true),
            "product-service.yml", new Required(Set.of("server", "spring", "logging"), true),
            "order-service.yml", new Required(Set.of("server", "spring", "logging"), true),
            "product-search-service.yml", new Required(Set.of("server", "spring", "logging"), true),
            "statistics-service.yml", new Required(Set.of("server", "spring", "logging"), true)
    );

    private record Required(Set<String> topKeys, boolean appName) {
    }

    static Stream<Arguments> yamlFiles() {
        return listFiles(".yml").stream().map(p -> Arguments.of(p.getFileName().toString(), p));
    }

    static Stream<Arguments> sentinelJsonFiles() {
        return listFiles(".json").stream().map(p -> Arguments.of(p.getFileName().toString(), p));
    }

    @ParameterizedTest(name = "{0} 可被 SnakeYAML 解析为非空映射")
    @MethodSource("yamlFiles")
    void everyYamlParsesToMap(String name, Path file) throws Exception {
        Map<String, Object> root = loadYaml(file);
        assertThat(root)
                .as("%s 应解析出非空 YAML 映射", name)
                .isNotNull()
                .isNotEmpty();
    }

    @ParameterizedTest(name = "{0} 含必需顶层键")
    @MethodSource("yamlFiles")
    void requiredTopKeysPresent(String name, Path file) throws Exception {
        Required required = REQUIRED_KEYS.get(name);
        assertThat(required).as("%s 应在必需键契约中登记", name).isNotNull();

        Map<String, Object> root = loadYaml(file);
        assertThat(root.keySet())
                .as("%s 缺少顶层必需键", name)
                .contains(required.topKeys().toArray(String[]::new));

        if (required.appName()) {
            assertThat(appName(root))
                    .as("%s 的 spring.application.name 必须非空", name)
                    .isNotBlank();
        }
    }

    @Test
    @DisplayName("下发的日志 pattern 只允许 logback 原生转换词，禁止 Spring 专有的 %clr/%wEx")
    void shippedLogPatternsUseOnlyNativeLogbackConverters() throws Exception {
        for (Path yml : listFiles(".yml")) {
            for (String pattern : collectPatternStrings(loadYaml(yml))) {
                assertThat(pattern)
                        .as("%s 的日志 pattern 含 Spring 专有转换词，离线渲染会抛错、服务启动即崩：%s",
                                yml.getFileName(), pattern)
                        .doesNotContain("%clr")
                        .doesNotContain("%wEx");
            }
        }
    }

    @ParameterizedTest(name = "{0} 解析为非空 JSON 数组")
    @MethodSource("sentinelJsonFiles")
    void sentinelRulesParseAsJsonArray(String name, Path file) throws Exception {
        JsonNode node = new ObjectMapper().readTree(Files.readString(file));
        assertThat(node.isArray())
                .as("%s 应是 Sentinel 规则数组", name)
                .isTrue();
        assertThat(node.size())
                .as("%s 规则数组不应为空", name)
                .isPositive();
    }

    @Test
    @DisplayName("网关安全策略锁定两项决策：内测关注册、快照仅管理员（admin-only 优先于白名单）")
    void gatewaySecurityPolicyLocksRegistrationAndSnapshotDecisions() throws Exception {
        Map<String, Object> root = loadYaml(NACOS_DIR.resolve("api-gateway.yml"));
        Map<String, Object> security = asMap(asMap(root.get("gateway")).get("security"));
        List<String> whitelist = asStringList(security.get("whitelist"));
        List<String> adminOnly = asStringList(security.get("admin-only-patterns"));

        assertThat(whitelist)
                .as("内测期自助注册关闭（决策 D7）：register/sms-code 不得匿名开放")
                .contains("POST:/api/auth/login", "POST:/api/auth/admin/login")
                .doesNotContain("POST:/api/auth/register", "POST:/api/auth/sms-code");

        assertThat(adminOnly)
                .as("商品变更快照属管理数据（决策 D8），且必须盖住 GET:/api/products/**")
                .contains("GET:/api/products/snapshots", "GET:/api/products/snapshots/**");
    }

    @Test
    @DisplayName("契约检查本身有牙：坏 YAML 会抛错、含 %clr 的 pattern 会被判违规")
    void guardIsNotVacuous() {
        // 未闭合的流式序列必须被解析步骤拒绝（ScannerException 是 YAMLException 子类）
        assertThatThrownBy(() -> safeYaml().load("a: [1, 2"))
                .isInstanceOf(YAMLException.class);

        // 安全加载器禁止 !!tag 构造任意对象（默认 Yaml 会尝试反射实例化）
        assertThatThrownBy(() -> safeYaml().load("a: !!com.example.Foo {}"))
                .isInstanceOf(YAMLException.class);

        // Boot 专有转换词必须被安全谓词判违规（正向样例同时应通过）
        assertThat(collectPatternStrings(Map.of(
                "logging", Map.of("pattern", Map.of("console", "%d{yyyy-MM-dd} [%X{requestId:-}] %m%n")))))
                .singleElement()
                .satisfies(p -> assertThat(p).doesNotContain("%clr").doesNotContain("%wEx"));
    }

    // ---- helpers ----

    /** 安全加载器：只产出 Map/List/标量，禁止 !!tag 构造任意应用对象。 */
    private static Yaml safeYaml() {
        return new Yaml(new SafeConstructor(new LoaderOptions()));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object node) {
        assertThat(node).as("YAML 节点应为映射").isInstanceOf(Map.class);
        return (Map<String, Object>) node;
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object node) {
        assertThat(node).as("YAML 节点应为列表").isInstanceOf(List.class);
        return (List<String>) node;
    }

    private static Map<String, Object> loadYaml(Path file) throws Exception {
        try (Reader reader = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
            Object root = safeYaml().load(reader);
            assertThat(root).as("%s 顶层应为映射", file.getFileName()).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) root;
            return map;
        }
    }

    @SuppressWarnings("unchecked")
    private static String appName(Map<String, Object> root) {
        Object spring = root.get("spring");
        if (!(spring instanceof Map)) {
            return null;
        }
        Object application = ((Map<String, Object>) spring).get("application");
        if (!(application instanceof Map)) {
            return null;
        }
        Object name = ((Map<String, Object>) application).get("name");
        return name == null ? null : String.valueOf(name);
    }

    /** 递归收集 logging.pattern.* 下的所有字符串值（跳过注释——注释不在解析结果里）。 */
    @SuppressWarnings("unchecked")
    private static List<String> collectPatternStrings(Map<String, Object> node) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Object> e : node.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Map) {
                if ("pattern".equals(e.getKey())) {
                    ((Map<String, Object>) v).values().forEach(pv -> {
                        if (pv instanceof String s) {
                            out.add(s);
                        }
                    });
                } else {
                    out.addAll(collectPatternStrings((Map<String, Object>) v));
                }
            }
        }
        return out;
    }

    private static List<Path> listFiles(String suffix) {
        try (var stream = Files.walk(NACOS_DIR)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(suffix))
                    .sorted()
                    .toList();
        } catch (Exception e) {
            throw new AssertionError("无法递归读取 nacos-config 目录：" + NACOS_DIR + "（应位于仓库根，surefire user.dir 为模块目录）", e);
        }
    }
}
