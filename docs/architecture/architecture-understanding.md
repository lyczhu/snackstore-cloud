# Snackstore Cloud 架构理解报告（当前态）

- **生成方式**：`explore` 路由 → `system-modeler`（场景技能）+ `c4model` / `graphviz`（基础技能）
- **范围**：当前态结构建模，不含风险评分与整改排期（那些分别属 `risk-quality-reviewer` / `evolution-planner`）
- **证据**：全部主张可回溯到代码 / 配置 / 迁移脚本 / docker-compose / AGENTS.md；置信度见 [`snackstore-cloud.evidence.md`](./snackstore-cloud.evidence.md)
- **阅读顺序**：本报告 → C4 四视图（[`snackstore-cloud.structurizr.dsl`](./snackstore-cloud.structurizr.dsl)：V1 上下文 / V2 服务拓扑 / V3 数据归属 / V4 信任边界）→ 下单流（[`order-checkout-flow.dot`](./order-checkout-flow.dot)）→ 状态机（[`order-status-lifecycle.dot`](./order-status-lifecycle.dot)）→ 依赖契约（[`integration-dependency.dot`](./integration-dependency.dot)）

---

## 1. 系统是什么

一个**零食电商（B2C）后端**，按业务能力拆成 6 个可独立部署的服务，每个服务拥有自己的数据库，通过「网关同步入口 + OpenFeign 服务间同步调用 + Kafka 异步事件」三种通道协作。没有前端代码，Swagger 是唯一的使用者界面。

**业务参与者只有两类**（证据：网关 `whitelist` 与 `admin-*` 模式、各控制器的 `X-User-Role` 断言）：

| 角色 | 能做什么 | 边界证据 |
|---|---|---|
| 顾客（USER） | 注册/登录/登出、收货地址、浏览与搜索商品、下单、支付（状态标记）、取消、删除已取消订单 | `api-gateway-routes.yml:14-41`、`OrderController.java:26-65` |
| 管理员（ADMIN） | 商品与分类增删改查、上下架、用户启禁用、统计看板、手动重建搜索索引 | `api-gateway.yml:58-74` + `ProductController.java:95-99` + `StatisticsController.java:63-67` |

**系统当前不具备的能力**（无实体/迁移/接口证据）：购物车、优惠券与促销、真实支付渠道与退款、物流与履约、多租户/店铺/组织隔离。这是「单体电商最小闭环」，不是完整电商平台。

---

## 2. 能力地图（L0）

| 业务能力 | 承载容器 | 数据落点 |
|---|---|---|
| 身份与访问（注册/登录/验证码/登出/账号状态） | user-service | snackstore_user（t_user）+ Redis db0（token/验证码/登录锁） |
| 收货地址 | user-service | snackstore_user（t_address，`is_default` 原子清默认） |
| 商品主数据与分类 | product-service | snackstore_product（t_product / t_product_category） |
| 库存（扣减/回滚） | product-service（唯一所有者） | snackstore_product.stock |
| 商品变更留痕（快照） | product-service | snackstore_product.t_product_snapshot（V2/V3 迁移） |
| 商品全文检索 | product-search-service | ES 索引 product_v1（别名 product） |
| 交易（下单/支付/取消/删除） | order-service | snackstore_order（t_order / t_order_item） |
| 经营统计（看板/趋势/TOP） | statistics-service | 无自有库，只读聚合 + Redis db3 缓存 |
| 流量入口与安全（认证/授权/限流降级/文档聚合） | api-gateway | 无库，配置在 Nacos |

---

## 3. 服务拓扑与关键设计取舍（L2）

**（1）单一入口 + 头信任模型。** 网关 `JwtAuthFilter`（order=-1）验证 JWT 后注入 `X-User-Id / X-Username / X-User-Role`，下游服务**不再验签**，直接把这些头当身份用（`OrderController.java:27`、`ProductController.java:45`）。因此「下游端口只绑 127.0.0.1」是这套模型的必要配套（`docker-compose.yml:174-175,208,242,270,301`），白名单路径还要剥离客户端伪造的身份头（`JwtAuthFilter.java:58-60,123-139`）。信任边界由此非常清晰：**网关 = 唯一的身份签发点**。

**（2）鉴权是双层的。** 网关用 `admin-prefix-patterns` / `admin-only-patterns` 做粗粒度拦截，服务内再用 `assertAdmin(role)` 二次断言（商品、统计）。这不是冗余——服务可被内网直连，第二层是纵深防御。

**（3）配置面完全外置。** 各模块 `application.yml` 只做 Nacos bootstrap，真实配置（端口、路由、DB URL、Sentinel 规则、白名单）在 `nacos-config/`；网关路由更进一步，由 `NacosRouteDefinitionRepository` 直接读取并监听，**改路由不需重启**（`api-gateway-routes.yml:1-12`）。这带来一个高频踩坑：改 `nacos-config/` 后重新编译或重启**不会**生效，必须走 Nacos 刷新。

**（4）路由顺序是硬约束。** `/api/product-search/**`（order 0）必须先于 `/api/products/**`（order 1），否则搜索前缀会被商品路由吃掉。这是配置层面的隐式契约，代码里没有守卫。

**（5）服务间只走 OpenFeign + fallbackFactory，且契约不共享。** 调用方各自定义客户端接口，被调方用 `*InternalController` 暴露内部面。注意一处**契约不对称**：product-search 与 statistics 走 `/internal/**`，而 order-service 走的是**公开面** `/products/stock/batch-deductions`（`ProductClient.java:28-32`）——即库存写操作同时存在于「对外公开路径」和「服务间调用路径」上，仅在网关侧靠 admin-only 拦外部调用，内部 Feign 调用完全绕过网关。

**（6）读写分域 + CQRS 式搜索侧。** product-service 是商品与库存的 source of truth；product-search-service 无 MySQL，只维护 ES 读模型，通过两条通道获取数据：Kafka 事件（增量）与 `/internal/products` 分页拉取（每日 3 点全量重建 + 手动重建）。事件与拉取用不同 DTO（`ProductSearchSyncMessage` 带事件元数据 / `ProductSearchItemDTO` 只有快照字段），职责分离干净。

**（7）统计服务是无状态聚合器。** 用有界线程池做 Feign 扇出（`statisticsExecutor`），Redis + 进程内 `synchronized` 双检单飞（`KEY_LOCKS`）避免缓存击穿，TTL ±10% 抖动避免雪崩（`StatisticsServiceImpl.java:159-178`）。它把三个服务的内部接口当成数据源，因此是**级联依赖最重的点**——任一上游不可用即整体报错（`extractData` 直接抛 `STATISTICS_SERVICE_ERROR`，无部分降级）。

---

## 4. 数据与状态所有权

| 数据 | 唯一所有者 | 生命周期状态 | 一致性 |
|---|---|---|---|
| 用户 | user-service | `status 0/1`、`role USER/ADMIN`、`deleted` 逻辑删除 | 禁用时同步吊销 Redis 会话（`UserApplicationServiceImpl.java:153-156`） |
| 收货地址 | user-service | `is_default`、`deleted` | 设默认前先单条条件 UPDATE 清掉旧默认（`AddressRepositoryImpl.clearDefaultsByUserId:85-90`），删除走 `@Transactional` |
| 商品 | product-service | `status 0/1`、`stock`、`deleted` | 库存条件更新 `WHERE stock >= qty`；变更写快照表 |
| 订单 | order-service | `status 0→1`、`0→2`、`2→删除` | **全部为原子条件更新 + 影响行数判断**，无 select-then-update |
| 订单项 | order-service | 无状态字段 | 下单时固化商品名/图/单价 → 订单价不受后续改价影响 |
| 搜索文档 | product-search-service | 无状态 | **最终一致**：事务提交后发事件、失败重试 5 次、最终失败仅告警、靠每日全量重建兜底 |
| 会话/验证码 | user-service（写）+ gateway（读） | `token:{userId}` 单会话覆盖 | 会话校验在 Redis 异常时 **fail-open**（`JwtAuthFilter.java:160-174`，已登记风险） |

**跨服务一致性模型**（本系统架构上最值得注意的一处）：下单在**一个本地事务**内完成订单落库 + 远程扣库存（`OrderServiceImpl.java:51-79`）。远程调用失败会回滚订单，但**远程超时（对方已成功）时订单回滚而库存不回滚**——库存扣减无幂等日志表，补偿方案已批准但未实施（`docs/库存补偿方案.md`）。取消订单则是同步反向调用 `batchRollbackStock`，失败即整个取消失败。也就是说：这是一个**用同步调用模拟分布式事务**的设计，一致性依赖「调用失败」而非「调用可能超时」这一假设。

---

## 5. 分层与代码组织

- **user-service 是唯一 DDD 四层结构**：`presentation`（4 个控制器）/ `application`（Command、AppService、ViewVO、Converter）/ `domain`（聚合根 `AggregateRoot`、值对象 `Password/Phone/UserRole/UserStatus`、领域服务、`port` 端口、`repository` 抽象）/ `infrastructure`（`do_` + mapper + repository 实现 + port 适配器）。六边形端口/适配器落地较实：`TokenPort` → `JwtTokenPort`、`SmsSender` → `ConsoleSmsSender`、`PasswordEncoder` → `BCryptPasswordEncoderAdapter`。
- **其余 5 个服务是扁平 controller/service/mapper/exception**。同一仓库内两种分层风格并存，是新人的主要认知负担来源，`AGENTS.md` 已显式声明该差异。
- **每个服务各自复制一份 `BusinessException` + `GlobalExceptionHandler`**（无共享），因此错误码语义按服务隔离，跨服务无统一异常契约——Feign 调用方只能靠 `Result.code == 200` 判断（`StatisticsServiceImpl.extractData`）。
- 统一响应封装（`Result` / `PageResult`）、`JwtUtil`、`AuthConstants`、事件与同步 DTO 全在 `common`，6/6 模块编译期依赖它。

---

## 6. 可部署单元与运行面（仅作边界说明，运行时详情属 `deployment-topology-analyzer`）

`docker-compose.yml` 提供 MySQL 9.5 / Redis 8.2 / Nacos 3.1.1（standalone + derby）/ Kafka 4.1（KRaft 单节点）/ ES 9.2.3（自定义镜像内置 IK）/ Kibana + 6 个应用容器。所有服务共用 `public` 命名空间；启动依赖以 `service_healthy` 串联，Nacos 就绪探针用 `/nacos/v3/admin/core/state/readiness`。ES 索引初始化失败会 **fail fast 阻断启动**（`ElasticsearchIndexConfig.java:33-40`）。

---

## 7. Agent 指令覆盖度（业务上下文）

`AGENTS.md` 属于**质量很高的一类**：不仅给出构建命令，还写清了配置外置陷阱、路由顺序硬约束、Feign fallbackFactory 必须 `@Component`、订单状态全为原子更新、统计只计 `status=1`、DTO 校验需 `@Valid`、ES 别名不自动切换、以及「`docs/系统审查报告.md` 才是权威、动手前先核对当前代码」。

**未覆盖的（改动这些路径时风险最高）**：无测试（`src/test` 全空，AGENTS.md 自己也说明无 CI/lint/formatter），因此**回归只能靠人工**；库存补偿方案「已批准未实施」但订单侧代码没有指向该文档的注释；业务规则（谁可以改库存、超时怎么补偿、订单能否部分取消）在指令中只以「已知风险」形式存在，没有形成可执行的变更守则。

---

## 8. 未知与待验证（不要当作事实使用）

1. **短信通道**：只有 `ConsoleSmsSender`，验证码实际如何送达用户 → unknown。
2. **Sentinel 规则是否已生效**：`nacos-config/sentinel/*.json` 是备份基线，需手工建到 Nacos；运行时是否已存在 → unknown（未建则限流/降级实际为空）。
3. **order-service 的 Redis**：依赖 + compose 环境变量存在，代码无使用、`order-service.yml` 无 redis 段 → 死配置还是待启用 → unknown。
4. **支付语义**：`payOrder` 仅状态 0→1 + `pay_time`，无支付渠道、无退款 → 「支付」是否等于真实收款 → unknown。
5. **ES 别名切换**：文档结构变更后需人工重建并切别名，应用只 warn 不自动切 → 生产操作手册缺失 → unknown。
6. **重复下单去重 / 幂等键**：`uk_order_no` 只保单号唯一，不保用户重复提交 → unknown。

---

## 9. 已完成的深入分析（本轮后续产物）

| 问题 | 产物 | 生成技能 |
|---|---|---|
| 改哪里的波及面最大 | [`dependency-impact-findings.md`](./dependency-impact-findings.md) + [`dependency-map.dot`](./dependency-map.dot) / [`impact-map.dot`](./impact-map.dot) / [`violations.csv`](./violations.csv) | `dependency-impact-analyzer` |
| 风险有哪些、优先级如何 | [`architecture-review.md`](./architecture-review.md) + [`risk-map.dot`](./risk-map.dot) | `risk-quality-reviewer` |
| 库存补偿怎么落地 | [`migration-roadmap.md`](./migration-roadmap.md) + [`decision-map.md`](./decision-map.md) + [`current-vs-target.dot`](./current-vs-target.dot) | `evolution-planner` |

**尚未覆盖的问题**（需起栈或额外证据）：下单/取消链路的详细时序（`flow-visualizer`）、容器网络与发布拓扑（`deployment-topology-analyzer`）、面向非技术干系人的版本（`architecture-communicator`）、本模型与代码的新鲜度校验（`architecture-health`）。

## 维护说明

- 图的可信来源是这些文本文件与证据表；渲染出的 SVG/预览是派生物，不要手改。
- ⚠️ `docs/` 当前未被 git 跟踪（`.gitignore:63`），本目录全部产物也不会进入提交——详见风险登记 R11。
- 触发重建：新增/删除服务、路由 order 变化、新增内部接口或 Feign 客户端、订单/商品状态语义变化、数据存储归属变化。
- 复现方式：用 Qoder 的 Structurizr DSL 预览打开 `.structurizr.dsl`（现包 4 个视图，预览面板可切换；单视图节点 ≤ 11、关系 ≤ 14，以免文字与边界重叠）；用 DOT 预览打开 `.dot`（DOT 为 Canvas 优先纵向 `rankdir=TB`，状态机为短链路故用 `LR`）。
