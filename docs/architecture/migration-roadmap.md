# 库存一致性演进路线 — evolution-planner

- **议题**：闭合 H6 残余风险（Feign 超时窗口的库存泄漏），落地 `docs/库存补偿方案.md` 中已批准的**方案 C**
- **当前态证据**：`OrderServiceImpl.java:51-79`（本地事务内远程扣减）、product 迁移仅 V1–V3（**无流水表**）、`ProductServiceImpl.java:270-336`（扣减/回滚非幂等）
- **目标态证据**：`docs/库存补偿方案.md`（状态：**方案讨论稿，未实施**）→ 本文件是其**切片化与验收定义**，不是实施记录
- **图**：[`current-vs-target.dot`](./current-vs-target.dot)；决策取舍见 [`decision-map.md`](./decision-map.md)
- ⚠️ 下列所有 target 节点均为 **target/proposed**，尚未有任何一行代码实现

---

## 1. 差距（Gap）

| # | 当前事实（已验证） | 目标能力 | 缺口性质 |
|---|---|---|---|
| G1 | `batchDeductStock`/`batchRollbackStock` 只做 `stock ± qty` 条件更新，同一 orderNo 重复调用**重复生效** | 按 `order_no` 幂等：重复扣减返回既有结果，重复回滚 no-op | 数据 + 逻辑（**任何补偿方案的前置**） |
| G2 | product 侧无法回答「这个订单到底扣没扣」 | `GET /internal/stock/deduction/status?orderNo=` 三态可查 | 契约新增 |
| G3 | `createOrder` 遇任何异常一律回滚本地订单 | 区分**业务失败**（回滚）与**超时**（提交并标记 `stock_confirm=0`） | 语义变更（最高风险片） |
| G4 | 无任何对账/补偿任务 | `@Scheduled` 扫标记单 → 查流水 → 确认/重试/走 `cancelOrder` | 新增机制 |
| G5 | 0 测试、无 CI | 改动前后有回归防线 | 验证缺口（阻塞所有其它片） |
| G6 | 泄漏只存在于口头认知 | 泄漏量可观测（stuck 订单 gauge + 告警） | 观测缺口 |

**关键取舍**：G3 意味着**下单接口的失败语义发生变化**——超时不再返回错误，而是返回成功 + 后台可能自动取消。这是产品可感知行为，不是纯技术重构。

---

## 2. 迁移切片（严格按依赖顺序）

### S0 · 回归防线先行 —— 前置闸门（S0-a 已推平全项目 2026-10-19，S0-b 暂停于环境）
- **原定做什么**：3 条特征化测试锁住现状（下单金额、取消后库存回原、统计只计 `status=1`），并补最小 CI
- **实施时发现两个硬约束（推翻原定范围）**：① 全仓库测试依赖为零（无任何 pom 含 `spring-boot-starter-test`/junit/mockito），不是「有框架缺测试」而是「测试基建不存在」；② Flyway 脚本含 `CREATE DATABASE` + `USE snackstore_xxx`（三个 V1 迁移首部），**H2 跑不了**，依赖真库的集成测试当前不可行
- **因此拆成两片**：
  - **S0-a（已完成）**：纯单元 + Mockito 隔离 Mapper/Feign，不依赖 MySQL/Redis/Nacos。已落地 `OrderServiceImplTest`（**8 个用例全绿**，`mvn test` 全 reactor BUILD SUCCESS），覆盖：金额 `Σ(price×qty)`、**先插订单再扣库存的调用顺序**、扣减返回 false → `STOCK_NOT_ENOUGH`、**`status=null` 视为下架而非 NPE**（评审 R6 的语义锚点）、商品数不匹配 → `ORDER_CREATE_FAILED`、支付条件更新 0 行的两个分支（`ORDER_CANNOT_PAY` / `ORDER_NOT_FOUND`）、取消时回滚失败必须报错、非 CANCELLED 不可删
  - **S0-a 已推平全项目（2026-10-19）**：**160 个用例 / 7 个模块 / 21 个测试文件**，`mvn test` 全 reactor 绿（~25s）。每个模块锁住的是各自最易静默坏的口径：
    | 模块 | 用例 | 锁住的关键语义 |
    |---|---|---|
    | common | 23 | JwtUtil 密钥按字节校验、过期/篡改必判无效；**搜索双通道 DTO 字段集不得互渗**（R6 族漂移闸门）；AuthConstants 头名与角色字面量 |
    | api-gateway | 16 | 白名单剥离伪造 `X-User-*`、方法+路径双匹配、admin-only 与角色大小写不敏感、会话吊销、**Redis fail-open 放行**（有意取舍不可被悄悄改掉）、IP 代理头覆写与过滤器顺序 |
    | user-service | 30 | 注册/登录的**检查顺序**（锁定早于查库、禁用早于口令比较、验证码失败必计数）、管理员登录不泄漏账号存在性、默认地址唯一性与自动提升、口令 72 字节口径、分页兜底 |
    | product-service | 29 | `WHERE stock >= n` 原子扣减、预检查先于更新、失败不发事件、**批量回滚不检查影响行数（与单商品不对称，S1 需收口）**、排序白名单；**发布端**：只发商品当前态、查不到商品降级为删除消息（防索引幽灵）、分区键 `product-{id}`、五种 changeType 全映射、发送失败按 backoff 重试且**绝不把异常抛回业务线程** |
    | product-search-service | 32 | 排序白名单与深分页上限、默认只出上架、**金额元⇄分双向换算**、降级空分页与透传、脏数据不入索引；**消费端**：索引写失败必须重抛且不 ack（消息不丢）、DELETED 只删不写、事件→索引 12 字段全映射、DLT 任何分支都 ack；**全量重建**：先删空再分页写入的顺序、重入只跑一份、失败后标志位必复位、`MAX_PAGES` 上限不死循环 |
    | order-service | 11 | 见上一段（下单主链路）；`ProductClientFallbackFactory` **失败关闭**（code=500 且 data 为空，绝不伪装成功）+ `@Component` 在册（缺 bean 则 SentinelFeign 启动即失败） |
    | statistics-service | 19 | 缓存键隔离与 ±10% TTL 抖动、**上游非 200 则整体失败且不写缓存**、单飞锁内二次检查、分类聚合合并/排序/丢弃未映射；三个 Feign 降级工厂同样失败关闭（不得返回 0/空集合冒充成功） |
  - **额外产出**：测试过程中发现一个新风险（评审 R13：`AggregateRoot.getDomainEvents()` 返回活视图，`clearDomainEvents()` 会清空已传出去的引用），已登记未修
  - **S0-b（暂停）**：跳库集成测试与端到端，需真实 MySQL（本地 compose 拉起）或 Testcontainers（需拉镜像）；原 S0 里的「取消后 `t_product.stock` 回原值」与「统计只计 `status=1`」**只有这一层能验**，S0-a 验不了，不得当作已覆盖
- **为什么先做**：S1–S4 全落在「钱货等价物」路径上。没有 S0，后续每一片都只能靠人工回归
- **验证信号**：故意改坏金额公式或插入顺序时 S0-a 变绿→变红
- **可逆性**：完全可逆（21 个测试文件 + 7 个模块 pom 各加一个 `spring-boot-starter-test` test 依赖 + gateway 额外一个 `reactor-test`；未动任何 main 代码、配置、Flyway 脚本）
- **已知残留**：JDK 25 下 Mockito 靠 self-attach 启用 inline-mock-maker，构建有告警（提示未来可能失效）；彻底解法是在 surefire `argLine` 里以 `-javaagent` 显式挂载 mockito-core，归入 S0-b 一并处理

### S1 · product-service 幂等化 + 流水表
- **做什么**：Flyway **新增 `V4__add_stock_deduction_log.sql`**（表含 `order_no`+`product_id` 唯一约束、`quantity`、`status∈{DEDUCTED,ROLLED_BACK}`、`deduct_time`、`rollback_time`）；`batchDeductStock` 在**同一本地事务**内写流水，同一 orderNo 重复调用直接返回既有结果；`batchRollbackStock` 已 ROLLED_BACK 则 no-op
- **约束遵守**：绝不修改 V1–V3（AGENTS.md：已应用迁移不可编辑）；流水与扣减同事务（无需分布式事务）
- **即时价值（即使不做补偿也值得）**：顺带堵住**并发双回滚导致库存翻倍**——这是原报告 H7/M15 族的残留面
- **多商品注意**：一个 orderNo 对应多行流水，唯一约束须落在 `(order_no, product_id)`
- **验证信号**：同一 orderNo 连调两次 `batchDeductStock` → `t_product.stock` 只减一次；两次 `batchRollbackStock` → 只加一次；S0 测试仍绿
- **可逆性**：additive，可通过不调用新逻辑回退

### S2 · 扣减状态查询接口
- **做什么**：`ProductInternalController` 增 `GET /internal/stock/deduction/status?orderNo=`，返回 `NONE / DEDUCTED / ROLLED_BACK`
- **边界**：只挂在 `/internal/**`（网关路由 `Path=/api/products/**` + `StripPrefix=1` 不转发 `/internal`，保持现状即安全）；**不要**挂到 `ProductController` 公开面（评审 R12 的教训）
- **验证信号**：S1 用例的三种状态各自可查；确认经网关 `/api/products/internal/...` 无法访问该接口

### S3 · 订单侧超时标记（本片需开关）
- **做什么**：Flyway **新增 `V2__add_stock_confirm_to_order.sql`**（`t_order.stock_confirm TINYINT NOT NULL DEFAULT 1`）；`createOrder` 按异常类型分流——业务失败保持现状抛 `STOCK_NOT_ENOUGH` 回滚；**超时类异常**提交事务并置 `stock_confirm=0`，接口返回成功
- **必须加配置开关**（如 `order.stock-confirm.enabled`）：这是唯一改变对外行为的切片，需可一键回退到「超时即回滚」
- **不动的东西**：订单状态机 0/1/2 语义与取值一律不改（`stock_confirm` 是旁路字段）；`OrderMapper` 的 `status = 1 AND deleted = 0` 统计口径不改（否则看板失真，评审 R8）
- **验证信号**：用测试替身注入超时 → 订单存在、`status=0`、`stock_confirm=0`、HTTP 200；关闭开关后行为回到现状
- **待决断（阻塞本片上线，非阻塞开发）**：产品是否接受「用户先看到成功、稍后可能被自动取消」→ 见 `decision-map.md` D-Open-1

### S4 · 定时对账补偿
- **做什么**：`@Scheduled`（建议 5 分钟）扫描 `stock_confirm=0 AND status=PENDING AND created_at < NOW()-5min` → 查 S2 状态：`DEDUCTED` 则置 1 闭环；`NONE` 则幂等重试 `batchDeductStock`；重试超 3 次仍失败 → 调用**现有** `cancelOrder`（其 `UPDATE ... WHERE status=0` 原子流转 + 回滚结果检查已由 M15 保证）
- **并发安全（2026-10-19 已定）**：用户确认**当前单实例部署** → **本切片不加 Redis 分布式锁**。即使未来多实例并发跑，S1 的幂等流水已保证重复执行只得到既有结果，**不产生错误数据**（锁从正确性前提降为资源优化）。与用户手动取消并发时，仍依赖 `cancelOrder` 的 `UPDATE ... WHERE status=0` 影响行数判断（0 行即退出，不会双取消）。
  - **失效条件**：一旦扩为多实例，必须补上锁以避免重复 Feign 调用与重复日志（不影响正确性）；可参照 `user-service`/`statistics-service` 已有的 Redis 用法。
- **范围纪律**：**只处理带标记的订单**，绝不引入「超时自动取消未支付订单」这类业务规则变化（方案文档第 8 条明确禁止）
- **验证信号**：S3 注入的标记单在 ≤5 分钟内被闭环为 `stock_confirm=1`（已扣场景）或被幂等重试后取消（未扣场景）；正常未支付订单不受影响

### S5 · 观测与收口
- **做什么**：暴露 stuck 标记单数量 gauge（`stock_confirm=0` 且超龄）、补偿成功/失败计数；补 `AGENTS.md` 与 `docs/库存补偿方案.md` 的状态（从「未实施」改为「已实施 + 残留边界」）
- **验证信号**：压测下单 + 人工断网 product-service，观察 gauge 上升与回落后归零；文档状态与代码一致（评审 R11 的同类问题不再复现）

---

## 3. 依赖与排序理由

```
S0 ──▶ S1 ──▶ S2 ──▶ S3(开关) ──▶ S4 ──▶ S5
闸门   幂等    查询   语义变更     机制    闭环
```
- **S1 必须先于 S3/S4**：方案文档第 17 行「任何补偿方案必须先做幂等化」——补偿会重试，非幂等的重试等于制造新的漂移
- **S0 必须先于一切**：本次评审的 P0 风险（0 测试）使「改了但没人能证明没改坏」成为主要风险形态
- **S3 与 S4 可并行开发、不可并行上线**：S4 依赖 S3 产生的标记数据
- **S1+S2 可独立发布并长期停留**：即使产品否决 S3 的语义变更，S1 的幂等化与 S2 的对账查询接口仍有独立价值（人工/脚本对账可用），**不构成沉没成本**

## 4. 若产品否决 S3（降级路线）

退化为「幂等 + 对账不改语义」：S0 + S1 + S2 + **人工对账 SOP**（定时导出「流水有扣减但订单不存在」清单，运维按单回滚）。代价：泄漏不会被自动修复，但订单对外行为完全不变、无用户感知风险。此路线**不推荐但可接受**，比现状（无任何兜底）好。

## 5. 里程碑完成定义（DoD）

1. S0-a 测试稳定为绿，且对金额/插入顺序/状态口径的破坏性改动能被捕获（**已达成 2026-10-19**：全项目 160 例全绿；但**无 CI**，只能靠人工执行 `mvn test`，本条不算完全关账）
2. 同一 orderNo 的重复扣减与重复回滚均为幂等（S1 用例）
3. 注入超时后：订单不丢失、`stock_confirm=0`、5 分钟内自动闭环（S3+S4）
4. 无「超时自动取消正常未支付订单」的行为变化（S4 范围纪律）
5. 看板/趋势/TOP 数值与改动前一致（统计口径未被触碰）
6. `docs/库存补偿方案.md` 与 `AGENTS.md` 状态更新为已实施，且两者纳入 git 跟踪
