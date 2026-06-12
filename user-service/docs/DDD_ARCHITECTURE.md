# User Service DDD 架构重构说明文档

## 一、架构概览

本次重构采用**领域驱动设计(DDD)**思想，将原有的贫血模型+事务脚本架构升级为**四层DDD架构**，实现业务逻辑的高内聚和低耦合。

### 架构分层

```
┌─────────────────────────────────────────────────────┐
│          Presentation Layer (表现层)                 │
│     UserController / AddressController               │
│     职责: 接收请求、参数校验、调用应用服务、返回结果   │
├─────────────────────────────────────────────────────┤
│          Application Layer (应用层)                  │
│     UserApplicationService / AddressApplicationService│
│     职责: 协调领域服务、事务控制、DTO/VO转换          │
├─────────────────────────────────────────────────────┤
│          Domain Layer (领域层)                       │
│     聚合根 / 实体 / 值对象 / 领域服务 / 仓储接口 / 事件│
│     职责: 核心业务逻辑、领域规则、不变性约束           │
├─────────────────────────────────────────────────────┤
│       Infrastructure Layer (基础设施层)              │
│     仓储实现 / Mapper / DO对象 / 转换器              │
│     职责: 技术实现、数据持久化、外部系统交互            │
└─────────────────────────────────────────────────────┘
```

## 二、包结构

```
com.lawyus.snackstore.user
├── UserServiceApplication.java
├── config/                          # 配置类
│   └── MybatisPlusConfig.java
│
├── presentation/                    # 表现层
│   └── controller/
│       ├── UserController.java
│       └── AddressController.java
│
├── application/                     # 应用层
│   ├── dto/                         # 命令对象
│   │   ├── UserRegisterCommand.java
│   │   ├── UserLoginCommand.java
│   │   ├── UserUpdateCommand.java
│   │   ├── AddressCreateCommand.java
│   │   └── AddressUpdateCommand.java
│   ├── vo/                          # 视图对象
│   │   ├── UserViewVO.java
│   │   ├── LoginViewVO.java
│   │   └── AddressViewVO.java
│   ├── converter/                   # 应用层转换器
│   │   ├── UserViewConverter.java
│   │   └── AddressViewConverter.java
│   └── service/                     # 应用服务接口+实现
│       ├── UserApplicationService.java
│       ├── AddressApplicationService.java
│       └── impl/
│           ├── UserApplicationServiceImpl.java
│           └── AddressApplicationServiceImpl.java
│
├── domain/                          # 领域层
│   ├── user/                        # 用户限界上下文
│   │   ├── model/
│   │   │   ├── entity/
│   │   │   │   └── User.java        # 用户聚合根
│   │   │   └── valueobject/
│   │   │       ├── Phone.java       # 手机号值对象
│   │   │       ├── Password.java    # 密码值对象
│   │   │       ├── UserRole.java    # 用户角色枚举
│   │   │       └── UserStatus.java  # 用户状态枚举
│   │   ├── event/                   # 领域事件
│   │   │   ├── UserRegisteredEvent.java
│   │   │   └── UserLoginEvent.java
│   │   ├── service/                 # 领域服务
│   │   │   └── UserAuthenticationDomainService.java
│   │   └── repository/              # 仓储接口
│   │       └── UserRepository.java
│   │
│   └── address/                     # 地址限界上下文
│       ├── model/
│       │   ├── entity/
│       │   │   └── Address.java     # 地址聚合根
│       │   └── valueobject/
│       │       ├── ReceiverInfo.java  # 收货人信息值对象
│       │       └── AddressDetail.java # 地址详情值对象
│       ├── event/                   # 领域事件
│       │   └── AddressCreatedEvent.java
│       ├── service/                 # 领域服务
│       │   └── AddressManagementDomainService.java
│       └── repository/              # 仓储接口
│           └── AddressRepository.java
│
└── infrastructure/                  # 基础设施层
    ├── persistence/
    │   ├── do_/                     # 数据库对象
    │   │   ├── UserDO.java
    │   │   └── AddressDO.java
    │   ├── mapper/                  # MyBatis Mapper
    │   │   ├── UserMapper.java
    │   │   └── AddressMapper.java
    │   └── repository/              # 仓储实现
    │       ├── UserRepositoryImpl.java
    │       └── AddressRepositoryImpl.java
    └── converter/                   # 领域<->DO转换器
        ├── UserConverter.java
        └── AddressConverter.java
```

## 三、领域模型

### 3.1 用户上下文 (User Context)

**聚合根: User**

```
User (聚合根)
├── id: Long
├── phone: Phone (值对象)
├── password: Password (值对象)
├── nickname: String
├── avatar: String
├── role: UserRole (值对象/枚举)
├── status: UserStatus (值对象/枚举)
├── createdAt: LocalDateTime
└── updatedAt: LocalDateTime

方法:
- create()         # 工厂方法创建新用户
- restore()        # 工厂方法从持久化数据恢复
- updateProfile()  # 更新用户资料
- changeStatus()   # 变更用户状态
- isAdmin()        # 判断是否管理员
- isActive()       # 判断是否启用状态
```

**值对象说明:**

- **Phone**: 封装手机号，包含格式校验逻辑
- **Password**: 封装密码，包含加密和验证逻辑
- **UserRole**: 用户角色枚举 (USER/ADMIN)
- **UserStatus**: 用户状态枚举 (DISABLED/ENABLED)

**领域服务: UserAuthenticationDomainService**

负责用户认证相关的业务逻辑：
- 用户注册(验证码校验 + 手机号唯一性校验 + 创建用户)
- 用户登录(查找用户 + 状态校验 + 密码校验)
- 管理员登录(额外校验角色)

### 3.2 地址上下文 (Address Context)

**聚合根: Address**

```
Address (聚合根)
├── id: Long
├── userId: Long
├── receiverInfo: ReceiverInfo (值对象)
│   ├── name: String
│   └── phone: String
├── addressDetail: AddressDetail (值对象)
│   ├── province: String
│   ├── city: String
│   ├── district: String
│   └── detail: String
├── isDefault: boolean
├── createdAt: LocalDateTime
└── updatedAt: LocalDateTime

方法:
- create()          # 工厂方法创建新地址
- restore()         # 工厂方法从持久化数据恢复
- updateInfo()      # 更新地址信息
- setAsDefault()    # 设为默认地址
- unsetDefault()    # 取消默认
- belongsToUser()   # 验证地址是否属于指定用户
```

**值对象说明:**

- **ReceiverInfo**: 收货人信息(姓名+手机号)
- **AddressDetail**: 地址详情(省+市+区+详细地址)

**领域服务: AddressManagementDomainService**

负责地址管理相关的业务逻辑：
- 创建地址(处理默认地址逻辑)
- 更新地址
- 删除地址
- 设置默认地址
- 查询地址列表

## 四、领域模型关系图

```
┌─────────────────────────────────────────────────────┐
│                   用户上下文                          │
│                                                     │
│  User (聚合根)                                       │
│  ├── Phone (值对象)                                  │
│  ├── Password (值对象)                               │
│  ├── UserRole (值对象)                               │
│  └── UserStatus (值对象)                             │
│                                                     │
│  UserAuthenticationDomainService (领域服务)           │
│  └── 依赖: UserRepository (仓储接口)                  │
└─────────────────────────────────────────────────────┘
                        │
                        │ 关联 (userId)
                        ▼
┌─────────────────────────────────────────────────────┐
│                   地址上下文                          │
│                                                     │
│  Address (聚合根)                                    │
│  ├── ReceiverInfo (值对象)                           │
│  └── AddressDetail (值对象)                          │
│                                                     │
│  AddressManagementDomainService (领域服务)            │
│  └── 依赖: AddressRepository (仓储接口)               │
└─────────────────────────────────────────────────────┘
```

## 五、核心DDD原则实现

### 5.1 聚合根与不变性

- User 和 Address 作为各自上下文的聚合根
- 通过私有构造函数 + 工厂方法 (create/restore) 控制创建
- 所有状态变更通过聚合根自身方法完成，保证不变性约束

### 5.2 值对象

- 不可变对象，使用 final 字段
- 通过私有构造函数 + 静态工厂方法创建
- 包含自验证逻辑（创建时校验格式）
- 封装业务语义（如 Password 包含加密和匹配逻辑）

### 5.3 领域服务

- 处理跨聚合根或复杂业务逻辑
- 依赖仓储接口而非实现
- 无状态，可被多个应用服务复用

### 5.4 仓储模式

- 接口定义在领域层，实现在基础设施层
- 面向聚合根操作，而非数据库表
- 隐藏持久化技术细节

### 5.5 依赖倒置

```
表现层 → 应用层 → 领域层 ← 基础设施层
                    ↑ 依赖接口
                    └──────┘
              基础设施层实现接口
```

## 六、数据流转

```
请求 → Controller
       ↓
    Command (DTO)
       ↓
    ApplicationService
       ↓ (协调)
    DomainService
       ↓ (操作)
    聚合根 (领域模型)
       ↓ (持久化)
    Repository (接口)
       ↓
    RepositoryImpl (实现)
       ↓ (转换)
    DO → Mapper → DB
       ↓
    聚合根 ← DO ← Mapper
       ↓
    VO (转换)
       ↓
    Result<VO> (响应)
```

## 七、重构收益

1. **业务逻辑内聚**: 领域规则封装在聚合根和值对象中
2. **可测试性提升**: 领域层无框架依赖，可独立单元测试
3. **可维护性**: 职责清晰，修改业务逻辑只需关注领域层
4. **可扩展性**: 新增业务只需添加领域服务/聚合根方法
5. **防腐层**: 领域模型与外部技术解耦，可替换持久化方案
6. **类型安全**: 值对象提供强类型约束，避免参数误传

## 八、API兼容性

重构后API接口路径、请求参数、响应格式**完全兼容**前端，无需前端改动。

### 接口映射表

| 旧DTO/VO | 新Command/VO | 说明 |
|---------|-------------|------|
| UserRegisterDTO | UserRegisterCommand | 用户注册请求 |
| UserLoginDTO | UserLoginCommand | 用户登录请求 |
| AdminLoginDTO | UserLoginCommand | 管理员登录(复用) |
| UserUpdateDTO | UserUpdateCommand | 用户更新请求 |
| AddressDTO | AddressCreateCommand / AddressUpdateCommand | 地址请求 |
| UserVO | UserViewVO | 用户视图 |
| LoginVO | LoginViewVO | 登录视图 |
| AddressVO | AddressViewVO | 地址视图 |
