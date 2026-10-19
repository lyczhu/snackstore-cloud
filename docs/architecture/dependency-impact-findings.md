# 依赖与变更波及面分析 — dependency-impact-analyzer

- **分析粒度**：可部署服务 + 共享库 + 服务间契约 + 配置面 + 数据面（非文件级）
- **图**：[`dependency-map.dot`](./dependency-map.dot)（依赖与耦合度量）、[`impact-map.dot`](./impact-map.dot)（6 个变更点的爆炸半径）
- **机器可读清单**：[`violations.csv`](./violations.csv)
- **证据基线**：193 个 `src/main/java` 文件、6 个模块 pom、5 个 `@FeignClient`、`nacos-config/api-gateway*.yml`、迁移脚本
- **注意**：本报告不含风险定级（→ [`architecture-review.md`](./architecture-review.md)），只回答「谁依赖谁、改这里会扩散到哪」

---

## 1. 结构度量结论

服务间调用构成**严格 DAG，无环**——这是当前架构最健康的一点：`statistics → {order, product, user}`、`{order, search} → product`，`product` 不反向依赖任何人。

| 服务 | Feign fanIn | Feign fanOut | instability I=fanOut/(fanIn+fanOut) | 含义 |
|---|---|---|---|---|
| product-service | 3 | 0 | **0.00（最稳定）** | 领域核心，被 order/search/statistics 三面依赖，符合「稳定被依赖」原则 |
| user-service | 1 | 0 | 0.00 | 仅被统计依赖 |
| order-service | 1 | 1 | 0.50 | 依赖商品，被统计依赖 |
| statistics-service | 0 | 3 | **1.00（最不稳定）** | 聚合器：依赖三个服务，无人依赖它 |
| product-search-service | 0 | 1 | 1.00 | 依赖商品（拉取 + 事件） |
| api-gateway | 0（路由 5） | 0 | n/a | 纯配置面入口，不参与业务依赖 |

`common` 的编译期 fanIn = **6/6 模块**，是全仓库唯一的强耦合汇聚点。

**结论**：结构方向是对的（不稳定依赖稳定，无环）。真正的耦合风险不在图上，而在**边上的契约没有任何静态约束**——见 §3。

---

## 2. 六个变更点的爆炸半径

### A｜改 `common` 的 `Result`/`PageResult` 形状 — 半径最大

- **传播方向**：编译期反向（6/6）+ 序列化边界
- **停止准则**：进程边界（各服务独立打包，需全量重启）
- **确认影响**：全模块重编译与重启；`StatisticsServiceImpl.extractData`（`:213-222`）以 `code == 200` 判成功，字段改名即把三个 Feign 客户端的降级判定打成「恒失败」；网关 `JwtAuthFilter.writeErrorResponse`（`:185-197`）直接序列化 `Result`，形状变更同时改变对外错误契约
- **证据**：`common/.../response/{Result,PageResult}.java`、6 个 pom 的 `common` 依赖
- **验证**：改动后至少跑一次「下单 + 看板 + 搜索」三链路手工回归（当前无测试可替代）

### B｜改 product-service 输出契约（`ProductVO`/`ProductDetailVO` 字段）— 最危险

- **传播方向**：运行时反向 1 跳，**但编译期不报错**（order/statistics 各持一份同名 DTO 副本）
- **停止准则**：Jackson 按字段名匹配；缺失字段静默为 `null`，不抛异常
- **确认影响（锋利的这条）**：`OrderServiceImpl.buildOrderItems`（`:94-98`）判断 `pVO.getStatus() == null || pVO.getStatus() == 0` 即抛 `PRODUCT_OFF_SHELF`。也就是说：**只要 `status` 字段被改名或从响应中移除，全站下单 100% 失败**，方向是 fail-closed（不会超卖，但等于拒绝服务）。同理 `price` 变 `null` 会让 `item.getProductPrice().multiply(...)`（`:57`）直接 NPE
- **已实际漂移的证据**：product 侧 `ProductVO` 有 12 字段（含 `highlightedName`/`highlightedDescription`），order 侧副本只有 6 字段 → 两份「ProductVO」早已不是同一个东西；且 product 侧那两个高亮字段在 `convertToVO`（`:343-361`）中从未赋值，属于搜索关注点对主数据 VO 的泄漏 + 死字段
- **验证**：任何字段增删改名，必须同步核对 `order-service/feign/product/*`、`statistics-service/client/*`、`common/dto/ProductSearchItemDTO` 三处，并跑通下单与看板

### C｜改网关路由 `order` 或路径前缀 — 半径隐蔽

- **传播方向**：配置面（无代码引用）
- **停止准则**：`AntPathMatcher` + `order` 排序
- **确认影响**：`/api/product-search/**` 必须 order 0 先于 `/api/products/**` order 1（`api-gateway-routes.yml:21-34`）；顺序写反则搜索请求被商品服务吞掉，而**没有任何代码守卫或启动校验**会报错，表现为「搜索返回 404/商品数据」
- **附带**：`admin-only-patterns` 6 条库存/商品模式与 `whitelist` 6 条（`api-gateway.yml:48-74`）必须与各控制器路径人肉同步；把 `/internal/**` 从 `api-gateway-routes.yml` 移除是刻意的（历史曾形成开放代理）
- **验证**：改路由后至少验证「搜索命中、匿名商品列表可访、普通用户改价被拒」三点

### D｜改 ES 文档结构 / `INDEX_NAME` — 半径含人工动作

- **传播方向**：数据面 + 两条同步通道
- **停止准则**：别名 `product` 指向新索引（**应用不会自动切**，只 `log.warn`，`ElasticsearchIndexConfig.java:71-80`）
- **确认影响**：`ProductSearchDocument` + `common` 的两个 DTO（事件 `ProductSearchSyncMessage` 与拉取 `ProductSearchItemDTO`）+ `ProductSearchIndexServiceImpl` 映射 + 搜索/重建/降级四条路径需同时改；启动会重建索引与 mapping（fail-fast）
- **可能影响（未证实）**：ES 服务器 9.2.3 与 Boot 3.5.15 BOM 管理的 elasticsearch-java 客户端版本兼容性——`product-search-service/pom.xml` 无显式 override，静态无法判定，需运行时验证

### E｜改订单状态语义（新增值或改含义）— 静默失真型

- **传播方向**：数据面 + **SQL 字面量**
- **停止准则**：无（字面量与常量之间没有编译期关联）
- **确认影响**：`OrderMapper` 两段 `@Select` 硬编码 `status = 1 AND deleted = 0`（`:17-28`），与 `OrderStatusConstants.COMPLETED` 无引用关系。若新增状态 3（如「已退款」）并让它也属于「已完成」语义，**趋势/销售额/TOP 商品会静默漏算**，看板与订单侧数字对不上但无报错
- **同族**：`payOrder WHERE status=0→1`、`cancelOrder WHERE status=0→2`、`deleteOrder WHERE status=2`（`OrderServiceImpl:166-219`）都是字面常量引用，改语义要逐处核对
- **验证**：改状态定义时，以 `OrderMapper` + `OrderStatusConstants` + 三个状态条件更新点为必查清单

### F｜改库存接口路径或语义 — 半径含鉴权面

- **传播方向**：运行时（order 侧 Feign）+ 网关鉴权模式
- **停止准则**：`@FeignClient` path 与 `admin-only-patterns` 字符串
- **确认影响**：`ProductClient`（`order-service/feign/product/ProductClient.java:16-32`）声明了 6 个方法，但代码只用到 3 个（`getProductsByIds`/`batchDeductStock`/`batchRollbackStock`），单商品 `deductStock`/`rollbackStock` 是**死契约**；库存写接口目前挂在**公开面** `POST /products/stock/batch-*` 上，网关侧对应 6 条 admin-only 模式
- **架构提示**：若把库存写迁移到 `/internal/**`（与 search/statistics 一致的契约面），需同步改 order 侧 path + 删网关模式；反之，任何对 `/api/products/**` 的鉴权收紧都可能**静默改变订单语义**——这是当前最容易被误伤的一条边
- **验证**：改动后必须实测「用户下单成功」+「普通用户直接调库存接口被拒」

---

## 3. 三个治理级发现（跨变更点普遍成立）

**G1 契约无静态约束（最高频踩坑源）。** 13 组跨模块同名类：`ProductClient`、`ProductClientFallbackFactory`、`BatchStockDTO`、`ProductVO`、`OrderStatisticsVO`、`ProductSalesVO`、`ProductCategoryVO` 各调用方自建副本；`BusinessException`/`BusinessExceptionEnum`/`GlobalExceptionHandler` ×5；`MybatisPlusConfig` ×3；`KafkaConfig` ×2。AGENTS.md 把「没有共享契约模块」写成有意设计，但它同时意味着**唯一的安全网是契约测试，而测试数为 0**。

**G2 语义耦合靠魔法数字与字符串。** 三类：SQL 里的 `status = 1`（E）、网关配置里的 `"POST:/api/products"`（C/F）、Kafka topic 名与 Redis key 前缀（`token:`、`product-changed`）散落在 `common` 与两个服务里。改这些没有任何一处会编译失败。

**G3 隔离得好的部分（可以放心改）。** 数据面隔离是这套架构最扎实的地方：三个服务各自独占 MySQL 库（`snackstore_user/product/order`），跨服务取数**只经 API，无一处直连他人库**；服务内部实现（service/mapper、user-service 的 DDD 四层）改起来不外溢。也就是说：**结构边界是健康的，脆弱的全在边界上的契约表示法。**

---

## 4. 本分析回答不了的（需运行时证据）

- 真实调用扇出与并发规模（无 metrics/tracing；`management.endpoints` 暴露了 prometheus 但无采集端）
- Sentinel 网关规则是否已在 Nacos 创建（仓库里只有 `nacos-config/sentinel/*.json` 基线备份，AGENTS.md 明确说需手工创建）
- 哪条边是热路径（`/internal/products` 全量重建 vs 事件增量，实际占比未知）

**下一步**：风险定级与优先级见 [`architecture-review.md`](./architecture-review.md)；若要改 product-service 契约，先看本节 B/F 与 §3 G1。
