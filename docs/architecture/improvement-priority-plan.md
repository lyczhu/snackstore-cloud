# Snackstore Cloud 系统改进优先级计划（2026-10-19）

- **依据**：[`architecture-review.md`](./architecture-review.md)（R1–R13，含 2026-10-19 复核更新：R1 由 P0 降 P1、R11 闭环）、[`migration-roadmap.md`](./migration-roadmap.md)（S0–S5）、[`decision-map.md`](./decision-map.md)（D1–D6）、[`violations.csv`](./violations.csv)、[`dependency-impact-findings.md`](./dependency-impact-findings.md)
- **基线**：HEAD `159b3d4`；25 个测试类 / 194 例单元测试 + Maven CI（test + package）已就位
- **排序口径**：先「能否马上关账」、再「失败后果有多静默」、最后「重构收益」；同等后果下优先低成本、可回滚的动作
- **硬约束（不可协商）**：仅本地单机单实例（用户确认，见 review §4）；不引入分布式协调；目标态一律标注「目标/建议」，不与现状混写
- **拍板状态（2026-10-19）**：B1 = 内测（关注册）、B3 = 快照需鉴权、D9 = 仅本机（A1 降为可选）；**仅 B2（库存写接口迁移）待定**。决策记录见 [`decision-map.md`](./decision-map.md) 的 D7–D9。
- **图**：[`improvement-sequence.dot`](./improvement-sequence.dot)

---

## 1. 一页结论

1. **先用半天关掉「便宜且几乎不可逆性为零」的口子**：端口收口（R2/R3）+ 三处一行级修复（R13 活视图、R8 状态字面量、R7/D6 配置契约测试）。
2. **再用半天把「钱路径」钉住**（R6 契约快照测试）：`ProductVO.status` 改名 → 全站下单 fail-closed，这是唯一能在编译期之外捕获它的手段。
3. **R4 已定：走「注册内测」**（2026-10-19）——撤下 register/sms-code 白名单，并补上建号路径；真实通道接入后按 D7 的失效条件加回。
4. **库存一致性（R5 = S1–S5）是最大一笔投入，但必须排在 S0-b 之后**——没有真库验证，改超时语义本身就是新的静默风险。

| 阶段 | # | 动作 | 风险 | 工作量 | 验证信号 |
|---|---|---|---|---|---|
| **A 立即**（≈1 天） | A1 | 【可选加固】`docker-compose.yml` 端口收口到 `127.0.0.1:`（网关 8080 + MySQL/Redis/Nacos/Kafka/ES/Kibana）；仅本机已确认（D9），不做不阻塞其它项 | R2 / R3 | 0.5 h | `docker compose config` 无 `0.0.0.0` 发布；同网段设备连 8080/8848 超时 |
| | A2 | 网关 actuator 暴露收窄（先确认 `GatewaySecurityProperties` 的 `@RefreshScope` 不依赖手动 refresh） | R2 | 0.5 h | 匿名 `GET /actuator/gateway/routes` 返回 404/401；改 Nacos 白名单仍能热刷新 |
| | A3 | `AggregateRoot.getDomainEvents()` 返回 `List.copyOf(...)` | R13 | 15 min | 新增断言：`publishAll` 后 `clearDomainEvents`，已捕获快照仍非空 |
| | A4 | `OrderMapper` 两段 SQL 的 `status = 1` 改为引用 `OrderStatusConstants.COMPLETED` 拼接 | R8 | 20 min | 编译通过、统计单测绿、`grep "status = 1"` 归零 |
| | A5 | 扩展 `common` 的 `NacosConfigContractTest`：路由 order 约束（search 0 < products 1）、stock 写路径必须在 `admin-only-patterns`、白名单不得含写方法 | R7 / D6 | 2 h | 故意调换 order 或删一条 admin-only → 测试红 |
| | A6 | 契约快照测试：断言 product 侧 `ProductVO` 响应字段集 = order 侧副本所需字段集（含 `status`/`price`） | R6 | 3 h | 改名/删字段 → 测试红并打印字段差 |
| **B 决策**（0.5–2.5 天） | B1 ✅已决 | **注册仅内测**（D7）：whitelist 撤下 `POST:/api/auth/register` 与 `POST:/api/auth/sms-code`，保留 login/admin-login；同时定开号路径（D-Open-5） | R4 | 0.5 h（配置）+ 0.5 d（建号接口，可选） | 无 token 调 register/sms-code → 401；login/admin-login 正常；开号路径可用 |
| | B2 ⏳待定 | 库存写接口去留（迁 `/internal/**`）拍板，**与 S2 同期发布** | R12 / D6 | 0.5 d 评估 | 决策写入 `decision-map.md`；否决则维持 A5 的覆盖测试 |
| | B3 ✅已决 | **快照需鉴权（ADMIN）**（D8）：让 `requiresAdmin` 先于 `isWhiteListed`，再把 `GET:/api/products/snapshots`、`/**` 加进 admin-only-patterns | R14（新） | 0.5 d | 无 token 401 / USER 403 / ADMIN 200；`GET /api/products/1` 仍匿名 200 |
| **C 一致性主线**（4–6 天） | C1 | **S0-b 最小集成层**：compose MySQL + product-service 真库用例（原子扣减、幂等前置、`OrderMapper` 口径） | R1 残余 | 1–2 d | 真库可验「扣减/回滚/统计只计 status=1」 |
| | C2 | S1：`stock_deduction_log`（V4）+ 按 `order_no` 幂等 | R5 | 1 d | 同单号重复扣/回滚各只生效一次 |
| | C3 | S2：`GET /internal/stock/deduction/status` 三态查询 | R5 | 0.5 d | 查询结果与流水一致 |
| | C4 | S3：超时标记 `stock_confirm=0`（开关默认关） | R5 | 0.5 d | 注入 Feign 超时后订单存在且标记为 0 |
| | C5 | S4 定时对账 + S5 观测（stuck 订单 gauge/告警） | R5 | 1 d | 5 分钟内自动闭环；gauge 可观测 |
| **D 可维护性**（按需） | D1 | statistics 部分降级 | R9 | 1 d | 单上游失败返回部分数据而非整块报错 |
| | D2 | ES 重建改「临时索引 + 别名切换」 | R10 | 1–2 d | 中断不产生空索引；切换可回滚 |
| | D3 | spotless/checkstyle 入 CI | R1 残余 | 0.5–1 d | 格式不合规即 CI 红 |

---

## 2. 为什么是这个顺序（而不是按风险编号）

| 判断 | 理由 | 证据 |
|---|---|---|
| R2/R3 放最前，但**只做端口收口，不做网关鉴权重构** | 当前部署仅本地单机，威胁面是「同网段可匿名触达」；一行 compose 改动即可把暴露面降到回环，成本远低于引入 `spring-boot-starter-security` | `docker-compose.yml`（8080 与 6 个中间件端口均 `0.0.0.0`，仅 5 个下游服务是 `127.0.0.1:`）；`api-gateway.yml` 的 `management.endpoints.web.exposure.include` |
| R13/R8 先于 R5 | 两处都是「单文件、零语义变更、现有测试能守」的修复；先做能立刻降低后续改动的噪音 | `user-service/.../domain/common/entity/AggregateRoot.java`；`order-service/.../mapper/OrderMapper.java` |
| R6 用**快照测试**而不是共享模块 | 共享契约模块要动 6 个 pom 与所有调用方，收益是「编译期发现」，但成本与发布风险都高于一次字段集断言；先止血，等再次漂移再升级 | `ProductVO` product 侧 12 字段 vs order 侧 6 字段；`OrderServiceImpl` 以 `status == null` 判下架 |
| R4 必须在 R5 之前有结论 | R5 的补偿链路会引入「订单存在但库存状态未知」的新状态；若注册链路本身不可用，验收场景（新用户下单）跑不起来 | `ConsoleSmsSender` 是唯一 `SmsSender` 实现且无 `@Profile` |
| R5 **不能**直接开工 | S1 的正确性依赖唯一约束/事务在真库上的行为，单元测试无法覆盖；且 S3 会改变下单失败语义，属于产品可感知变更 | `docs/库存补偿方案.md`（方案讨论稿）；`migration-roadmap.md` 的 S0-b 未完成 |
| R9/R10/R12 延后 | 三者都需要结构性改动（聚合口径、索引生命周期、接口迁移），当前没有触发条件；R12 已被 A5 的覆盖测试与网关 admin-only 临时兜住 | `StatisticsServiceImpl#extractData`；`ProductSearchRebuildService#rebuildAll`；`ProductClient` 6 方法用 3 |

---

## 3. 各阶段的关账标准（Gate）

| Gate | 何时算过 | 不通过则 |
|---|---|---|
| **G1（A 阶段）** | `./mvnw -B -ntp test` 全绿（194 例基线不回退）；A5/A6 各做一次「故意破坏 → 红 → 恢复 → 绿」演练；4 条 R2 旁证 curl 结果记录进 review §4；`docker compose config` 显示回环绑定 | 不进入 B/C，避免在无防线状态下改业务语义 |
| **G2（B 阶段）** | R4 与 R12 均有书面结论（批准/否决 + 理由 + 失效条件），写入 `decision-map.md` 与 `AGENTS.md` | C 阶段只能做 C1，不得动 S1 之后的切片 |
| **G3（C 前置）** | C1 在本地 compose MySQL 上跑通；明确 CI 是否接入 MySQL service（默认不接，保持 CI 轻量） | 用 S1 的用例作为准入门槛，仍不允许先改语义（S3） |
| **G4（C 关账）** | 超时注入演练：订单不丢、`stock_confirm=0`、5 分钟内闭环；看板/趋势/TOP 数值与改动前一致（roadmap DoD 第 5 条） | 不开 S4（自动取消会放大误判） |

---

## 4. 明确不做 / 延后（避免范围膨胀）

- **多实例化改造与分布式锁**：部署形态未变；现有 `synchronized`/`AtomicBoolean` 单实例假设继续成立（review §2「单实例前提」）。
- **网关引入 Security 过滤器链**：actuator 暴露改用端口收口解决，不新增依赖与过滤器链。
- **共享契约模块 / 删除全部同名类**：先用 A6 的快照测试覆盖，D1–D3 之后再评估。
- **`common` 拆分**：fanIn=6/6 是真实成本，但拆分不解决任何当前风险（`violations.csv` D7）。
- **前端/Swagger 聚合以外的体验层改动**：不涉及。

## 5. 需要外部输入才能推进的点

| 项 | 需要谁 | 阻塞什么 | 当前状态 |
|---|---|---|---|
| 短信服务商与账号（R4） | 产品/运维 | 恢复注册链路 | ✅ **已决：暂无通道 → 走内测（D7）**；恢复条件：接入后加回白名单 + `@Profile("!dev")` |
| 该 compose 是否会用于共享/演示环境（R3） | 运维 | A1 是否必须 | ✅ **已决：仅本机（D9）** → A1 降为可选；失效条件：一旦进入共享/演示环境立即升级为必须 |
| 商品变更快照是否对外公开 | 产品 | B3 的走向 | ✅ **已决：需鉴权（D8，ADMIN）** |
| 内测期新用户开号方式（D-Open-5） | 后端/产品 | D7 落地后的可用性 | ⏳ **待定**：补 admin-only 建号接口 or 运维手写库 |
| CI 是否允许跑集成层（C1） | 团队 | S0-b 的自动化程度 | ⏳ 默认不接，先本地跑，保持 CI 轻量 |
| 库存写接口是否迁 `/internal`（R12） | 架构/后端 | B2、S2 的落点 | ⏳ 与 S2 同期评估，默认不迁 |

## 6. 与既有产物的关系

- **本计划不替代** [`migration-roadmap.md`](./migration-roadmap.md)：其中 S0-a 已完成、S0-b ≈ C1、S1–S5 ≈ C2–C5；顺序、语义与验收标准以该文件为准，本文件只回答「先后与取舍」。
- **决策依据**仍以 [`decision-map.md`](./decision-map.md) 为准：D3（用 `stock_confirm` 旁路字段而非新增状态值）、D4（超时分支需开关）、D5（回归防线先行，已落实）在本计划中保持有效。
- **风险编号**沿用 [`architecture-review.md`](./architecture-review.md)：R11 已闭环、R1 降为 P1（残余 = 集成/契约层），其余编号未变。

## 7. 复核触发条件

出现任一项即重跑一次优先级评估（方法见 [`architecture-understanding.md`](./architecture-understanding.md) §10）：服务数量或部署形态变化；新增/删除内部接口或 Feign 客户端；网关白名单/路由变更；订单状态语义变化；ES 索引结构或别名策略变化；CI 覆盖范围变化；`docker-compose.yml` 的端口发布策略变化。

## 8. 拍板后的执行顺序（2026-10-19）

| 序 | 动作 | 决策 | 影响面 | 注意 |
|---|---|---|---|---|
| 1 | 先写网关测试：快照路径无 token→401 / USER→403 / ADMIN→200；`GET /api/products/1` 仍匿名 200 | D8 | `JwtAuthFilterTest` | 先红后绿，避免改完再补测 |
| 2 | 改 `JwtAuthFilter`：admin-only 判定先于白名单 | D8 | 网关过滤器（安全敏感） | 必须回归「白名单剥离伪造头」「admin-only 403」两类既有用例 |
| 3 | `api-gateway.yml` 加 `GET:/api/products/snapshots`、`GET:/api/products/snapshots/**` 到 admin-only | D8 | Nacos 配置 | 走 Nacos 刷新生效，不是重启 |
| 4 | `api-gateway.yml` 撤下 `POST:/api/auth/register`、`POST:/api/auth/sms-code` | D7 | Nacos 配置 | 生效后验证 login/admin-login 仍匿名可用 |
| 5 | 定开号路径（补 admin-only 建号接口 or 手写库 SOP） | D7 + D-Open-5 | `AdminUserController` 或运维文档 | 不做则内测期没有合法开号路径 |
| 6 | 代码生效后再同步 AGENTS.md 与本计划状态 | D7/D8 | 文档 | 避免文档跑在实现前 |

**顺序理由**：D8 是唯一动到鉴权语义的改动，先加测试再动过滤器；D7 只是配置项，可与之并行但**在 D8 之后**验证，避免白名单与优先级两类改动同时生效导致归因困难。
