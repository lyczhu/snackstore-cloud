# snackstore-cloud 架构风险与质量评审 — risk-quality-reviewer

- **评审目标**：判断当前架构是否足以支撑「继续加业务能力 + AI 代理安全改动」，并给出可执行的优先级
- **评审基线**：`docs/系统审查报告.md`（2026-08-01，H1–H9 / M1–M22）+ **逐条对活当前代码**（AGENTS.md 明确要求：文档表格仍把已修项列为 open，动手前必须核对代码）
- **风险容忍度假设**：MVP 阶段，可接受功能取舍，不可接受静默数据错误与安全面暴露
- **图**：[`risk-map.dot`](./risk-map.dot)；依赖侧证据见 [`dependency-impact-findings.md`](./dependency-impact-findings.md)

---

## 1. 先说结论：审查报告的 30 项已基本修完，别再照单重做

我按报告条目逐个回代码验证，**已确认修复**的（附证据）：

| 原条目 | 现在的代码事实 | 证据 |
|---|---|---|
| H1 Kafka 链路失效 | 生产端 `JsonSerializer`，消费端 `ErrorHandlingDeserializer` + `JsonDeserializer` + trusted.packages | `nacos-config/product-service.yml:27-28`、`product-search-service.yml:19-24` |
| H2 授权缺失 | `AuthConstants` + `equalsIgnoreCase`；网关 6 条 admin-only 商品/库存模式 + 服务内 `assertAdmin` 双层 | `JwtAuthFilter.java:156-158`、`api-gateway.yml:61-74`、`ProductController.java:95-99`、`StatisticsController.java:63-67` |
| H3 身份头可缺省 | `@RequestHeader(AuthConstants.HEADER_USER_ID)` 默认 required=true，缺失即 403 | `OrderController.java:27,34,40`、`AddressController.java:27-69`（6 处） |
| H4 JWT 密钥硬编码 | `JwtUtil(secret, expiration)` 构造注入 + 缺失/短密钥 fail-fast；`jwt.secret: ${JWT_SECRET}` 无默认值 | `common/.../JwtUtil.java:26-36`、`api-gateway.yml:91-93` |
| H5 金额不乘数量 | `price.multiply(BigDecimal.valueOf(quantity))` 累加 | `OrderServiceImpl.java:56-58` |
| H6 主体 | 先插订单（PENDING）再扣库存；单号 `IdWorker.getIdStr()` | `OrderServiceImpl.java:51-76` |
| H7 check-then-act | 支付/取消/删除全为 `UPDATE ... WHERE status=X` + 影响行数判断 | `OrderServiceImpl.java:166-171,194-201,216-219` |
| H8.1 Sentinel 前缀 | 前缀 `spring.cloud.sentinel`，namespace `public`，rule-type `gw-flow`/`degrade` | `api-gateway.yml:16-38` |
| H8.2 feign.sentinel 未生效 | 5 个服务 `spring.config.import: optional:nacos:application.yml`（内含 `feign.sentinel.enabled: true`）；4 个 fallbackFactory 全部 `@Component` | 各模块 `application.yml:7-9`、`nacos-config/application.yml:35-37` |
| H8.3 命名空间矛盾 | compose 对六个服务统一强制 `public` | `docker-compose.yml:152-155` 等 |
| H8.5 Nacos 探活 | 改用 `/nacos/v3/admin/core/state/readiness` | `docker-compose.yml:61` |
| H9.1 验证码无发放 | `tryAcquireSend`（手机号 + IP 双限流）→ `save(code,5min)` → `send` → `markSendSuccess`；提交后才消费 | `UserApplicationServiceImpl.java:60-74,86-92` |
| H9.2 弱口令管理员 | V2 迁移轮换密码 + `AdminPasswordInitializer` | `user-service/.../db/migration/V2__rotate_admin_password.sql` |
| H9.3 actuator 泄密钥 | 暴露收敛为 `health,info,refresh,gateway`，`show-details: never`，gateway 只读 | `api-gateway.yml:100-107`、`nacos-config/application.yml:31-33` |
| M1/M6/M7/M8/M14/M15/M17/M19/M20/M21 | 数量校验 / 分页 1–100 / `@TableLogic` / DLT 带 `Acknowledgment` 且 ack / `pageSize<=0` 保护 / 回滚结果检查并回滚事务 / SQL 带 `status=1 AND deleted=0` / 有界队列 10 + CallerRunsPolicy / 多态类型白名单 / IdWorker | 见各文件对应行（`ProductServiceImpl.java:264-268`、`ProductQueryDTO.java:25-30`、`Product.java:34-35`、`ProductSearchSyncDltConsumer.java`、`PageResult.java:126`、`OrderServiceImpl.java:207-210`、`OrderMapper.java:17-28`、`ThreadPoolConfig.java:16-29`、`RedisConfig.java:27-34`） |

**这条本身就是评审结论**：报告的「表格状态」已不可信，继续按它逐项「修复」会做大量重复劳动。真正剩下的风险是下面这些**报告里没有或只登记了一半**的。

---

## 2. 风险登记表（按优先级排序）

评分口径：**影响**（业务/数据后果严重度）、**可能性**（当前触发概率）、**置信度**（证据强度）。归属为假设，仓库无 OWNERS 文件。

### P0 — 会放大其它一切风险的元风险

**R1 零自动化验证 + 无 CI（历史定级 P0，2026-10-19 复核后 P1）｜影响 High｜可能性 Certain｜置信度 High**
`git ls-files "*.java"` 下 193 个 main 源文件、**0 个测试文件**（`src/test` 全空），无 `.github/`、无 lint、无 formatter（AGENTS.md 自述）。
> ← 以上为**评审当时的基线陈述**，已被下方「2026-10-19 复核更新」修正（现：201 个 main 源文件 / 25 个测试类 194 例 / 有 CI），保留作时间线。
> 2026-10-19：order-service 已有 8 个测试，但上述四条缺失依旧成立（无 CI / 无 lint），所以本项仍定 P0。
- **为什么排第一**：上表 30 项修复**全部没有回归防线**。契约漂移（R6）、路由顺序（R7）、状态字面量（R8）这三类的共同特征是「编译通过、启动正常、只有跑业务才暴露」，恰好是自动化测试唯一能廉价捕获的类别。
- **验收标准**：至少 3 条特征化测试（characterization test）——① 下单返回 200 且 `total_amount = Σ(price×qty)`；② 取消后 `t_product.stock` 回到原值且订单为 2；③ `GET /api/statistics/dashboard` 在 order 服务返回 `code!=200` 时抛统计异常而非 500。CI 上 `mvn -q test` 必须为绿。

> **2026-10-19 进展（S0 已推平全项目）**：已有 **160 个纯单元测试，覆盖 6 个服务 + common**（`mvn test` 全 reactor 绿，~25 秒）：common 23 / api-gateway 16 / user-service 30 / product-service 29 / product-search-service 32 / order-service 11 / statistics-service 19。除主链路外，本轮把**消息链路**也纳入防线：消费端索引写失败必须重抛且不 ack、DLT 必 ack、发布端失败重试不抛回业务线程、全量重建的标志位复位与页数上限。**仍未覆盖**：controller / `GlobalExceptionHandler`、`NacosRouteDefinitionRepository`、`ProductSearchIndexServiceImpl` 之外的基础设施依赖层、跳库与端到端（S0-b）、以及跨服务契约漂移。
> 但本项**仍为 P0 未关账**：① **仍无 CI**，绿灯全靠人工跑 `mvn test`；② 全部是**不依赖基础设施的单元层**，原本验收标准里的「取消后 `t_product.stock` 回原值」与「统计只计 `status=1` 的 SQL 口径」**仍需真库集成测试**（Flyway 脚本含 `CREATE DATABASE`/`USE`，H2 不可用）；③ 无跨服务契约测试（R6 的 ProductVO 漂移仍靠人肉守）。
>
> **2026-10-19 复核更新（`architecture-health`，HEAD `159b3d4`）——R1 由 P0 降为 P1**：① **CI 已落地**（`.github/workflows/maven.yml`：`mvn -B -ntp test` + `mvn -B -ntp package -DskipTests`，JDK 25 zulu，push/PR to `main` + 手动）；② 用例数由 160 → **194**（25 个测试类，`./mvnw -B -ntp test` 全 reactor 绿）；③ **仍无 lint / formatter**；④ 以下两项实质缺口不变：**无集成层、无跨服务契约测试**（S0-b 未做）。因此「零自动化验证 + 无 CI」已失效，剩余风险是「**自动化只覆盖单元层**」，不再具备 P0 的「完全无防线」性质。

### P1 — 立即处理

**R2 网关运维端点匿名可达**｜影响 Medium（本地使用下由 High 下调）｜可能性 Low（仅局域网）｜置信度 已静态定案
`api-gateway` 是六个业务容器里唯一不带 `127.0.0.1` 前缀发布的端口（`docker-compose.yml:144` `"8080:8080"`；下游 user/product/order/search/statistics 均刻意绑 `127.0.0.1:`，见 `174-175,208,242,270,301`），而暴露的 `management.endpoints` 含 `refresh` 与 `gateway`（`api-gateway.yml:100-107`）。

**2026-10-19 新增证据（已实测核对，静态充分）：**
1. `api-gateway/pom.xml` 全部 14 个依赖中**没有 `spring-boot-starter-security`** → 根本不存在 Security 过滤器链，actuator 不可能被 `authorizeExchange` 保护。
2. 全模块扫描 `WebFilter|SecurityWebFilterChain|authorizeExchange|SecurityFilterChain|HandlerInterceptor` → **唯一命中是 `CorsConfig.java` 的 CORS WebFilter**（非鉴权）。
3. 三个过滤器（`AccessLogFilter`/`ClientIpHeaderFilter`/`JwtAuthFilter`）**均为 `GlobalFilter`**，只作用于匹配路由的请求；`/actuator/**` 由 actuator 自己的 handler mapping 处理，不走网关过滤器链。
→ 结论：匿名读 `/actuator/gateway/routes`、匿名 POST `/actuator/refresh` **成立**。缓解因素：`management.endpoint.gateway.access: read-only` 使网关端点只读（不能增删路由），`show-details: never` 已限制 health 内容泄露。
- 后果：`GET /actuator/gateway/routes` 泄露内部服务清单、路径前缀、StripPrefix 与路由 order（等于公开「哪些路径绕过鉴权」的地图）；`POST /actuator/refresh` 可被反复触发配置刷新。
- **运行时旁证命令（必须在你的环境跑，见下方「为何本地跑会假阴性」）**：对比两条请求的状态码即可定案。
- 注：审查报告 H9.3 只处理了 `env` 泄露与 `show-details`，未覆盖「网关端口对外 + GlobalFilter 不覆盖 actuator」这一层。
- **2026-10-19 定级修正（用户确认仅本地使用 + 单实例）**：暴露面从「公网/内网」降为「同一局域网内其他设备」——但**不等于只有本机能访问**：`"8080:8080"` 在 Docker 下绑的是 `0.0.0.0`，而其余六个业务容器刻意绑 `127.0.0.1:` 只剩本机可达。后果是：**未授权可访问的运维端点恰好集中在网关这一个最敏感的入口**。办公网建议收口（compose 改 `"127.0.0.1:8080:8080"`，一行改动、零功能影响）；家用网络可当取舍。
> **2026-10-19 拍板（D9）**：仅本机使用再次确认 → 端口收口降为**可选加固**，不阻塞其它改进；actuator 暴露收窄（计划 A2）仍保留在 A 阶段，尚未实施。

**R3 配置面可被匿名改写｜影响 Critical（非本地场景）｜可能性 零（当前单机使用）｜置信度 High**
> **2026-10-19 定级修正**：用户确认仅本地使用 → 从「P1 立即处理」**下调为已登记的本地取舍**（与网关 Redis fail-open 同级）。风险本身未消失，只是被使用场景限制住了。
`NACOS_AUTH_ENABLE: false`（`docker-compose.yml:54`）且 `8848/9848/9849/8090→8080` 四个端口全部发布。由于 `nacos-config/api-gateway-routes.yml` 由 `NacosRouteDefinitionRepository` **实时热刷新**，能访问 Nacos 者即可改写路由把 `/api/products/**` 指向自建服务，并配合下游「信任 `X-User-Id` 不验签」的模型伪造身份。
- 这是**设计取舍而非缺陷**：本地开发关掉鉴权合理。风险在于该 compose 文件被用于任何非本机隔离场景。
- **失效条件（必须盯住）**：一旦该 compose 被复用到共享/演示/外网环境，R3 立即回到 Critical，且比 R2 严重得多——R2 只泄露拓扑，**R3 可直接接管流量与身份**。
- **低成本防护（现在就加不影响开发体验）**：把 Nacos/MySQL/Redis/Kafka/ES 的端口发布也改绑 `127.0.0.1:`（与下游服务保持同一约定），并在 compose 顶部写明「仅限单机开发，禁止直接用于共享环境」；非本地环境再叠加 `NACOS_AUTH_ENABLE=true` + `NACOS_PASSWORD` 从密钥库注入。
> **2026-10-19 拍板（D9）**：仅本机 → 维持已登记取舍，端口收口不阻塞其它改进；**失效条件不变**：该 compose 一旦进入共享/演示环境即回到 Critical，端口收口立即升为必须。

**R4 注册链路在生产不可用（业务能力缺口，非代码缺陷）｜影响 High｜可能性 Certain｜置信度 High**
唯一 `SmsSender` 实现是 `ConsoleSmsSender`，注释写明「开发环境短信通道：验证码打印到日志；生产环境请实现真实服务商替换该 Bean」（`ConsoleSmsSender.java:9-20`）。因此 H9.1 修复的只是**发放与限流机制**，用户实际收不到验证码 → `POST /api/auth/register` 走不通。
- 影响面：新用户获取只能靠「运维从日志读码」或直接用种子管理员。
- **验收标准**：要么接入真实通道并加 `@Profile("!dev")` 强制校验存在性实现，要么在产品层面明确「注册仅内测」并把注册入口对外关闭。二者必居其一，不能停在 unknown。
> **2026-10-19 拍板（D7）**：暂无真实通道 → **走内测分支**：撤下 `register` 与 `sms-code` 白名单（无 token → 401），保留 `login`/`admin-login`；另需定开号路径（`AdminUserController` 无建号接口，见 D-Open-5）。「接真实通道」分支保留为失效条件，不是否定。
> **2026-10-19 已实施**：两项白名单已撤下；开号路径已补为 `POST /api/admin/users`（admin-only、BCrypt、角色缺省 USER），D-Open-5 关闭。回归：203 例全绿。

**R5 库存扣减的超时窗口无兜底（H6 残余）｜影响 Medium-High｜可能性 Low｜置信度 High**
`OrderServiceImpl.createOrder`（`:51-79`）在本地事务内远程扣库存；超时异常 → 本地回滚，但 product 侧可能已提交。`product-service` 迁移仅 V1–V3，**无 `stock_deduction_log`、扣减无 `order_no` 幂等约束** → 补偿动作本身也会重复生效。方案 C 已批准未实施（`docs/库存补偿方案.md`，状态「方案讨论稿，未实施」）。
- **落地路径与切片**：见 [`migration-roadmap.md`](./migration-roadmap.md)。

**R6 契约靠同名片段复制，已实际漂移｜影响 High｜可能性 Medium｜置信度 High**
`ProductVO`：product 侧 12 字段、order 侧副本 6 字段（193 源文件中 13 组跨模块同名类）。`OrderServiceImpl:94-98` 以 `status == null` 判定下架 → **改字段名 = 全站下单失败（fail-closed）**，且编译与启动毫无异常。product 侧还挂着两个从未赋值的 `highlightedName/highlightedDescription`（搜索关注点泄漏进主数据 VO）。
- **验收标准**：一条契约快照测试（product 响应 JSON 的字段集合断言）+ 决定是「抽 `api-contract` 共享模块」还是「保留自建契约但加测试」，并把结论写进 AGENTS.md。

**R11 权威知识资产不在版本控制内｜影响 Medium-High｜可能性 Certain｜置信度 High**
`git ls-files docs/` 返回**空**；`git status --short` 显示 `?? AGENTS.md`；`.gitignore:63` 新增了 `docs/`。而 AGENTS.md 把 `docs/系统审查报告.md` 称为权威审查、把 `docs/库存补偿方案.md` 列为「不读它就不要碰库存泄漏修复」。**结论：系统当前的决策依据对 clone/CI/评审流程不可见**，也解释了为什么 AGENTS.md 与代码之间会出现「文档状态过期」（见 §1）。
- **验收标准**：`docs/` 与 `AGENTS.md` 纳入跟踪；`.gitignore` 只忽略工具产物目录（`.qoder/`、`.specify/`）。

### P2 — 计划内处理

**R7 路由顺序与鉴权模式靠人肉同步｜影响 Medium｜可能性 Medium｜置信度 High**
`/api/product-search/**` order 0 必须早于 `/api/products/**` order 1（`api-gateway-routes.yml:21-34`），写反即搜索被商品路由吞掉，**无启动校验**（历史上还因 `/**`+StripPrefix=2 形成过越权开放代理，见同文件 `49-51` 注释）。
**R8 业务语义用字面量跨层耦合｜影响 Medium（数据失真）｜可能性 Medium｜置信度 High**
`OrderMapper` 两段 SQL 硬编码 `status = 1 AND deleted = 0`（`:17-28`）与 `OrderStatusConstants` 无引用关系；新增「已完成族」状态会静默漏算，看板与明细对不上账。
**R9 product-service 是 fanIn=3 的运行时单点，且统计无部分降级｜影响 Medium｜可能性 Medium｜置信度 High**
`statistics` 三个 Feign 客户端都有 fallbackFactory，但 `extractData`（`:213-222`）对非 200 一律抛错 → 任一上游异常即整块看板失败；`search` 的 MySQL 降级同样回落到 product，product 挂则搜索、下单、统计同时受损。
**R10 搜索索引重建与版本兼容｜影响 Medium｜可能性 未知｜置信度 Medium**
`ProductSearchRebuildService.rebuildAll`（`:53-86`）**先 `deleteQuery(Query.findAll())` 清空再分页重灌**，中途失败即留下空/半索引（异常向上抛，定时任务仅记日志）；`rebuilding` 是单实例 `AtomicBoolean`，多实例并发重建会互不感知；别名不自动切换只 `warn`；ES 服务器 9.2.3 与 Boot 管理的客户端版本无显式对齐（pom 无 override）。
**R12 库存写接口挂在公开面（契约不对称）｜影响 Medium｜可能性 Medium｜置信度 High**
`POST /products/stock/batch-*` 属 `ProductController`，而 `ProductInternalController` 就在同一服务；`ProductClient` 6 个方法只用 3 个（单商品 deduct/rollback 为死契约）。任何对 `/api/products/**` 的鉴权收紧都可能静默打断下单。

**R13 聚合根事件列表是活视图，延迟发布会静默丢事件｜影响 Medium｜可能性 Low｜置信度 High（测试过程中发现）**
`AggregateRoot.getDomainEvents()` 返回 `Collections.unmodifiableList(domainEvents)`（`:17-19`）——是内部列表的**视图而非快照**；`clearDomainEvents()` 清空同一底层列表（`:21-23`）。当前 `SpringDomainEventPublisher.publishAll` 立即遍历所以安全，但任何「先收集、提交后再发布」或异步化的改动都会读到空列表，导致事件静默丢失且无编译错误。写测试时已踩到（`ArgumentCaptor` 捕到的 list 最终为空）。
- **修复方向**：`getDomainEvents()` 返回 `List.copyOf(domainEvents)`，或发布方法改为变参/流式逐条发布。属单文件改动，但会让现有「取两次 getDomainEvents」的写法需要复查。
- **验证信号**：新增一条测试：publishAll 后立即 clearDomainEvents，断言已捕获的事件快照仍非空（现已在 `UserAuthenticationDomainServiceTest` 以 doAnswer 快照形式存在）。

**R14 商品变更快照匿名可读（2026-10-19 复核发现，已修复）｜影响 Medium｜可能性 Certain（修复前）｜置信度 High**
`ProductSnapshotController` 原本既无 `assertAdmin`，又落在 `GET:/api/products/**` 白名单下（该白名单先于任何鉴权判定）→ 未登录即可读价格/库存变更史。
- **修复（决策 D8）**：`JwtAuthFilter` 改为 **admin-only 优先于白名单**；`api-gateway.yml` 新增 `GET:/api/products/snapshots`、`/**` 两条 admin-only；控制器补第二层 `assertAdmin`（缺角色头也拒）。
- **回归防线**：`JwtAuthFilterTest#snapshotRequiresAdminDespiteProductWhitelist`（401/403/200 三态）+ `ProductSnapshotControllerTest`（3 例）。

### 单实例前提带出的三个连带结论（2026-10-19）

1. **现有的并发保护全部是「单实例级」的**，单实例下它们确实成立：`StatisticsServiceImpl` 的 `synchronized` 双检 + `KEY_LOCKS` 内存单飞、`ProductSearchRebuildService.rebuilding` 的 `AtomicBoolean`。**但它们是隐式依赖部署形态的**：一旦扩多实例，缓存击穿防护与重建互斥会逐实例退化（各算各的、同时删重建）。→ 建议把「单实例」写成可检查约束（compose 注明 + AGENTS.md 记录），而不是口头共识。
2. **没有滚动发布能力**：单实例 = 重启即中断。叠加 ES 索引初始化失败会 **fail-fast 中止启动**（`ElasticsearchIndexConfig.java:33-40`），一次 ES 抖动就能让搜索服务**完全起不来**（而不是降级起）。本地可接受；这是 R9 可用性的真正上限。
3. **S4 补偿任务可以不加分布式锁**（已写入路线图），但补偿依赖的 `@Scheduled` 本身也属于单实例假设的一部分——它不保护业务正确性（正确性由 S1 幂等保证），只避免重复干活。

### 已登记的有意取舍（不建议本轮改动）
- **网关 Redis fail-open**：`validateSession` 在 Redis 异常时放行（`:165-174`），已在代码注释与 `系统审查报告.md`「已登记风险」章节声明，并有 error 级告警。若要关闭需改为 fail-closed 并评估网关整体可用性代价。

---

## 3. Business Application Fitness（业务应用适配度，定性）

| 维度 | 结论 | 依据 |
|---|---|---|
| 业务模型清晰度 | **中** | 实体/角色/状态在代码里自洽（User/Address/Product/Category/Order/OrderItem，状态有注释），但「支付」无渠道语义、「库存归零是否自动下架」无规则；AGENTS.md 只描述状态转换机制，不描述业务规则动因 |
| 数据与状态理解 | **良** | 每服务独占库、生命周期状态明确、逻辑删除 + 快照表 + `pay_time` 齐备；订单项固化价格快照说明设计者理解「历史订单不随改价漂移」 |
| 端到端路径落地 | **弱** | 下单主链路代码完整，但**注册链路在生产断链（R4）**、库存一致性有已知缺口（R5）、无任何端到端验证资产 |
| Agent 指令使用 | **良** | AGENTS.md 质量高（含陷阱与「先核对代码」告诫）；2026-10-19 复核：`docs/` 与 AGENTS.md 均已纳入 git（18 个 docs 文件），R11 闭环；仍未提示 R2/R3 类部署面风险 |
| 业务验证就绪度 | **中（2026-10-19 复核上调）** | 194 例单元测试 + CI（test/package）已覆盖金额/状态流转/降级/消息链路；**仍无契约测试与迁移/集成测试** → 业务规则改动仍需人工三链路回归（R1 残余） |

**最值得记住的一条**：这套代码的**结构是清楚的，边界是干净的，缺的是「证明它仍然正确」的机制**。补测试的收益远高于再拆服务。

---

## 4. 假设与证据缺口（2026-10-19 更新：已闭环两项）

| 项 | 状态 | 说明 |
|---|---|---|
| R3 前提：Sentinel 网关规则是否已在 Nacos 创建 | ✅ **已确认存在**（用户告知） | R9 不再因「规则为空」而加剧；但未确认具体 QPS/降级阈值是否合理 |
| R10 中的 ES 客户端/服务端版本兼容 | ✅ **已确认无问题**（用户告知，运行正常） | R10 仅剩「重建先全删索引」与「别名人工切换」两个子项 |
| R2 运行时旁证 | ⏳ **静态已定案，待一条 curl** | 见下方命令与假阴性陷阱；本地使用下仅做风险确认，不改变优先级 |
| 中间件端口发布是否用于非本地场景 | ✅ **已确认：仅本地使用**（用户告知） | R3 下调为已登记取舍；R2 影响由 High 降为 Medium（但仍是局域网暴露而非仅回环） |
| 多实例部署（D-Open-2） | ✅ **已确认：当前单实例**（用户告知） | S4 补偿任务的 Redis 锁从「必须」降为「未来扩容前必补」；幂等化（S1）已使并发本身无害，见 D2 |

### 如何闭合 R2 的运行时旁证（带阴性对照）

```bash
# 1) 未授权的业务请求应该 401（证明鉴权链确实在工作）
curl -i http://localhost:8080/api/orders# 预期 401 UNAUTHORIZED

# 2) actuator 路由列表：如果返回 200 + JSON 路由定义，则匿名可读成立
curl -i http://localhost:8080/actuator/gateway/routes# 预期 200（风险）或 401/404（安全）

# 3) 写端点受 read-only 保护，应拒（验证缓解措施有效）
curl -i -X POST http://localhost:8080/actuator/gateway/routes# 预期 405/403

# 4) 刷新端点可被触发
curl -i -X POST http://localhost:8080/actuator/refresh -H 'Content-Type: application/json' -d '[]'
```

> ⚠️ **为何不能脱离 Nacos 在本地直接跑网关来做这个测试**：`management.endpoints.web.exposure.include=health,info,refresh,gateway` 这行配置**来自 Nacos 的 `api-gateway.yml`**。若绕过 Nacos 启动，会回退到 Spring Boot 默认仅暴露 `health,info` → `/actuator/gateway/routes` 返回 404 → **得出「安全」的假阴性结论**。因此该旁证必须在已导入配置的真实实例上取。

## 5. 建议的下一步动作（只列验收动作，不排期）

> 先后顺序、工作量与阶段关账标准见 [`improvement-priority-plan.md`](./improvement-priority-plan.md)（2026-10-19：A 立即 / B 决策 / C 一致性主线 / D 可维护性）。

1. 跑上面「如何闭合 R2」的 4 条 curl → 定 R2 运行时结论与缓解措施有效性。
2. ~~查 Nacos 控制台 `SENTINEL_GROUP` 是否存在两个规则 data-id~~ → **已确认存在**；剩下只需确认各路由的 QPS/降级阈值是否与流量相称。
3. ~~`git ls-files docs/ | wc -l` 应为非 0 → 定 R11~~ → **2026-10-19 已闭环**：`git ls-files docs` = 18 个文件；AGENTS.md 亦已入库；仅 `docs/系统审查报告*.md` 按约定忽略。
4. 写 §2 R1 的 3 条特征化测试 → 让 R6/R7/R8 从「人肉核对」变成「红灯即知」。**2026-10-19 部分完成**：③ 已覆盖（`StatisticsServiceImplTest` 上游非 200 则整体失败且不写缓存 + 三个降级工厂失败关闭）；① 的金额公式已锁，但「返回 200」属 controller/HTTP 层，未覆盖；② 「取消后 `t_product.stock` 回原值」只能靠真库集成测试（S0-b），当前仅锁定「回滚失败必须报错」。
5. 库存补偿按 [`migration-roadmap.md`](./migration-roadmap.md) 的 S0→S4 推进（S0 幂等化是任何补偿方案的前置）。
