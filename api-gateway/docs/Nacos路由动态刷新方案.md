# Nacos 路由动态刷新方案

> 状态：**方案 A 已实施**（B/C 为备选/参考，未采用）
> 关联：`api-gateway`、`nacos-config/api-gateway-routes.yml`、`api-gateway/src/main/resources/application.yml`
> 适用版本：Spring Boot 3.5.15 / Spring Cloud 2025.0.2（Gateway 4.3.x）/ Spring Cloud Alibaba 2025.0.0.0

## 实施说明（方案 A）

已落地内容：

- 新增 `com.lawyus.snackstore.gateway.route.NacosRouteDefinitionRepository`：实现 `RouteDefinitionRepository`，构造时经 `NacosConfigManager` 的 `ConfigService` 拉取 dataId `api-gateway-routes.yml`（group `DEFAULT_GROUP`），并 `addListener` 监听变更；变更时重新解析并发布 `RefreshRoutesEvent`。YAML 用 SnakeYAML `SafeConstructor` 解析并做结构校验后映射为 `RouteDefinition`；`save`/`delete` 显式抛 `UnsupportedOperationException`（路由只由 Nacos 管理）。
- 重构 `nacos-config/api-gateway-routes.yml`：路由改为**顶层 `routes` 列表**（shortcut 格式），去掉 `spring.cloud.gateway` 前缀；原 discovery locator 关闭配置迁至 `api-gateway.yml`。
- 更新 `api-gateway/src/main/resources/application.yml`：移除对 `api-gateway-routes.yml` 的 `spring.config.import` 与 `extension-configs` 两处重复导入（改由 Repository 直读，避免与 `PropertiesRouteDefinitionLocator` 重复）。

> ⚠️ 部署前提：本地/Nacos 中的 `api-gateway-routes.yml` 需更新为新的顶层 `routes` 结构（旧格式 `spring.cloud.gateway.routes` 不再被读取）；网关需重新构建并重启以加载新的 Repository 类与 `application.yml`。

### 联调验证结论（已在本地 Nacos + 网关实测通过）

- 启动即从 Nacos 加载路由：日志 `加载网关路由 10 条`；探测 `/api/products/x` 返回 503（转发到 `lb://product-service`，服务未起故 503），证明路由生效。
- **不重启**向 Nacos 追加一条路由 → 日志 `收到 Nacos 路由配置变更` + `加载网关路由 11 条`，新路径由 404 变为 401（路由命中、JWT 拦截），实时生效。
- 再恢复原配置 → `加载网关路由 10 条`，该路径回到 404。**增、删路由均热生效**（验证了方案 A 相对 PDF 方案的删除闭环优势）。
- 验证时本地 Nacos 的 `api-gateway-routes.yml` 已更新为新格式（即最终所需状态）；Sentinel 因本地无规则 dataId，验证时以 `--spring.cloud.sentinel.enabled=false` 临时关闭，不影响本功能。

## 背景

有文章称：只要 Nacos 开启 `refresh: true`，Spring Cloud Gateway 借助 `CachingRouteDefinitionRepository` 内置的 `@RefreshScope` 支持，改完 Nacos 配置路由就会自动刷新，无需额外代码。

但当前系统在 Nacos 中修改路由配置后，网关**并未**实时刷新路由，`/actuator/gateway/routes` 返回的仍是旧路由。

结论：**该文章的说法在当前技术栈下不成立，必须写代码兜底。**

## 根因：刷新链路在两个环节都断了

### 环节一（网关侧）：`RouteRefreshListener` 不监听 `EnvironmentChangeEvent`

- 路由缓存由 `CachingRouteLocator` 持有，它**只在收到 `RefreshRoutesEvent` 时**才重新读取路由。
- `RefreshRoutesEvent` 由 `RouteRefreshListener` 发布，而它只响应以下事件：
  `ContextRefreshedEvent`、`RefreshScopeRefreshedEvent`、`InstanceRegisteredEvent`、`HeartbeatEvent`/`ParentHeartbeatEvent`。
  ——**不包含 `EnvironmentChangeEvent`**。
- 网关中也不存在文章所说的"CachingRouteDefinitionRepository 内置 @RefreshScope"这一机制；属性路由由 `PropertiesRouteDefinitionLocator` 从 `GatewayProperties` 读取，`GatewayProperties` 需要被重新绑定才能拿到新值。

### 环节二（Nacos / SCA 侧）：config-data 模式下没有把变更桥接成 Spring 刷新

当前 `api-gateway` 走的是 `spring.config.import: optional:nacos:...`（config-data 模式）。在 SCA 2025.0.0.0 中：

- Nacos 配置变更时，`NacosContextRefresher` 只发布 Alibaba 自己的 `NacosConfigRefreshEvent`。
- 本该把它转成 Spring 刷新（更新 Environment property source → 触发 `ConfigurationPropertiesRebinder` 重绑 `GatewayProperties` → 发 `RefreshScopeRefreshedEvent`）的 `NacosPropertySourceRefreshListener`，**在该版本里没有被注册为 bean（属死代码）**，也没有其他组件桥接。
- 结果：Environment 未更新 → `GatewayProperties` 未重绑 → 无 `RefreshScopeRefreshedEvent` → `RouteRefreshListener` 不发 `RefreshRoutesEvent` → `CachingRouteLocator` 一直是旧缓存。

两个环节叠加，导致属性式路由（`spring.cloud.gateway.routes`）在该版本下**不会随 Nacos 变更而刷新**，`/actuator/gateway/routes` 持续返回旧值。

> 三个方案的核心思路一致：**绕开坏掉的刷新链路，直接从 Nacos `ConfigService` 读取最新配置**，再主动触发路由刷新。

## 方案 A（推荐）：自定义 `RouteDefinitionRepository`

新增一个由 Nacos 驱动的路由仓库，作为路由的唯一定义来源，彻底不依赖 `GatewayProperties`。

要点：

1. 实现 `RouteDefinitionRepository`，`getRouteDefinitions()` 返回内存中最新的路由列表。
2. 构造时从 `ConfigService` 拉取一次路由 dataId，并 `addListener` 监听变更；变更时重新解析并 `publishEvent(new RefreshRoutesEvent(...))`。
3. **把路由从 `spring.cloud.gateway.routes` 键挪到独立结构**（否则 `PropertiesRouteDefinitionLocator` 会再加载一份，产生重复路由）。

```java
@Component
public class NacosRouteDefinitionRepository implements RouteDefinitionRepository {

    private static final String DATA_ID = "api-gateway-routes.yml";
    private static final String GROUP = "DEFAULT_GROUP";

    private final ConfigService configService;
    private final ApplicationEventPublisher publisher;
    // SafeConstructor：只解析为基本类型（Map/List/标量），杜绝 YAML 标签构造任意对象
    private final Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
    private volatile List<RouteDefinition> routes = List.of();

    public NacosRouteDefinitionRepository(NacosConfigManager manager,
                                          ApplicationEventPublisher publisher) throws NacosException {
        this.configService = manager.getConfigService();
        this.publisher = publisher;
        this.routes = parse(configService.getConfig(DATA_ID, GROUP, 5000));
        configService.addListener(DATA_ID, GROUP, new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                routes = parse(configInfo);
                publisher.publishEvent(new RefreshRoutesEvent(this));
            }
        });
    }

    private List<RouteDefinition> parse(String content) {
        if (content == null || content.isBlank()) return List.of();
        Object root = yaml.load(content);
        if (!(root instanceof Map<?, ?> rootMap)) return List.of();
        Object raw = rootMap.get("routes");
        if (!(raw instanceof List<?> rawList)) return List.of();
        List<RouteDefinition> result = new ArrayList<>();
        for (Object o : rawList) {
            if (!(o instanceof Map<?, ?> m)) continue;
            RouteDefinition rd = new RouteDefinition();
            rd.setId((String) m.get("id"));
            rd.setUri(URI.create((String) m.get("uri")));
            rd.setOrder(m.get("order") instanceof Integer i ? i : 0);
            if (m.get("predicates") instanceof List<?> ps) {
                ps.forEach(p -> rd.getPredicates().add(new PredicateDefinition((String) p)));
            }
            if (m.get("filters") instanceof List<?> fs) {
                fs.forEach(f -> rd.getFilters().add(new FilterDefinition((String) f)));
            }
            result.add(rd);
        }
        return result;
    }

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        return Flux.fromIterable(routes);
    }

    @Override
    public Mono<Void> save(Mono<RouteDefinition> route) { return Mono.empty(); }

    @Override
    public Mono<Void> delete(Mono<String> routeId) { return Mono.empty(); }
}
```

对应 dataId `api-gateway-routes.yml` 改为独立结构（去掉 `spring.cloud.gateway.routes` 前缀）：

```yaml
routes:
  - id: user-service
    uri: lb://user-service
    predicates:
      - Path=/api/users/**,/api/auth/**,/api/admin/**
    filters:
      - StripPrefix=1
    order: 0
  - id: product-search-service
    uri: lb://product-search-service
    predicates:
      - Path=/api/product-search/**
    filters:
      - StripPrefix=1
    order: 0
  # ... 其余路由同现有定义
```

优点：标准、稳妥，路由来源单一，天然支持网关 actuator 的增删查；不改动框架内部 bean。
缺点：需要调整 dataId 的结构（去掉 `spring.cloud.gateway.routes` 外层）。

## 方案 B：轻量监听器，直接改写 `GatewayProperties`

保留现有 `spring.cloud.gateway.routes` YAML 格式，新增一个组件：监听 `NacosConfigRefreshEvent`，命中路由 dataId 时，直接从 `ConfigService` 拉最新配置、解析后写回 `GatewayProperties` 单例，再发 `RefreshRoutesEvent`。

```java
@Component
public class GatewayPropertiesNacosRefresher {

    private static final String DATA_ID = "api-gateway-routes.yml";
    private static final String GROUP = "DEFAULT_GROUP";

    private final NacosConfigManager manager;
    private final GatewayProperties gatewayProperties;
    private final ApplicationEventPublisher publisher;
    // SafeConstructor：只解析为基本类型，杜绝 YAML 标签构造任意对象
    private final Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

    @EventListener(NacosConfigRefreshEvent.class)
    public void onNacosChange(NacosConfigRefreshEvent event) throws Exception {
        if (!DATA_ID.equals(event.getDataId())) {
            return; // 只处理路由 dataId，忽略 api-gateway.yml 等
        }
        String content = manager.getConfigService().getConfig(DATA_ID, GROUP, 5000);
        List<RouteDefinition> routes = parseRoutes(content); // 解析 spring.cloud.gateway.routes 子树
        gatewayProperties.setRoutes(routes);                  // 直接改写单例
        publisher.publishEvent(new RefreshRoutesEvent(this));
    }
}
```

要点：之所以能生效，是因为它**直接从 `ConfigService` 拉取最新配置**，不依赖已被证明失效的 Environment 刷新链路；`PropertiesRouteDefinitionLocator` 与 `gatewayProperties` 是同一单例引用，改写后 `RefreshRoutesEvent` 触发 `CachingRouteLocator` 重读即可。

优点：改动最小，保留现有 YAML 结构与 `spring.cloud.gateway.routes` 键，无重复路由风险。
缺点：直接改写框架 `@ConfigurationProperties` 单例，属偏 hack 的手法；`parseRoutes` 需解析 `spring.cloud.gateway.routes` 子树并映射为 `RouteDefinition`。

## 方案 C（外部参考）：`RouteDefinitionWriter` + InMemory 仓库（极客时间 PDF 方案）

> 来源：《微服务网关：如何借助 Nacos 实现动态路由规则？》（极客时间）。与方案 A 同属"绕开刷新链路、由 Nacos 显式驱动"一族，差别在于写入**内置 InMemory 仓库**而非自定义仓库，定位是把动态路由作为**静态路由之上的补充**。

做法要点：

- 路由以 **JSON 数组**存在独立 dataId（如 `routes-config.json`），内容直接是 `RouteDefinition` 列表（`predicates`/`filters` 用 `name`+`args` 对象）。
- `DynamicRoutesListener implements Listener`：`receiveConfigInfo` 里用 `JSON.parseArray(configInfo, RouteDefinition.class)` 反序列化。
- `GatewayService`：逐条 `routeDefinitionWriter.save(Mono.just(r)).subscribe()`，并逐条 `publishEvent(new RefreshRoutesEvent(...))`。
- `DynamicRoutesLoader implements InitializingBean`：启动拉一次配置并 `addListener` 注册监听。
- 写入目标是网关**默认的 `InMemoryRouteDefinitionRepository`**，因此 actuator 的 `/actuator/gateway/routes` CRUD 与之共用同一存储。

```java
@Service
public class GatewayService {
    @Autowired private RouteDefinitionWriter routeDefinitionWriter;
    @Autowired private ApplicationEventPublisher publisher;

    public void updateRoutes(List<RouteDefinition> routes) {
        if (CollectionUtils.isEmpty(routes)) { return; }
        routes.forEach(r -> {
            try {
                routeDefinitionWriter.save(Mono.just(r)).subscribe();
                publisher.publishEvent(new RefreshRoutesEvent(this));
            } catch (Exception e) {
                log.error("cannot update route, id={}", r.getId());
            }
        });
    }
}

@Component
public class DynamicRoutesListener implements Listener {
    @Autowired private GatewayService gatewayService;
    @Override public Executor getExecutor() { return null; }
    @Override public void receiveConfigInfo(String configInfo) {
        List<RouteDefinition> list = JSON.parseArray(configInfo, RouteDefinition.class);
        gatewayService.updateRoutes(list);
    }
}
```

对应 dataId（JSON，`predicates`/`filters` 为 name+args 对象）：

```json
[{
  "id": "customer-dynamic-router",
  "order": 0,
  "uri": "lb://coupon-customer-serv",
  "predicates": [{ "name": "Path", "args": { "pattern": "/dynamic-routes/**" } }],
  "filters":    [{ "name": "StripPrefix", "args": { "parts": 1 } }]
}]
```

优点：
- **代码量最少**：复用内置 `RouteDefinitionWriter`/InMemory 仓库，JSON 直接反序列化成 `RouteDefinition`，无需方案 A 的手工 `PredicateDefinition/FilterDefinition` 转换。
- 与 actuator CRUD 天然打通，可手动增删路由，是社区最经典配方。
- 同样**绕开坏掉的刷新链路**（显式 `addListener` + 手动发 `RefreshRoutesEvent`），在当前 SCA 版本下能生效。

缺点 / 风险：
- **删除不闭环（最大短板）**：`updateRoutes` 只 `save` 从不 `delete`，在 Nacos 删掉某条路由后，InMemory 里的旧路由会残留到重启；方案 A 每次返回全量，天然反映删除。
- **逐条 save + 逐条发 RefreshRoutesEvent**：N 条路由发 N 次刷新事件，且 `save(...).subscribe()` 是异步 fire-and-forget，事件可能先于 save 完成，存在轻微时序窗口。
- **依赖 fastjson**：文中为 fastjson1 风格；本项目用的是 fastjson2（`2.0.57`），需改用 `com.alibaba.fastjson2.JSON`，且 fastjson 反序列化外部配置本身是安全敏感点。
- 示例基于 bootstrap.yml，但本项目为 config-data 模式——路由机制不依赖 bootstrap/`refresh-enabled`，故不受影响，仅那些配置项对本项目无意义。

## 方案 D（官方链路桥接）：触发 `ContextRefresher.refresh()`

> 思路与 A/B/C 不同：不绕开官方刷新链路，而是**把官方链路重新点亮**。A/B/C 都是"Nacos → 自己解析 → 自己发 `RefreshRoutesEvent`"；D 是"Nacos → 触发一次完整 Spring 刷新 → 官方 `RouteRefreshListener` 自动发 `RefreshRoutesEvent`"。

原理（回顾"根因"一节）：属性路由要热更，需要 `GatewayProperties` 重绑（靠 `EnvironmentChangeEvent`）+ 缓存重载（靠 `RefreshRoutesRefreshedEvent`→`RefreshRoutesEvent`）。这两个事件会在 `ContextRefresher.refresh()` 执行时一起发出，且 `RouteRefreshListener` 本就监听 `RefreshScopeRefreshedEvent`。当前 SCA 版本缺的只是"谁来触发这次 refresh"——补上这个桥接即可。

```java
@Component
public class NacosRefreshBridge {

    private final ContextRefresher contextRefresher;

    public NacosRefreshBridge(ContextRefresher contextRefresher) {
        this.contextRefresher = contextRefresher;
    }

    @EventListener(NacosConfigRefreshEvent.class)
    public void onNacosChange(NacosConfigRefreshEvent event) {
        // refreshEnvironment() 重读配置并发 EnvironmentChangeEvent（重绑全部 @ConfigurationProperties，含 GatewayProperties）；
        // scope.refreshAll() 重建全部 @RefreshScope bean 并发 RefreshScopeRefreshedEvent，
        // 后者被官方 RouteRefreshListener 捕获 → 发 RefreshRoutesEvent → CachingRouteLocator 重载路由。
        contextRefresher.refresh();
    }
}
```

优点：
- **代码量最少（~15 行）**，不改配置结构，`spring.cloud.gateway.routes` 维持原样，无需自定义 Repository。
- 恢复的是**官方链路**：不仅路由，所有 `@RefreshScope` / `@ConfigurationProperties`（如 `gateway.security` 白名单）一并恢复热更。
- 语义最"正统"，与 `@RefreshScope` 配置刷新的体验一致。

缺点 / 风险：
- **全量刷新**：任意 Nacos 变更（不只路由，`api-gateway.yml` 等也算）都会触发一次整应用范围的配置重绑 + RefreshScope 重建，副作用面大（详见下文"关于全量刷新"）。
- **已实测：本版本下无法实现路由热更（不可用）**。详见下方"方案 D 实测结论"。
- 每次变更做全量刷新，频率高时开销与抖动大于定向方案。

### 方案 D 实测结论（本地已验证：**不可用**）

对"属性式路由 + 桥接"形态做了端到端实测，证据链如下：

1. 桥接生效：Nacos 变更 → 日志 `[方案D] 收到 Nacos 配置变更，触发 ContextRefresher.refresh()` → `refresh() 完成`。
2. Environment 已更新：日志 `Refresh keys changed: [spring.cloud.gateway.routes[10].id/uri/...]`，说明 `refreshEnvironment()` 确实重读了 Nacos 并识别出路由 key 变化，且发出了 `EnvironmentChangeEvent`。
3. **但路由未变**：`/actuator/gateway/routes`（读取的是 `RouteDefinitionLocator` 的路由定义，非缓存）仍返回旧路由；增、删路由都不生效。
4. **根因定位**：通过 `/actuator/configprops` 直接观察 `GatewayProperties` bean——变更后其 `routes` 数量**保持不变**（未随 Nacos 新值重绑）。即 **`ConfigurationPropertiesRebinder` 没有重新绑定 `GatewayProperties`**，`PropertiesRouteDefinitionLocator` 一直返回旧路由。

结论：方案 D 虽能触发完整刷新、Environment 也更新了，但**属性式路由的载体 `GatewayProperties` 在本版本下不会被重绑**，因此路由拿不到新值。**方案 D 在当前技术栈不可用**，保留方案 A（直读 Nacos、绕开该失效环节）。

## 方案对比

| 维度 | A：自定义 Repository | B：改写 GatewayProperties | C：PDF（Writer + InMemory） | D：官方链路桥接 |
|---|---|---|---|---|
| 路由存储 | 自定义仓库即唯一来源 | 仍用 `spring.cloud.gateway.routes` | 写进默认 InMemory 仓库 | 仍用 `spring.cloud.gateway.routes` |
| 定位 | Nacos 作为全部路由唯一来源 | 让既有属性路由可热更 | 动态路由作为补充，与静态并存 | 重新点亮官方刷新链路 |
| 配置格式 | YAML shortcut，需手工转 Predicate/Filter | 沿用现有 YAML | JSON，fastjson 直接映射 | 沿用现有 YAML，零改动 |
| 代码量 | 中（手工映射） | 中（解析子树） | 少（反序列化即可） | 最少（~15 行） |
| 删除路由 | 天然支持（每次返回全量） | 不涉及（属性整体替换） | **不处理**，旧路由残留 | 支持（`GatewayProperties` 整体重绑） |
| 刷新粒度 | 定向（仅路由） | 定向（仅路由） | 定向（仅路由） | **全量**（所有可刷新配置） |
| 附带收益 | 无 | 无 | actuator CRUD | 白名单等 `@RefreshScope` 一并热更 |
| 改动成本 | 中（迁移 dataId 结构） | 小 | 中（新增 JSON dataId） | 极小（加一个类） |
| 本版本是否实测 | **已实测可用** | 未实测 | 未实测 | **已实测不可用** |

结论：
- A/B/C 都是"绕开失效链路、自己驱动 `RefreshRoutesEvent`"；D 是"修复/点亮官方链路"。
- **全部路由由 Nacos 托管、增删改自动闭环** → 选 **A**（返回全量，天然处理删除）——当前已实现并实测。
- **在既有静态路由之上动态增改少量路由、并保留 actuator 管理** → 选 **C**，但必须补"删除/全量替换"逻辑。
- **最小 diff、不动配置结构** → 选 **B**。
- ~~追求最轻量、走官方链路~~ → 方案 **D** 已实测**不可用**（`GatewayProperties` 不被重绑），不作为候选。

**关于方案 D 与已实现方案 A 的取舍**：曾考虑用最轻量的 D（桥接 `ContextRefresher.refresh()`）替代 A，但实测证明 D 在本版本下路由无法热更——刷新能跑、Environment 也更新，唯独 `GatewayProperties` 不重绑。因此**维持方案 A**：定向、无全局副作用、且已实测可用。

**折中建议**：采用 C 的 JSON + fastjson2 + `RouteDefinitionWriter` 骨架，但把 `updateRoutes` 改为**先 diff/全量替换（删旧增新），最后只发一次 `RefreshRoutesEvent`**，同时吸收 C 的低代码量与 A 的删除闭环。

## 关于"全量刷新"

"全量刷新"特指 `ContextRefresher.refresh()` 这一次调用所做的事，它是**应用范围**的，而非只针对路由：

1. `refreshEnvironment()`：重新读取**所有**配置源（所有 import 的 Nacos/本地配置），计算变更的 key，发布 `EnvironmentChangeEvent`。
2. `ConfigurationPropertiesRebinder`（监听 `EnvironmentChangeEvent`）：重新绑定**全部** `@ConfigurationProperties` bean——不止路由，还包括数据源、Redis、JWT、Sentinel 等所有配置类。
3. `scope.refreshAll()`：销毁并重建**全部** `@RefreshScope` bean，并发布 `RefreshScopeRefreshedEvent`。

也就是说：**任意一个 Nacos 配置变更（哪怕只改了与路由无关的项），都会触发整应用的配置重绑 + RefreshScope 重建**，与本次变更是否相关无关——这就是"全量/全局"的含义，也是方案 D 的主要代价。

**方案 A 不需要、也不做全量刷新**：

- A 只对 `api-gateway-routes.yml` 这一个 dataId 注册了监听；
- 变更时仅重新解析路由列表，发布一个 `RefreshRoutesEvent`；
- 只有 `CachingRouteLocator` 响应（重载路由缓存，纯内存操作），不重读其他配置、不重绑其他 bean、不触碰 `@RefreshScope`。

因此 A 属于**定向/外科手术式刷新**，爆炸半径小、无全局副作用；D 属于**全量刷新**。二者是"精准小范围"与"省事但影响面大"的取舍。

## 附带清理项

- `api-gateway/src/main/resources/application.yml` 中 `api-gateway-routes.yml` 被**重复引入两次**（`spring.config.import` 与 `spring.cloud.nacos.config.extension-configs` 各一份）。无论选 A、B 还是 C，都建议只保留一处，避免重复加载/监听带来的歧义。
- 当前 `management.endpoints.web.exposure.include` 仅暴露 `health,info`。若要像文中那样用 `/actuator/gateway/routes` 验证，需临时把 `gateway` 端点加入暴露列表（或经网关内部路由放行），验证后按需收紧。

## 验证方式

1. 启动网关 + Nacos（`docker compose`）。
2. 修改 `api-gateway-routes.yml`（如改某路由 `Path` 或新增路由）。
3. 请求 `/actuator/gateway/routes`，确认列表实时反映新配置；并用实际请求验证转发命中新路由。
4. 回归既有路由（`/api/products/**`、`/api/product-search/**` 的顺序约束、`/api-docs/**` 聚合）不受影响。

## 风险与注意事项

- **YAML 安全解析**：路由配置来自 Nacos，解析时务必使用 `SafeConstructor`（只产出 Map/List/标量，禁止 YAML 标签实例化任意对象），并对解析出的结构做类型校验后再映射为 `RouteDefinition`，不要裸用 `new Yaml()` 加载外部配置。
- **JSON 反序列化（方案 C）**：用 fastjson 反序列化外部配置同样属安全敏感点；本项目应使用 fastjson2（`com.alibaba.fastjson2.JSON`），避免引入 fastjson1，且仅反序列化为 `RouteDefinition` 这类明确类型，不开启 autoType。
- 方案 A 必须同步移除/迁移 `spring.cloud.gateway.routes` 键，否则与仓库路由重复（相同 `id` 会冲突）。
- 路由顺序约束仍然有效：`/api/product-search/**`（order 0）必须先于 `/api/products/**`（order 1），迁移时保持 `order` 字段不变。
- `configService.addListener` 的回调在 Nacos 客户端线程执行，解析/发布事件应轻量，避免阻塞；必要时异步化，但要注意事件发布与路由列表可见性（`volatile` 已保证）。
- A/B/C 均绕过 Environment 刷新，因此**网关其余 `@ConfigurationProperties`（如 `gateway.security` 白名单）依旧不会随 Nacos 热更新**——如这些也需热更，需另行处理；而方案 D 走全量刷新，会连同这些配置一并刷新（这也是它的影响面所在）。
