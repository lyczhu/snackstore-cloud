package com.lawyus.snackstore.gateway.route;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.AbstractListener;
import com.alibaba.nacos.api.exception.NacosException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 由 Nacos 驱动的网关路由仓库（方案 A）。
 *
 * <p>背景：当前 SCA 版本在 config-data 模式下，Nacos 配置变更不会桥接成 Spring 的
 * EnvironmentChangeEvent / RefreshScopeRefreshedEvent，因此 {@code spring.cloud.gateway.routes}
 * 属性式路由不会随 Nacos 变更而刷新。本仓库绕开该失效链路：直接从 Nacos {@link ConfigService}
 * 读取路由配置作为唯一来源，并在配置变更时主动发布 {@link RefreshRoutesEvent}，使
 * {@code CachingRouteLocator} 重新加载路由。</p>
 *
 * <p>路由定义存放于 Nacos dataId {@value #DATA_ID}（group {@value #GROUP}），结构为顶层
 * {@code routes} 列表；{@code predicates}/{@code filters} 使用网关 shortcut 文本格式
 * （如 {@code Path=/api/users/**}、{@code StripPrefix=1}）。</p>
 *
 * <p>作为 {@link RouteDefinitionRepository} 的自定义实现，它会抑制网关默认的
 * {@code InMemoryRouteDefinitionRepository}（{@code @ConditionalOnMissingBean}）；同时路由不再
 * 经由 {@code spring.cloud.gateway.routes} 绑定，故也不会与 {@code PropertiesRouteDefinitionLocator}
 * 产生重复。路由的增删改统一通过 Nacos 完成，网关侧写入接口被显式拒绝。</p>
 */
@Component
public class NacosRouteDefinitionRepository implements RouteDefinitionRepository {

    private static final Logger log = LoggerFactory.getLogger(NacosRouteDefinitionRepository.class);

    private static final String DATA_ID = "api-gateway-routes.yml";
    private static final String GROUP = "DEFAULT_GROUP";
    private static final long TIMEOUT_MS = 5000L;

    private final ConfigService configService;
    private final ApplicationEventPublisher publisher;
    // SafeConstructor：仅解析为 Map/List/标量，禁止 YAML 标签实例化任意对象
    private final Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

    private volatile List<RouteDefinition> routeDefinitions = List.of();

    public NacosRouteDefinitionRepository(NacosConfigManager nacosConfigManager,
                                          ApplicationEventPublisher publisher) {
        this.configService = nacosConfigManager.getConfigService();
        this.publisher = publisher;
        loadRoutes(fetchConfig());
        registerListener();
    }

    private String fetchConfig() {
        try {
            return configService.getConfig(DATA_ID, GROUP, TIMEOUT_MS);
        } catch (NacosException e) {
            log.error("拉取 Nacos 路由配置失败 dataId={}, group={}", DATA_ID, GROUP, e);
            return null;
        }
    }

    private void registerListener() {
        try {
            configService.addListener(DATA_ID, GROUP, new AbstractListener() {
                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("收到 Nacos 路由配置变更 dataId={}, group={}，刷新网关路由", DATA_ID, GROUP);
                    loadRoutes(configInfo);
                    publisher.publishEvent(new RefreshRoutesEvent(this));
                }
            });
        } catch (NacosException e) {
            log.error("注册 Nacos 路由监听失败 dataId={}, group={}，路由将无法动态刷新", DATA_ID, GROUP, e);
        }
    }

    private void loadRoutes(String content) {
        List<RouteDefinition> parsed = parse(content);
        this.routeDefinitions = parsed;
        log.info("加载网关路由 {} 条（来源 Nacos dataId={}）", parsed.size(), DATA_ID);
    }

    private List<RouteDefinition> parse(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        Object root = yaml.load(content);
        if (!(root instanceof Map<?, ?> rootMap)) {
            log.warn("路由配置根节点不是映射结构，忽略 dataId={}", DATA_ID);
            return List.of();
        }
        Object raw = rootMap.get("routes");
        if (!(raw instanceof List<?> rawList)) {
            log.warn("路由配置缺少 routes 列表，忽略 dataId={}", DATA_ID);
            return List.of();
        }
        List<RouteDefinition> result = new ArrayList<>(rawList.size());
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> m) {
                RouteDefinition rd = toRouteDefinition(m);
                if (rd != null) {
                    result.add(rd);
                }
            }
        }
        return result;
    }

    private RouteDefinition toRouteDefinition(Map<?, ?> m) {
        Object id = m.get("id");
        Object uri = m.get("uri");
        if (!(id instanceof String idStr) || idStr.isBlank() || !(uri instanceof String uriStr) || uriStr.isBlank()) {
            log.warn("路由缺少有效的 id 或 uri，跳过：{}", m);
            return null;
        }
        RouteDefinition rd = new RouteDefinition();
        rd.setId(idStr);
        try {
            rd.setUri(URI.create(uriStr));
        } catch (IllegalArgumentException e) {
            log.warn("路由 uri 非法 id={}, uri={}，跳过", idStr, uriStr);
            return null;
        }
        if (m.get("order") instanceof Number order) {
            rd.setOrder(order.intValue());
        }
        if (m.get("predicates") instanceof List<?> predicates) {
            for (Object p : predicates) {
                if (p instanceof String text && !text.isBlank()) {
                    rd.getPredicates().add(new PredicateDefinition(text));
                }
            }
        }
        if (m.get("filters") instanceof List<?> filters) {
            for (Object f : filters) {
                if (f instanceof String text && !text.isBlank()) {
                    rd.getFilters().add(new FilterDefinition(text));
                }
            }
        }
        return rd;
    }

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        return Flux.fromIterable(routeDefinitions);
    }

    @Override
    public Mono<Void> save(Mono<RouteDefinition> route) {
        return Mono.error(new UnsupportedOperationException(
                "路由由 Nacos 统一管理，不支持通过网关写入，请修改 Nacos 配置：" + DATA_ID));
    }

    @Override
    public Mono<Void> delete(Mono<String> routeId) {
        return Mono.error(new UnsupportedOperationException(
                "路由由 Nacos 统一管理，不支持通过网关删除，请修改 Nacos 配置：" + DATA_ID));
    }
}
