# Snackstore Cloud 架构模型 — 证据索引

配套文件：`snackstore-cloud.structurizr.dsl`（C4 四视图：V1 上下文 / V2 服务拓扑 / V3 数据归属 / V4 信任边界）、`order-checkout-flow.dot`（下单端到端）、`order-status-lifecycle.dot`（状态机）、`integration-dependency.dot`（依赖与契约）。
生成日期：2026-10-19。范围：**仅当前态（current）**，不含目标态与整改建议（后者属 `evolution-planner` / `risk-quality-reviewer`）。
置信度定义：**high** = 代码/配置/迁移脚本直接证据；**medium** = 多处间接证据一致；**low** = 仅由命名、目录结构或文档推断；**unknown** = 明确待验证。

## 节点

| ID | 标签 | 类型 | state | 置信度 | sourceRefs |
|---|---|---|---|---|---|
| actor.shopper | 顾客 | actor | current | high | `nacos-config/api-gateway.yml:48-54`（`GET:/api/products/**` 白名单 + 订单接口需 X-User-Id） |
| actor.operator | 管理员 | actor | current | high | `nacos-config/api-gateway.yml:58-74`、`product-service/.../ProductController.java:95-99`、`statistics-service/.../StatisticsController.java:63-67` |
| container.gateway | api-gateway :8080 | service | current | high | `api-gateway/src/main/java/.../gateway/`（filter/route/handler/config）、`nacos-config/api-gateway.yml:1-15` |
| container.user | user-service :8081 | service | current | high | `user-service/src/main/java/.../user/{presentation,application,domain,infrastructure}`、`nacos-config/user-service.yml` |
| container.product | product-service :8082 | service | current | high | `product-service/src/main/java/.../product/`（41 文件）、`nacos-config/product-service.yml:8-31` |
| container.order | order-service :8083 | service | current | high | `order-service/src/main/java/.../order/`（30 文件）、`nacos-config/order-service.yml:21-25` |
| container.search | product-search-service :8085 | service | current | high | `product-search-service/src/main/java/.../product/search/`、`nacos-config/product-search-service.yml:8-32` |
| container.statistics | statistics-service :8087 | service | current | high | `statistics-service/src/main/java/.../statistics/`（26 文件）、`nacos-config/statistics-service.yml:20-28` |
| data.user-db | snackstore_user | database | current | high | `user-service/src/main/resources/db/migration/V1__init_user_tables.sql`、`V2__rotate_admin_password.sql` |
| data.product-db | snackstore_product | database | current | high | `product-service/.../db/migration/V1__init_product_tables.sql`、`V2__add_product_snapshot_table.sql`、`V3__denormalize_snapshot_category_and_index.sql` |
| data.order-db | snackstore_order | database | current | high | `order-service/.../db/migration/V1__init_order_tables.sql:5-35` |
| data.redis | Redis db0 / db3 | database | current | high | `nacos-config/user-service.yml:19-26`（db0）、`product-service.yml:14-18`（db0）、`statistics-service.yml:22-26`（db3） |
| data.es | product_v1（别名 product） | database | current | high | `product-search-service/.../config/ElasticsearchIndexConfig.java:21-81` |
| mq.product-changed | Kafka topic product-changed | queue | current | high | `nacos-config/product-service.yml:29-30`、`nacos-config/product-search-service.yml:31-32` |
| ext.nacos | Nacos（注册 + 配置中心） | external-system | current | high | `docker-compose.yml:39-67`、`nacos-config/README.md`、`api-gateway/.../route/NacosRouteDefinitionRepository.java` |
| ext.sentinel-dashboard | Sentinel Dashboard | external-system | current | medium | `nacos-config/api-gateway.yml:17-19`（transport.dashboard，默认 127.0.0.1:8858，compose 未部署该容器） |
| ext.sms-provider | 短信通道 | external-system | **unknown** | low | 端口接口 `user-service/.../domain/user/port/SmsSender.java`；唯一实现 `infrastructure/port/ConsoleSmsSender.java`（打印日志），无供应商 SDK/HTTP 调用证据 |
| lib.common | common（共享库，非容器） | module | current | high | 6/6 模块 `pom.xml`（如 `order-service/pom.xml:18`）、`common/src/main/java/.../common/{response,util,constant,message,dto}` |

## 边

| ID | from → to | 类型 | sync | protocol | 置信度 | sourceRefs |
|---|---|---|---|---|---|---|
| edge.gateway.routes.user | gateway → user | routes-to | sync | HTTP lb:// | high | `nacos-config/api-gateway-routes.yml:14-20` |
| edge.gateway.routes.search | gateway → search | routes-to | sync | HTTP lb:// | high | `api-gateway-routes.yml:21-27`（order 0，先于 products） |
| edge.gateway.routes.product | gateway → product | routes-to | sync | HTTP lb:// | high | `api-gateway-routes.yml:28-34`（order 1） |
| edge.gateway.routes.order | gateway → order | routes-to | sync | HTTP lb:// | high | `api-gateway-routes.yml:35-41`（order 2） |
| edge.gateway.routes.stats | gateway → statistics | routes-to | sync | HTTP lb:// | high | `api-gateway-routes.yml:42-48`（order 3） |
| edge.gateway.session-check | gateway → redis | reads | sync | Redis | high | `api-gateway/.../filter/JwtAuthFilter.java:160-174`（异常时 fail-open 放行） |
| edge.gateway.identity-inject | gateway → 下游服务 | validates | sync | HTTP header | high | `JwtAuthFilter.java:82-87`（注入 X-User-Id/Username/Role）、`:123-139`（白名单剥离伪造头）、`common/.../constant/AuthConstants.java` |
| edge.order.product | order → product | calls | sync | OpenFeign HTTP | high | `order-service/.../feign/product/ProductClient.java:13-32`、`OrderServiceImpl.java:72,88,206`；降级 `ProductClientFallbackFactory.java` |
| edge.search.product | search → product | calls | sync | OpenFeign HTTP | high | `product-search-service/.../client/ProductDataClient.java:13-21` → `product-service/.../controller/ProductInternalController.java:32-41` |
| edge.stats.order | statistics → order | calls | sync | OpenFeign HTTP | high | `statistics-service/.../client/order/OrderClient.java` → `order-service/.../controller/OrderInternalController.java:29-44` |
| edge.stats.product | statistics → product | calls | sync | OpenFeign HTTP | high | `statistics-service/.../client/product/ProductClient.java` → `ProductInternalController.java:43-56` |
| edge.stats.user | statistics → user | calls | sync | OpenFeign HTTP | high | `statistics-service/.../client/user/UserClient.java:7-11` → `user-service/.../presentation/controller/UserInternalController.java:19-22` |
| edge.product.publish | product → kafka | publishes | async | Kafka | high | `product-service/.../listener/ProductChangedListener.java:59-119`（AFTER_COMMIT + 重试 5 次 + 最终失败仅告警） |
| edge.search.consume | search → kafka | subscribes | async | Kafka | high | `product-search-service/.../listener/ProductSearchSyncConsumer.java:26-61`（MANUAL_IMMEDIATE ack，`nacos-config/product-search-service.yml:29-30`）、`ProductSearchSyncDltConsumer.java` |
| edge.search.es | search → es | reads/writes | sync | ES API | high | `ProductSearchServiceImpl.java:56-115,97-98`、`ProductSearchIndexServiceImpl.java`、`ElasticsearchIndexConfig.java:42-81` |
| edge.search.rebuild | search → product | batch calls | batch | OpenFeign HTTP | high | `ProductSearchRebuildService.java:43-77`（`@Scheduled cron=0 0 3 * * *`）+ `ProductSearchController.java:33-37`（手动重建，网关 admin-only） |
| edge.search.es-fallback | search → product | calls (降级) | sync | OpenFeign HTTP | high | `ProductSearchServiceImpl.java:57-60,117-128`（`@SentinelResource(fallback=…)` → `/internal/products/search/fallback` → `ProductServiceImpl.searchByKeyword:102-116`） |
| edge.stats.cache | statistics → redis(db3) | reads/writes | sync | Redis | high | `StatisticsServiceImpl.java:159-178`（double-check + `KEY_LOCKS` 单飞）、`cache/{DashboardCacheService,StatisticsCacheService}.java`、`constant/StatisticsCacheConstants.java`（jitteredTtlSeconds） |
| edge.product.cache | product → redis(db0) | reads/writes | sync | Redis Spring Cache | high | `ProductCategoryServiceImpl.java:40,50,61,78,100`（`@Cacheable`/`@CacheEvict`，仅分类）、`config/RedisCacheConfig.java:23-58`（异常降级直查 DB） |
| edge.user.session | user → redis(db0) | writes | sync | Redis | high | `user-service/.../infrastructure/port/JwtTokenPort.java:26-34`（`token:{userId}` set/delete） |
| edge.user.verification | user → redis(db0) | reads/writes | sync | Redis | high | `infrastructure/port/RedisVerificationCodePort.java`、`RedisLoginLockPort.java`、`application/service/impl/UserApplicationServiceImpl.java:60-74,86-92` |
| edge.user.sms | user → sms-provider | calls | sync | **unknown** | low | `UserApplicationServiceImpl.java:71`（`smsSender.send`）；实现 `ConsoleSmsSender.java` 仅日志 |
| edge.*.nacos | 全部服务 → nacos | depends-on (注册/配置) | sync | Nacos HTTP | high | `docker-compose.yml:146-155` 等环境变量、各模块 `src/main/resources/application.yml`（仅 bootstrap）、`AGENTS.md`（配置位于 `nacos-config/`） |
| edge.*.common | 全部模块 → common | depends-on (编译期) | — | in-process | high | 各模块 `pom.xml`（`api-gateway/pom.xml:65`、`user-service/pom.xml:18` 等） |

## 关键业务事实（结构与行为分离）

| 主张 | 类型 | 置信度 | sourceRefs |
|---|---|---|---|
| 订单状态 0=PENDING / 1=COMPLETED / 2=CANCELLED | 结构（字段+注释） | high | `V1__init_order_tables.sql:10`、`order-service/.../constant/OrderStatusConstants.java` |
| 状态流转为原子条件更新（UPDATE … WHERE status=X + 行数判断） | **行为** | high | `OrderServiceImpl.java:166-181`（支付）、`:194-201`（取消）、`:216-219`（删除仅 status=2） |
| 订单项保存下单时商品名/图/单价（价格快照） | 结构+行为 | high | `V1__init_order_tables.sql:24-35`、`OrderServiceImpl.java:104-113`（价格取自 product-service 返回值而非请求体） |
| 商品变更写入 `t_product_snapshot` | 行为 | high | `ProductServiceImpl.java:163,176,215,227` + `V2__add_product_snapshot_table.sql`、`ProductSnapshotServiceImpl.java` |
| 库存扣减采用 `stock >= qty` 条件更新（并发保护） | 行为 | high | `ProductServiceImpl.java:237-246,296-308` |
| 下单扣减与订单落库在同一本地事务 | 行为 | high | `OrderServiceImpl.java:51`（`@Transactional`）+ `:67-76` |
| 扣减调用失败会回滚本地事务 | 行为 | high | `OrderServiceImpl.java:73-76`（抛异常） |
| **Feign 超时但对方已扣减 → 库存泄漏** | **unknown/已知遗留** | medium（文档 + 无幂等表） | `AGENTS.md`（H6 残留）、`docs/库存补偿方案.md`；证据缺口：无 `stock_deduction_log` 表、product 侧扣减无幂等键 |
| 统计只计 COMPLETED 订单 | 行为 | high | `order-service/.../mapper/OrderMapper.java:17-28`（`status = 1 AND deleted = 0`） |
| 搜索默认只返回上架商品 | 行为 | high | `ProductSearchServiceImpl.java:160-163`、`ProductServiceImpl.java:107`（MySQL 降级同样默认 status=1） |
| 商品/分类变更事件为最终一致（ES 允许滞后） | 行为 | high | `ProductChangedListener.java:59`（AFTER_COMMIT）+ `ProductSearchRebuildService.java:43`（每日 3 点全量重建兜底） |

## 明确的未知项（建议后续验证）

1. **短信投递**：`SmsSender` 只有 `ConsoleSmsSender` 实现 → 生产验证码如何送达用户未知。验证方式：查是否有其他 profile 的实现或外部网关配置。
2. **Sentinel 规则是否已建**：`nacos-config/sentinel/*.json` 为基线备份，需手工创建到 Nacos；运行时是否已存在未知（需查 Nacos 控制台）。
3. **order-service 的 Redis**：`order-service/pom.xml:42` 有 redis starter，`docker-compose.yml:283-284` 注入 Redis 环境变量，但 `nacos-config/order-service.yml` 无 redis 配置块且代码中未见使用 → 死配置还是待启用未知。
4. **支付/退款**：`payOrder` 只把状态从 0 改 1 并写 `pay_time`，无支付渠道调用、无退款路径 → 「支付」是业务上真实支付还是标记动作未知（代码证据仅支持后者：`OrderServiceImpl.java:165-182`）。
5. **购物车/优惠券/物流**：无任何实体、迁移或接口证据 → 系统当前不具备这些能力（缺失即结论，非待验证）。
6. **多实例幂等与并发**：`IdWorker.getIdStr()` 保证单号唯一（`uk_order_no`），但同一用户重复提交订单的去重机制未见证据。
