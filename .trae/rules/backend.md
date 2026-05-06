## 一、版本规则
- Java：25
- SpringBoot：3.5.13
- Spring Cloud：2025.0.2
- Spring Cloud Alibaba：2025.0.0.0
## 二、分层架构规则（强制严格执行）
- 严格遵循 **四层架构**，**禁止跨层调用**
- **禁止 Controller 直接操作 DB**
- **禁止在 Controller 编写任何业务逻辑**
- **必须面向接口编程**：service 业务层 + service.impl 实现层
- 层职责定义：
  1. **Controller**：仅接收请求、参数校验、调用 Service、统一返回结果，无业务逻辑、无计算、无事务
  2. **Service**：业务逻辑、事务控制、条件判断、数据计算、调用 Mapper、组合逻辑
  3. **Dao/Mapper**：仅数据库 CRUD，**无任何业务逻辑、无判断、无计算**
  4. **Model 分层**：严格区分，禁止混用
     - **DO**：数据库实体，与表结构一一对应
     - **DTO**：接口入参，用于前端/服务间传参
     - **VO**：接口出参，用于返回前端视图
     - 禁止 DO/DTO/VO 互相赋值混用
## 三、异常处理规则
- 统一使用 **自定义业务异常**（如 `BusinessException`），**禁止直接抛出 RuntimeException/Exception**
- 异常必须携带 **业务错误码 + 错误信息**
- 异常只在 Service 层抛出，Controller 不处理异常，由**全局异常处理器统一捕获**
- 禁止空 catch 块，禁止吞异常
## 四、软件设计规则
- 严格遵循 **SOLID 原则**
- 类职责必须**单一**，禁止大杂烩类
- 继承层级 ≤ 2 层，优先使用**组合/接口**而非继承
- 依赖注入必须使用 **构造器注入**，禁止 field 注入
- 禁止循环依赖
- 方法粒度最小化，一个方法只做一件事
## 五、API 使用规则
- 时间 API：**强制使用 java.time 包**（LocalDateTime、LocalDate、Instant）
  - 禁止使用 `java.util.Date` / `Calendar`
- 集合 API：优先使用 JDK 原生集合，禁止冗余工具类
- 字符串：优先使用 `StringBuilder` 做拼接，禁止 `+` 高频拼接
- 并发：必须使用线程池，禁止手动 `new Thread()`
## 六、数据库规范
- MyBatis-Plus / MyBatis 必须使用 **参数化查询**，禁止手写 SQL 拼接
- 禁止 Service 直接写 SQL
- 分页必须使用**分页插件**，禁止内存分页
- 事务必须加在 Service 层，使用 `@Transactional(rollbackFor = Exception.class)`
- 禁止事务嵌套过深，禁止跨服务事务
- 数据库表结构迁移必须统一使用 Flyway 版本化脚本管理
- 禁止手动改线上 / 开发库表结构，所有 DDL 变更全部走 Flyway SQL 脚本
- Flyway 脚本遵循版本命名规范：V1__初始化表.sql、V2__新增字段.sql
## 七、命名风格（全局统一）
- 全部使用 **小驼峰**：userInfo、userService、createUser
- 类名使用 **大驼峰**：UserController、UserVO、UserDTO
- 常量使用 **全大写下划线**：MAX_SIZE、DEFAULT_STATUS
- 包名全小写：com.xxx.user.controller
- 禁止中文命名、禁止拼音混合、禁止无意义命名（a、b、temp）
## 八、代码格式与通用规范
- 缩进：4 空格
- 一行代码长度 ≤ 120 字符
- 方法行数 ≤ 50 行
- 所有接口返回统一 **Result<T> 格式**
- DTO 必须加 JSR380 校验注解（@NotBlank、@NotNull 等）
- 禁止魔法值，全部使用枚举/常量
- 日志必须使用 SLF4J，禁止 `System.out.println()`
- 禁止在循环里查询数据库
## 九、编译与语法校验规则
- 所有功能代码编写完成后，必须保证代码可正常编译、无语法报错