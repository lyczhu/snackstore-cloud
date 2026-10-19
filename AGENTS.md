# AGENTS.md

Spring Boot 3 + Spring Cloud Alibaba microservices backend. Java 25, MyBatis-Plus, JJWT. Base package: `com.lawyus.snackstore`.

## Build

Use the Maven Wrapper — it pins the build to **Maven 3.9.11** via
`.mvn/wrapper/maven-wrapper.properties` (`distributionType=only-script`, `wrapperVersion=3.3.4`,
so there is no `maven-wrapper.jar` to commit; `.gitignore` still ignores that path).
`./mvnw` in bash/WSL/CI, `.\mvnw.cmd` in PowerShell. Plain `mvn` works too, but your local
version then decides the build.

```bash
./mvnw clean compile
./mvnw clean package -DskipTests
./mvnw clean package -DskipTests -pl <module> -am   # single module + deps
```

Tests: **160 unit tests across all 6 service modules + common** (JUnit 5 + Mockito, no Spring context, no MySQL/Redis/Nacos/ES needed). `./mvnw test` runs the whole reactor green in ~25s. No CI, no lint, no formatter — the green signal only exists when someone runs `./mvnw test` by hand.

Test coverage by module: `common` 23 (JwtUtil 签名/过期/密钥强度, Result/PageResult 契约, 搜索双通道 DTO 字段集比对), `api-gateway` 16 (`JwtAuthFilter` 信任边界, `ClientIpHeaderFilter` 伪造代理头), `user-service` 30 (注册/登录检查顺序, 默认地址不变量, 值对象校验), `product-service` 29 (库存原子扣减与事件, 排序白名单与降级口径, `ProductChangedListener` 消息组装/分区键/失败重试), `product-search-service` 32 (ES 查询构造/金额换算/降级, Kafka 消费者手工 ACK 与字段映射, DLT 必 ack, 全量重建的顺序/单实例/失败复位/页数上限), `order-service` 11 (下单金额、插入顺序、状态流转, `ProductClientFallbackFactory` 失败关闭), `statistics-service` 19 (缓存键隔离与 TTL 抖动, 聚合口径, 非 200 不写缓存, 三个 Feign 降级工厂失败关闭).

When adding tests in this repo:
- **Add the dependency first**: `spring-boot-starter-test` (scope `test`) is declared per-module — it exists in all 7 modules now, but a new module needs it explicitly (version comes from the parent BOM).
- **DB-backed tests can't use H2**: Flyway `V1__*.sql` scripts start with `CREATE DATABASE` + `USE snackstore_*`. Integration tests need a real MySQL (docker-compose) or Testcontainers.
- **Pure unit tests must initialize MyBatis-Plus metadata** before exercising `LambdaQueryWrapper`/`LambdaUpdateWrapper` code, otherwise column resolution throws `can not find lambda cache for this entity`. Copy the `@BeforeAll TableInfoHelper.initTableInfo(...)` block from `OrderServiceImplTest` or `ProductServiceImplStockTest`.
- **`AggregateRoot.getDomainEvents()` returns a live `unmodifiableList` view** — `clearDomainEvents()` empties any reference you captured. Assert published events with a `doAnswer` that snapshots the list at call time (see `UserAuthenticationDomainServiceTest.registerPersistsAsUserAndPublishesEvent`), not with `ArgumentCaptor`.
- **Gateway filters are `GlobalFilter`s**: test them with `MockServerWebExchange`/`MockServerHttpRequest` and a hand-written `RecordingChain` implementing `GatewayFilterChain` (not a Mockito mock) so injected/stripped headers are assertable.
- **Wrapper condition values are lazy**: `LambdaQueryWrapper.getSqlSegment()` materializes parameters only when the SQL fragment is generated. Call `getSqlSegment()` *before* asserting on `getParamNameValuePairs()`, or the map is empty.
- **The Kafka producer result type is Spring's**, `org.springframework.kafka.support.SendResult` — there is no `SendResult` in `kafka-clients`. `KafkaTemplate.send` returns `CompletableFuture<org.springframework.kafka.support.SendResult<K,V>>`.
- **Never build a stub inside another stubbing**: `when(x.send(...)).thenReturn(helper())` fails with `UnfinishedStubbingException` if `helper()` itself stubs a mock. Precompute the value into a field in `@BeforeEach` (or use `doReturn`) and pass the variable.
- **Most DTOs have no `equals`** (`ProductSearchItemDTO`, `Product`, …), so `verify(mock).method(List.of(dto))` silently never matches. Verify with `any()` and re-assert on captured *content* (ids/sizes) via `ArgumentCaptor`.
- **`PageResult` has exactly one factory**: `PageResult.success(List<T> data, Long pageNum, Long pageSize, Long total)` (boxed `Long` args, `pages` computed internally). There is no `PageResult.of(...)`.
- **`@Value` fields are not injected in plain unit tests**: set them with `ReflectionTestUtils.setField` (see `ProductSearchRebuildServiceTest.pageSize`, `ProductChangedListenerTest` topic/max-attempts), otherwise the field is `0`/`null`.

Run a single test: `./mvnw -pl <module> -am test -Dtest=OrderServiceImplTest#methodName`.

## Infrastructure

`docker-compose.yml` provides MySQL, Redis, Nacos, Kafka, Elasticsearch, Kibana, and all six services. Nacos must be running before services start locally.

## Modules & Ports

| Module | Port | Notes |
|---|---|---|
| `common` | — | Library: `Result`/`PageResult` wrappers, `JwtUtil`, Kafka message DTOs |
| `api-gateway` | 8080 | Reactive gateway: routing, JWT auth, CORS, Sentinel |
| `user-service` | 8081 | DDD layout (see below) |
| `product-service` | 8082 | Source of truth for products; publishes Kafka events |
| `order-service` | 8083 | Orders; Feign calls to product-service |
| `product-search-service` | 8085 | ES-backed search, no MySQL; consumes Kafka from product-service |
| `statistics-service` | 8087 | Dashboard aggregation; Feign fan-out on bounded `statisticsExecutor` pool, Redis cache (db 3) |

## Critical: Configuration lives in `nacos-config/`

Each module's `application.yml` only bootstraps Nacos. **Real config** (ports, routes, DB URLs, Sentinel rules, whitelists) is in `nacos-config/<service>.yml` plus a shared `nacos-config/application.yml`. Edit those files, not module `application.yml`. All six services use the `public` Nacos namespace (docker-compose forces it via `SPRING_CLOUD_NACOS_*_NAMESPACE`).

- The shared `nacos-config/application.yml` is imported by **5/6 services** (product, order, product-search, statistics, user — via `optional:nacos:application.yml`). **api-gateway does NOT import it** (intentional: it keeps its own restricted actuator exposure); its config lives in its own `api-gateway.yml`, which also optionally imports `api-gateway-<profile>.yml` (default profile `dev`; no local backup of that file).
- **Gateway routes are NOT in `api-gateway.yml`** — they live in `nacos-config/api-gateway-routes.yml`, loaded directly by `NacosRouteDefinitionRepository` (live-refresh, no restart). `api-gateway.yml` only holds ports, Sentinel datasources, security whitelist, and springdoc UI urls.
- Edits to `nacos-config/` only take effect at runtime via Nacos refresh — rebuild/restart alone won't apply them.

## Architecture gotchas

- **Gateway route order matters**: `/api/product-search/**` (order 0) must precede `/api/products/**` (order 1) in `nacos-config/api-gateway-routes.yml` (shortcut text format predicates/filters).
- **JWT flow**: Gateway `JwtAuthFilter` validates tokens and injects `X-User-Id`, `X-Username`, `X-User-Role` headers. Downstream services trust these headers — they do NOT re-validate JWT. Missing `X-User-Id` on a controller param maps to 403. Header names + admin role constant live in `common`'s `AuthConstants` — use those, not string literals; role comparison is case-insensitive.
- **Gateway admin enforcement**: `nacos-config/api-gateway.yml` `gateway.security` has `whitelist` (`"METHOD:PATH"`, e.g. `"POST:/api/auth/register"`) plus `admin-prefix-patterns` / `admin-only-patterns` (e.g. `POST:/api/products`, `GET:/api/statistics/**` require admin).
- **Feign, not RestTemplate/WebClient**: All sync inter-service calls use `@FeignClient` with `fallbackFactory`. No shared contract module — each caller defines its own client interfaces. **fallbackFactory 类必须标注 `@Component` 注册为 bean**：`feign.sentinel.enabled=true` 生效后 SentinelFeign 会从容器强制查找，缺失直接启动失败（`No fallbackFactory instance ... found for feign client`）。
- **Internal endpoints**: Services expose `*InternalController` classes for Feign callers.
- **Separate databases**: `snackstore_user`, `snackstore_product`, `snackstore_order` — each service owns its own MySQL database.
- **Flyway migrations**: `<service>/src/main/resources/db/migration/`. Never edit applied migrations; add new versioned files.
- **Kafka**: product-service publishes `ProductSearchSyncMessage` (defined in `common`) to `product-changed` topic; product-search-service consumes with manual ack + dead-letter consumer.
- **Search data contract — two channels, two DTOs**: (1) events `ProductSearchSyncMessage` (carries `eventId`/`eventTime`/`changeType` event metadata); (2) sync pull via `ProductDataClient` Feign — `GET /internal/products` (rebuild) and `POST /internal/products/search/fallback` (ES-down fallback) — which return **`ProductSearchItemDTO`** (shared in `common`, product-snapshot fields only, no event metadata). Do not mix the two: the internal API should not leak event metadata, and the consumer maps event → `ProductSearchItemDTO` before indexing.
- **`BusinessException` / `GlobalExceptionHandler` are NOT shared** — each service has its own copy. Every service's handler maps `BindException`/`MethodArgumentNotValidException` → 400, so DTO validation works.
- **DTO validation needs `@Valid`**: bean-validation annotations (`@Min`/`@Max`/…) on query DTOs only take effect when the controller method param has `@Valid` — easy to miss.
- **Gateway Sentinel rules**: `nacos-config/api-gateway.yml` defines two Nacos datasources — `flow` (rule-type `gw-flow`, data-id `api-gateway-flow-rules`) and `degrade` (rule-type `degrade`, data-id `api-gateway-degrade-rules`), both group `SENTINEL_GROUP`, namespace `public`. Rule JSON baselines live in `nacos-config/sentinel/` (flow: 5 routes, QPS; degrade: error-ratio + slow-RT per route). They are **backups only — must be created manually in the Nacos console**; routes without rules are unlimited. Local `sentinel-gateway-*.json` files in `api-gateway/src/main/resources/` were removed (never wired up).
- **statistics-service caching**: Redis (db 3) caches + in-memory single-flight (`synchronized` double-check + `KEY_LOCKS` map in `StatisticsServiceImpl.computeIfAbsent`) and ±10% TTL jitter (`StatisticsCacheConstants.jitteredTtlSeconds`). Preserve the double-checked locking / jitter when editing.
- **product-search-service ES index**: documents are written to a versioned index `product_v1` (`ElasticsearchIndexConfig.INDEX_NAME`) behind alias `product` (`ProductSearchDocument` uses `@Document(indexName = "product", createIndex = false)`). Startup now **fails fast** if index/mapping init fails (M10). If the document structure changes: bump `INDEX_NAME`, restart to recreate + set mapping; the app **does not auto-switch the alias** (logs a warn) — reindex and point alias `product` at the new index manually.

## Order domain facts

- Status: `0=PENDING`, `1=COMPLETED`, `2=CANCELLED`. All state transitions are **atomic conditional updates** (`UPDATE ... WHERE status=X` + affected-rows check), never select-then-update.
- `deleteOrder` only deletes CANCELLED orders; PENDING/COMPLETED are rejected (`ORDER_STATUS_ERROR`).
- Order numbers use MyBatis-Plus `IdWorker.getIdStr()` (collision-safe; do not replace with timestamp+random).
- Statistics only count COMPLETED (`status=1`) orders in raw SQL; `orderCount`/`todayOrderCount` exclude CANCELLED. Keep these filters when touching `OrderMapper` SQL.

## user-service uses DDD

Layered structure: `presentation` / `application` / `domain` / `infrastructure` (with `do_`, `mapper`, `repository` sub-packages). Other services use flat controller/service/mapper. See `user-service/docs/DDD_ARCHITECTURE.md` before restructuring.

## Docs & conventions

- **`docs/系统审查报告.md` is the authoritative audit** (HIGH H1-H9 / MEDIUM M1-M22 / LOW items, with line refs). **All MEDIUM items (M1-M22) are fixed** (as of 2026-08). **HIGH status**: H1-H5, H7, H9 verified fixed; H8.5 verified fixed (Nacos healthcheck → `/nacos/v3/admin/core/state/readiness`). H6's core fix (insert order first + IdWorker) done; its residual Feign-timeout leak is deferred — see `docs/库存补偿方案.md`. **All LOW items are done** (gateway whitelist moved to Nacos `gateway.security` with `GET:/api/products/**` + `GET:/api-docs/**` opened, Bearer case-insensitive, aggregated Swagger UI at 8080 via `/api-docs/{service}/**` routes, PageSpecification logs clamps, address limit 20 + atomic default-clear + default migration on delete, Password masked + 6-64 len/72-byte check, SMS code consumed after commit, logout endpoint, dead configs removed, explicit route order, `getProductListByIds` order-preserving, search defaults to `status=1` (ES + MySQL fallback), escapeKeyword removed, `firstValidationMessage` NPE guard, `spring.application.name` added, EXPOSE unified 8080) The doc's tables still list these as open — check current code before "fixing" anything.
- **`docs/库存补偿方案.md`**: approved design for the remaining H6 risk (Feign-timeout stock leak after deduct). Not implemented — product-service deduct/rollback are NOT idempotent yet (no `stock_deduction_log` table). Do not "fix" the leak without reading it.
- **`nacos-config/README.md`** explains the config setup; service `docs/` folders contain design notes (Chinese) — consult for context.
- Lombok everywhere (annotation processor in root pom).
- Wrap all API responses in `common`'s `Result` / `PageResult`.
- **Swagger**: each service exposes springdoc UI at `http://localhost:<port>/swagger-ui.html`; the gateway also ships `springdoc-openapi-starter-webflux-ui` and aggregates all services at `http://localhost:8080/swagger-ui.html` via `/api-docs/{service}/**` proxy routes (see `api-gateway-routes.yml`) + `springdoc.swagger-ui.urls` in `api-gateway.yml`. `/api-docs/**` is in the gateway whitelist.
