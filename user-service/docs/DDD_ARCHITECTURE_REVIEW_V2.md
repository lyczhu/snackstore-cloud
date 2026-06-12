# DDD 架构审查报告（改进后复审）

> **审查范围**: `user-service` | **审查日期**: 2026-05-16 | **文件总数**: 40+ Java 文件

---

## 一、总体评估

| 维度 | 改进前评分 | 改进后评分 | 变化 |
|------|:---------:|:---------:|:----:|
| 领域模型纯度 | B+ | A- | ↑ |
| 依赖倒置 | B | A- | ↑ |
| 基础设施解耦 | B- | A- | ↑↑ |
| 领域事件 | B- | A- | ↑↑ |
| 端口/适配器 | D | A- | ↑↑↑ |
| 仓储设计 | B | A- | ↑ |
| 聚合设计 | A- | A- | → |
| 应用层职责 | B | A- | ↑ |

**整体评分: A-**（改进前 B）

---

## 二、已解决的核心问题

### ✅ #1 - Password 值对象解耦 BCrypt
`Password` 不再直接依赖 `BCryptPasswordEncoder`，改为注入领域层接口 `PasswordEncoder`：

```java
// Password.java
public static Password fromRaw(String rawValue, PasswordEncoder encoder) { ... }
public boolean matches(String rawPassword, PasswordEncoder encoder) { ... }
```

基础设施层 `BCryptPasswordEncoderAdapter` 实现该接口，领域层零框架依赖。

### ✅ #6 - DomainConfig 注册领域服务
`DomainConfig.java` 通过 `@Bean` 显式声明领域服务实例，消除了对 `@Service` 注解的依赖。

### ✅ #8 - 抽取 VerificationCodePort / TokenPort
领域层定义三个端口接口，基础设施层实现三个适配器：

| 端口（领域层） | 适配器（基础设施层） |
|--------------|-------------------|
| `PasswordEncoder` | `BCryptPasswordEncoderAdapter` |
| `VerificationCodePort` | `RedisVerificationCodePort` |
| `TokenPort` | `JwtTokenPort` |

应用层不再直接操作 `StringRedisTemplate` 和 `JwtUtil`。

### ✅ #12 - 领域事件发布机制
完整的事件体系：
```
BaseDomainEvent → AggregateRoot（收集事件）→ DomainEventPublisher（发布）→ SpringDomainEventPublisher（Spring 实现）
```

### ✅ #14 - PageSpecification / PagedResult
引入领域层分页规范，替代裸的 `pageNum/pageSize` 参数。

### ✅ #15 - 仓储 save() 身份回填
`save()` 返回后调用 `aggregate.assignId(insertedId)` 回填 ID。

### ✅ #20 - User.disable() / User.enable() 业务规则
重复禁用/启用抛出 `BusinessExceptionEnum.USER_ALREADY_DISABLED / USER_ALREADY_ENABLED`。

### ✅ Address 子域
- `ReceiverInfo` 复用 `Phone` 值对象
- `AddressRepository.clearDefaultAddress()` 替换为 `findDefaultByUserId()`
- `AddressManagementDomainService` 构造器注入 `DomainEventPublisher`

---

## 三、新发现的问题

### 🔴 P0 — 严重问题

#### 3.1 领域事件未被发布（UserRegisteredEvent / UserLoginEvent）

**文件**: [UserAuthenticationDomainService.java](file:///e:/Projects/Java/snackstore-cloud/user-service/src/main/java/com/lawyus/snackstore/user/domain/user/service/UserAuthenticationDomainService.java)

```java
// 注册流程
public User register(...) {
    ...
    User saved = userRepository.save(user);
    saved.onRegistered();   // ← 事件注册了，但从未发布！
    return saved;
}

// 登录流程
public User login(...) {
    ...
    user.onLogin();         // ← 事件注册了，但从未发布！
    return user;            // ← 没有调用 userRepository.save()
}
```

**根因**: `UserAuthenticationDomainService` 没有注入 `DomainEventPublisher`，且 `login()` 不经过 `save()`。
`onRegistered()` 在 `save()` 之后调用，避免了 ID 为空的问题，但事件随后跟着聚合根返回应用层就丢失了。

**预期行为**: `UserRegisteredEvent` 在用户注册成功后发布，`UserLoginEvent` 在用户登录成功后发布。

---

#### 3.2 事件双重发布（冗余）

**文件**: 
- [UserRepositoryImpl.java](file:///e:/Projects/Java/snackstore-cloud/user-service/src/main/java/com/lawyus/snackstore/user/infrastructure/persistence/repository/UserRepositoryImpl.java#L49-L53)
- [UserManagementDomainService.java](file:///e:/Projects/Java/snackstore-cloud/user-service/src/main/java/com/lawyus/snackstore/user/domain/user/service/UserManagementDomainService.java#L39-L42)

```java
// UserRepositoryImpl.save() - 第一次发布
eventPublisher.publishAll(user.getDomainEvents());
user.clearDomainEvents();

// UserManagementDomainService.updateUser() - 第二次（但事件已清空，无效调用）
eventPublisher.publishAll(saved.getDomainEvents());
saved.clearDomainEvents();
```

**问题**: 仓储和领域服务都在发布事件。仓储已负责发布 + 清理，领域服务再次调用是冗余的（事件列表已在仓储中清空）。虽然不会造成实际 bug（因为第二次发布的是空列表），但造成了职责模糊 —— 事件发布应该哪一个层负责？

**建议**: 统一由仓储在 `save()` 时发布，领域服务不再关心事件发布。

---

### 🟡 P1 — 中等问题

#### 3.3 领域事件注册时机存在时间耦合

**文件**: 
- [User.java](file:///e:/Projects/Java/snackstore-cloud/user-service/src/main/java/com/lawyus/snackstore/user/domain/user/model/entity/User.java#L90-L96)

```java
public void onRegistered() {
    registerEvent(new UserRegisteredEvent(id, phone.getValue(), nickname));
}

public void onLogin() {
    registerEvent(new UserLoginEvent(id, phone.getValue(), role.getCode()));
}
```

`onRegistered()` / `onLogin()` 作为独立方法被外部调用，而非在聚合根内部状态变更时自动触发。这要求调用方记住调用顺序：`save()` → `onRegistered()`，存在遗忘风险。

**建议**: 将事件注册逻辑内聚到 `User.create()` 工厂方法中，或由领域服务统一在注册成功后调用 `save()` 发布。

---

#### 3.4 spare of unused imports in AddressApplicationService interface

**文件**: [AddressApplicationService.java](file:///e:/Projects/Java/snackstore-cloud/user-service/src/main/java/com/lawyus/snackstore/user/application/service/AddressApplicationService.java)

```java
import com.lawyus.snackstore.common.response.PageResult;       // 未使用
import com.lawyus.snackstore.user.domain.address.model.entity.Address;        // 未使用
import com.lawyus.snackstore.user.domain.address.model.valueobject.AddressDetail;  // 未使用
import com.lawyus.snackstore.user.domain.address.model.valueobject.ReceiverInfo;   // 未使用
import com.lawyus.snackstore.user.domain.address.service.AddressManagementDomainService; // 未使用
import com.lawyus.snackstore.user.application.converter.AddressViewConverter;   // 未使用
```

**上一轮已修复 UserApplicationService 的问题，遗漏了 AddressApplicationService。**

---

### 🟢 P2 — 建议优化

#### 3.5 DomainConfig 隐性依赖顺序

**文件**: [DomainConfig.java](file:///e:/Projects/Java/snackstore-cloud/user-service/src/main/java/com/lawyus/snackstore/user/config/DomainConfig.java#L15-L24)

```java
@Bean
public UserAuthenticationDomainService userAuthenticationDomainService(UserRepository userRepository) {
    return new UserAuthenticationDomainService(userRepository);
}
```

`UserAuthenticationDomainService` 需要 `PasswordEncoder`（在 `register()` 方法参数中传入），但 Spring Bean 声明中未显式声明此依赖。当前通过 ApplicationService 传递 `passwordEncoder`，导致领域服务在 Spring 层面的依赖声明不全。

**建议**: 将 `PasswordEncoder` 注入到 `UserAuthenticationDomainService` 构造器中，简化应用层代码。

```java
@Bean
public UserAuthenticationDomainService userAuthenticationDomainService(
        UserRepository userRepository, PasswordEncoder passwordEncoder) {
    return new UserAuthenticationDomainService(userRepository, passwordEncoder);
}
```

---

#### 3.6 领域服务承担了编排职责

**文件**: [UserManagementDomainService.java](file:///e:/Projects/Java/snackstore-cloud/user-service/src/main/java/com/lawyus/snackstore/user/domain/user/service/UserManagementDomainService.java)

```java
public User updateUser(Long id, String nickname, String avatar, Phone phone) {
    User user = getUserById(id);                     // 查找
    user.updateProfile(nickname, avatar, phone);      // 状态变更
    User saved = userRepository.save(user);           // 持久化
    eventPublisher.publishAll(saved.getDomainEvents());  // 事件发布
    saved.clearDomainEvents();
    return saved;
}
```

此方法严格来说是 **应用服务编排**（查→改→存→发事件），而非纯领域逻辑。领域服务通常不应承担事务编排和事件发布责任。

---

#### 3.7 ValueObject 未做 `@EqualsAndHashCode` 一致性检查

| 值对象 | 当前状态 |
|--------|---------|
| `Phone` | ✅ `@EqualsAndHashCode` |
| `Password` | ✅ `@EqualsAndHashCode` |
| `ReceiverInfo` | ✅ `@EqualsAndHashCode` |
| `AddressDetail` | ❌ 仅有 `@Getter`，无 `@EqualsAndHashCode` |

`AddressDetail` 缺少值对象的关键标记 —— 值对象应当具备相等性比较。

---

#### 3.8 分页查询缺少排序策略

**文件**: [PageSpecification.java](file:///e:/Projects/Java/snackstore-cloud/user-service/src/main/java/com/lawyus/snackstore/user/domain/common/model/PageSpecification.java)

当前 `PageSpecification` 仅包含 `pageNum` 和 `pageSize`，排序逻辑硬编码在 `UserRepositoryImpl.findAll()` 中：
```java
wrapper.orderByDesc(UserDO::getCreatedAt);
```

对于更复杂的查询场景（如按更新时间排序、多字段排序），`PageSpecification` 无法支撑。

---

## 四、审查结果矩阵

| # | 问题 | 优先级 | 状态 |
|---|------|:------:|:----:|
| 1 | UserRegisteredEvent/UserLoginEvent 从未被发布 | 🔴 P0 | 待修复 |
| 2 | 事件发布职责双重（Repository + DomainService 都在发布） | 🔴 P0 | 待修复 |
| 3 | onRegistered()/onLogin() 时间耦合 | 🟡 P1 | 待优化 |
| 4 | AddressApplicationService 接口未使用 import | 🟡 P1 | 待修复 |
| 5 | UserAuthenticationDomainService 未注入 PasswordEncoder | 🟢 P2 | 建议 |
| 6 | 领域服务承担编排职责 | 🟢 P2 | 建议 |
| 7 | AddressDetail 缺少 @EqualsAndHashCode | 🟢 P2 | 建议 |
| 8 | PageSpecification 缺少排序策略 | 🟢 P2 | 建议 |
| 9 | DomainConfig 中 PasswordEncoder 隐性依赖 | 🟢 P2 | 建议 |

---

## 五、架构合规性检查

| 检查项 | 结果 |
|--------|:----:|
| Controller 无业务逻辑 | ✅ |
| 构造器注入 | ✅ |
| 依赖倒置（Repository 接口在 domain） | ✅ |
| 端口/适配器模式（Port Interface） | ✅ |
| 值对象不可变 | ✅ |
| 聚合根通过工厂方法创建 | ✅ |
| DO/DTO/VO 严格分离 | ✅ |
| 领域层零 Spring 依赖 | ✅ |
| 领域层零基础设施依赖 | ✅ |
| Flyway 数据库迁移 | ✅ |
| 统一返回 Result<T> | ✅ |
| JSR380 校验注解 | ✅ |
| 禁止 @Autowired field 注入 | ✅ |
| 业务异常统一使用 BusinessException | ✅ |
| 禁止 Controller 操作 DB | ✅ |

---

## 六、改进建议优先级

### 立即修复（P0）
1. 修复 `UserRegisteredEvent` 和 `UserLoginEvent` 发布链路
2. 统一事件发布职责（仅仓储或仅领域服务）

### 短期优化（P1）
3. 清理 `AddressApplicationService` 接口未使用的 import
4. 将 `onRegistered()`/`onLogin()` 内聚到聚合根或领域服务中

### 持续改进（P2）
5. `PasswordEncoder` 注入到 `UserAuthenticationDomainService` 构造器
6. `AddressDetail` 添加 `@EqualsAndHashCode`
7. `AddressUpdateCommand` 评估是否需要 `isDefault` 字段