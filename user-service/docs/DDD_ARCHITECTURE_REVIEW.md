# User Service DDD 架构全面审视与评估报告

## 总体评价

当前 `user-service` 在 DDD 分层结构上做了不错的尝试——四层架构清晰、值对象有自验证、聚合根有工厂方法、仓储接口与实现分离。但深入审视后，**在多个核心维度上存在"形似而神不似"的问题**：领域事件是死代码、领域服务无法被 Spring 容器管理、应用层泄漏了基础设施关注点、聚合根缺乏真正的不变性保护。以下逐维度详细分析。

---

## 一、领域模型设计

### 1.1 实体与值对象划分

**✅ 做得好的部分：**
- `Phone` 和 `Password` 作为值对象封装了自验证逻辑
- `ReceiverInfo` 和 `AddressDetail` 正确地将概念上的整体建模为值对象
- 值对象使用 `final` 字段 + 私有构造函数 + 静态工厂方法，符合不可变原则

**❌ 关键问题：**

#### 问题 1：Password 值对象泄漏了基础设施关注点

```java
// Password.java 第11行
private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
```

`Password.java:11` 中直接依赖了 `BCryptPasswordEncoder`，这是一个 **Spring Security 基础设施组件**。领域层不应依赖任何框架，这违反了 DDD 的核心原则——领域层应该是纯粹的业务逻辑，不依赖任何技术框架。

**改进建议：** 引入 `PasswordEncoder` 接口（定义在领域层），由基础设施层提供实现：

```java
// domain层定义接口
public interface PasswordEncoder {
    String encode(String rawPassword);
    boolean matches(String rawPassword, String encodedPassword);
}

// Password值对象改为接收接口
public class Password {
    private final String encodedValue;
    
    public static Password fromRaw(String rawValue, PasswordEncoder encoder) {
        return new Password(encoder.encode(rawValue));
    }
    
    public boolean matches(String rawPassword, PasswordEncoder encoder) {
        return encoder.matches(rawPassword, encodedPassword);
    }
}
```

#### 问题 2：ReceiverInfo 的 phone 字段是 String 而非复用 Phone 值对象

```java
// ReceiverInfo.java 第11行
private final String phone;
```

`ReceiverInfo.java:11` 中 `phone` 是 `String` 类型，手动重复了手机号校验逻辑。而 `Phone` 值对象已经封装了手机号格式校验，应直接复用：

```java
public class ReceiverInfo {
    private final String name;
    private final Phone phone;  // 复用值对象
    
    private ReceiverInfo(String name, Phone phone) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("收货人姓名不能为空");
        }
        this.name = name;
        this.phone = phone;  // Phone自身已包含校验
    }
}
```

#### 问题 3：User 实体字段可变，缺乏真正的不变性保护

```java
// User.java
@Getter
public class User {
    private Long id;          // 非final
    private Phone phone;      // 非final
    private Password password; // 非final
    // ...
}
```

`User.java` 的所有字段都是非 `final` 的，且 `@Getter` 暴露了所有字段的 getter（包括 `password`）。聚合根应该：
- **身份标识字段** (`id`) 应该是 `final` 的
- **敏感字段** (`password`) 不应暴露 getter
- 状态变更应通过行为方法，而非 setter

#### 问题 4：User 实体的 `generateDefaultNickname` 方法存在健壮性问题

```java
// User.java 第87-90行
private static String generateDefaultNickname(Phone phone) {
    String phoneValue = phone.getValue();
    return "用户" + phoneValue.substring(7);  // 硬编码偏移量
}
```

`User.java:87-90` 中 `substring(7)` 假设手机号固定11位，但缺乏防御性校验。虽然 Phone 值对象已校验格式，但作为领域规则，应更显式地表达意图。

### 1.2 聚合根边界

**✅ 做得好的部分：**
- User 和 Address 各自作为独立聚合根，边界清晰
- 通过工厂方法控制创建过程

**❌ 关键问题：**

#### 问题 5：Address 聚合根通过 `userId` 直接引用 User，形成跨聚合的强耦合

```java
// Address.java 第13行
private Long userId;
```

`Address.java:13` 中 `userId` 是一个裸 `Long` 类型，直接引用了 User 聚合根的身份标识。在 DDD 中，聚合之间应通过 **身份标识引用** 而非直接持有外键值，且应使用类型化的标识以避免混淆：

```java
// 定义类型化的聚合标识
public record UserId(Long value) {
    public UserId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("用户ID无效");
        }
    }
}

// Address中使用
private UserId userId;
```

---

## 二、领域层与应用层分离

### 2.1 领域服务与应用服务的职责边界

**✅ 做得好的部分：**
- `UserAuthenticationDomainService` 封装了注册/登录的核心业务逻辑
- `AddressManagementDomainService` 封装了地址管理的业务逻辑

**❌ 关键问题：**

#### 问题 6（🔴 严重）：领域服务未被 Spring 容器管理，应用无法启动

`UserAuthenticationDomainService` 和 `AddressManagementDomainService` 均无 `@Component`/`@Service` 注解，也没有任何 `@Configuration` 类注册 Bean。但 `UserApplicationServiceImpl` 通过构造器注入了 `UserAuthenticationDomainService`，这会导致 **`NoSuchBeanDefinitionException`** 运行时异常。

**改进建议：** 创建领域配置类，手动注册领域服务 Bean：

```java
@Configuration
public class DomainConfig {
    
    @Bean
    public UserAuthenticationDomainService userAuthenticationDomainService(UserRepository userRepository) {
        return new UserAuthenticationDomainService(userRepository);
    }
    
    @Bean
    public AddressManagementDomainService addressManagementDomainService(AddressRepository addressRepository) {
        return new AddressManagementDomainService(addressRepository);
    }
}
```

> 注意：不在领域服务上加 `@Service` 是正确的——领域层不应依赖 Spring 框架。通过配置类注册是更好的方式。

#### 问题 7：UserApplicationServiceImpl 绕过领域服务直接操作仓储

```java
// UserApplicationServiceImpl.java 第73-77行
public UserViewVO getUserById(Long id) {
    User user = userRepository.findById(id)
            .orElseThrow(() -> BusinessExceptionEnum.USER_NOT_FOUND.getException());
    return UserViewConverter.toViewVO(user);
}

// 第81-89行
public UserViewVO updateUser(Long id, UserUpdateCommand command) {
    User user = userRepository.findById(id)
            .orElseThrow(() -> BusinessExceptionEnum.USER_NOT_FOUND.getException());
    Phone phone = command.getPhone() != null ? Phone.of(command.getPhone()) : null;
    user.updateProfile(command.getNickname(), command.getAvatar(), phone);
    userRepository.save(user);
    return UserViewConverter.toViewVO(user);
}
```

`UserApplicationServiceImpl.java:73-89` 中 `getUserById`、`updateUser`、`updateUserStatus` 等方法直接操作 `UserRepository`，绕过了领域服务。这导致：
- **业务逻辑分散**：用户查询、状态变更等逻辑没有统一的领域服务入口
- **违反应用层职责**：应用层应协调领域服务，而非直接操作聚合根和仓储

**改进建议：** 扩展 `UserDomainService`（或创建 `UserManagementDomainService`）来封装用户管理逻辑：

```java
public class UserManagementDomainService {
    private final UserRepository userRepository;
    
    public User getUserById(Long id) { ... }
    public User updateUser(Long id, String nickname, String avatar, Phone phone) { ... }
    public User changeStatus(Long id, UserStatus status) { ... }
}
```

#### 问题 8（🔴 严重）：应用层泄漏了基础设施关注点

```java
// UserApplicationServiceImpl.java 第48-49行
String codeKey = SMS_CODE_PREFIX + command.getPhone();
String cachedCode = redisTemplate.opsForValue().get(codeKey);

// 第54行
redisTemplate.delete(codeKey);

// 第116-117行
String token = JwtUtil.generateToken(user.getId(), user.getPhone().getValue(), user.getRole().getCode());
redisTemplate.opsForValue().set("token:" + user.getId(), token, 2, TimeUnit.HOURS);
```

`UserApplicationServiceImpl.java:48-54` 和 `第116-117行` 中，应用服务直接操作 `StringRedisTemplate` 和 `JwtUtil`，这是 **基础设施关注点泄漏**。应用层应只协调领域逻辑和基础设施服务，不应直接操作 Redis。

**改进建议：** 抽取端口接口（Port Interface），由基础设施层实现：

```java
// 领域层定义端口接口
public interface VerificationCodePort {
    String get(String phone);
    void invalidate(String phone);
}

public interface TokenPort {
    String generate(Long userId, String phone, String role);
    void store(Long userId, String token);
}
```

#### 问题 9：领域服务中 SMS 验证码校验逻辑的归属不当

```java
// UserAuthenticationDomainService.java 第56-59行
private void validateSmsCode(String inputCode, String cachedCode) {
    if (cachedCode == null || !cachedCode.equals(inputCode)) {
        throw BusinessExceptionEnum.SMS_CODE_ERROR.getException();
    }
}
```

`UserAuthenticationDomainService.java:56-59` 中 `validateSmsCode` 方法接收的是 `String cachedCode`（从 Redis 获取的原始值），这意味着领域服务知道验证码是从缓存中获取的。验证码的**获取**是基础设施关注点，但验证码的**校验规则**是领域逻辑。当前设计将两者混合了。

**改进建议：** 应用层通过端口接口获取验证码，领域服务只负责校验规则：

```java
// 应用层
String cachedCode = verificationCodePort.get(phone.getValue());
userAuthenticationDomainService.register(phone, rawPassword, smsCode, cachedCode);

// 领域服务 - 只关心校验规则
public User register(Phone phone, String rawPassword, String smsCode, String expectedCode) {
    if (expectedCode == null || !expectedCode.equals(smsCode)) {
        throw BusinessExceptionEnum.SMS_CODE_ERROR.getException();
    }
    // ...
}
```

---

## 三、限界上下文划分

**✅ 做得好的部分：**
- 将 User 和 Address 分为两个子上下文，包结构清晰

**❌ 关键问题：**

#### 问题 10：User 和 Address 的上下文边界模糊，缺乏真正的上下文隔离

当前 User 上下文和 Address 上下文：
- **共享同一个数据库**（`snackstore_user`）
- **共享同一个 Spring 应用**
- **Address 直接持有 `userId`（Long 类型）引用 User**
- **没有上下文映射（Context Map）定义**
- **没有防腐层（Anti-Corruption Layer）**

在 DDD 中，限界上下文的核心价值是 **概念边界的明确性**。当前实现中，Address 上下文中的 `userId` 只是一个数据库外键概念，而非领域概念。如果未来需要跨服务拆分，这种耦合会成为障碍。

**改进建议：**
1. 引入类型化的 `UserId` 值对象，替代裸 `Long`
2. 如果 User 和 Address 确实属于同一限界上下文（用户管理上下文），则应重新考虑聚合边界——Address 可能是 User 聚合内的实体，而非独立聚合根
3. 如果坚持两个独立上下文，则需要定义上下文映射关系（如 Customer-Supplier 或 Conformist）

#### 问题 11：Address 是否应该是独立聚合根值得商榷

在电商场景中，收货地址通常与用户紧密关联——地址的生命周期完全依赖于用户。当前将 Address 作为独立聚合根，但：
- Address 的所有操作都需要 `userId` 参数
- Address 无法独立于 User 存在
- 删除用户时，地址也应被删除

这暗示 Address 更适合作为 **User 聚合内的实体**，而非独立聚合根。

**改进建议（两种方案）：**

**方案 A：Address 作为 User 聚合内实体（推荐）**
```
User (聚合根)
├── Phone (值对象)
├── Password (值对象)
├── addresses: List<Address> (实体集合)
│   ├── ReceiverInfo (值对象)
│   └── AddressDetail (值对象)
├── UserRole (值对象)
└── UserStatus (值对象)
```

**方案 B：保持独立聚合根，但引入领域事件解耦**
- User 删除时发布 `UserDeletedEvent`
- Address 上下文订阅该事件，执行级联清理

---

## 四、领域事件实现

**❌ 这是最严重的缺陷之一——领域事件完全无效**

#### 问题 12（🔴 严重）：领域事件已定义但从未发布，是死代码

`UserRegisteredEvent`、`UserLoginEvent`、`AddressCreatedEvent` 这三个事件类已定义，但在整个代码库中 **没有任何地方发布它们**。这违反了 DDD 中领域事件的核心价值——通过事件实现领域模型的解耦和副作用传播。

#### 问题 13：没有事件发布机制

当前缺少：
- 事件发布器/事件总线接口（定义在领域层）
- 事件发布器实现（如基于 Spring ApplicationEventPublisher）
- 聚合根内的事件收集机制

**改进建议：**

**步骤 1：定义领域事件基类和事件发布器接口**

```java
// 领域层
public abstract class BaseDomainEvent {
    private final LocalDateTime occurredAt;
    private final String eventId;
    
    protected BaseDomainEvent() {
        this.occurredAt = LocalDateTime.now();
        this.eventId = UUID.randomUUID().toString();
    }
}

public interface DomainEventPublisher {
    void publish(BaseDomainEvent event);
    void publishAll(List<BaseDomainEvent> events);
}
```

**步骤 2：聚合根内收集事件**

```java
public abstract class AggregateRoot {
    private final List<BaseDomainEvent> domainEvents = new ArrayList<>();
    
    protected void registerEvent(BaseDomainEvent event) {
        domainEvents.add(event);
    }
    
    public List<BaseDomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }
    
    public void clearDomainEvents() {
        domainEvents.clear();
    }
}
```

**步骤 3：聚合根在状态变更时注册事件**

```java
public class User extends AggregateRoot {
    public static User create(Phone phone, Password password, UserRole role) {
        // ...创建逻辑
        user.registerEvent(new UserRegisteredEvent(user.id, phone.getValue(), user.nickname));
        return user;
    }
}
```

**步骤 4：仓储实现中发布事件**

```java
@Repository
public class UserRepositoryImpl implements UserRepository {
    
    private final UserMapper userMapper;
    private final DomainEventPublisher eventPublisher;
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public User save(User user) {
        UserDO userDO = UserConverter.toDO(user);
        if (userDO.getId() == null) {
            userMapper.insert(userDO);
        } else {
            userMapper.updateById(userDO);
        }
        User saved = UserConverter.toDomain(userDO);
        eventPublisher.publishAll(user.getDomainEvents());
        user.clearDomainEvents();
        return saved;
    }
}
```

**步骤 5：基础设施层实现事件发布**

```java
@Component
public class SpringDomainEventPublisher implements DomainEventPublisher {
    
    private final ApplicationEventPublisher applicationEventPublisher;
    
    @Override
    public void publish(BaseDomainEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
```

---

## 五、仓储模式应用

**✅ 做得好的部分：**
- 仓储接口定义在领域层，实现在基础设施层，符合依赖倒置原则
- 仓储面向聚合根操作，而非直接暴露 Mapper
- `UserConverter` 和 `AddressConverter` 正确隔离了领域模型与数据模型

**❌ 关键问题：**

#### 问题 14：UserRepository 接口方法签名泄漏了数据访问细节

```java
// UserRepository.java 第19行
List<User> findAll(int pageNum, int pageSize, String orderBy);
```

`UserRepository.java:19` 中 `findAll` 方法接收 `pageNum`、`pageSize`、`orderBy` 参数，这是 **数据访问关注点泄漏到领域层**。领域层的仓储接口应表达领域意图，而非分页技术细节。

**改进建议：** 引入领域级的分页规格对象：

```java
// 领域层
public record PageSpecification(int pageNum, int pageSize) {
    public PageSpecification {
        if (pageNum < 1) pageNum = 1;
        if (pageSize < 1 || pageSize > 100) pageSize = 10;
    }
}

public record PagedResult<T>(List<T> content, long total, int pageNum, int pageSize) {}

// 仓储接口
public interface UserRepository {
    PagedResult<User> findAll(PageSpecification spec);
}
```

#### 问题 15：仓储 save() 方法返回新对象，可能破坏聚合根身份

```java
// UserRepositoryImpl.java 第30-38行
public User save(User user) {
    UserDO userDO = UserConverter.toDO(user);
    if (userDO.getId() == null) {
        userMapper.insert(userDO);
    } else {
        userMapper.updateById(userDO);
    }
    return UserConverter.toDomain(userDO);  // 返回的是从DO转换的新对象
}
```

`UserRepositoryImpl.java:30-38` 中 `save` 方法返回了一个从 DO 重新转换的 User 对象。这有两个问题：
1. **身份断裂**：新对象与传入对象不是同一个引用，可能导致调用方持有过期状态
2. **ID 回填问题**：新增时 MyBatis-Plus 会回填 ID 到 DO，但调用方持有的原始 User 对象没有 ID

**改进建议：** 新增时回填 ID 到原始对象：

```java
@Override
@Transactional(rollbackFor = Exception.class)
public User save(User user) {
    UserDO userDO = UserConverter.toDO(user);
    if (userDO.getId() == null) {
        userMapper.insert(userDO);
        // 回填ID到领域对象
        ReflectUtil.setFieldValue(user, "id", userDO.getId());
    } else {
        userMapper.updateById(userDO);
    }
    return user;
}
```

#### 问题 16：AddressRepository.clearDefaultAddress 方法包含业务语义

```java
// AddressRepository.java 第18行
void clearDefaultAddress(Long userId);
```

`AddressRepository.java:18` 中 `clearDefaultAddress` 是一个具有业务语义的方法名。仓储接口应只提供通用的数据访问能力，业务逻辑应在领域服务中编排。

**改进建议：** 仓储提供通用方法，业务逻辑在领域服务中编排：

```java
// 仓储接口 - 只提供通用数据操作
public interface AddressRepository {
    List<Address> findDefaultByUserId(Long userId);
    void updateDefaultFlag(Long userId, boolean isDefault);
    // ...
}

// 领域服务 - 编排业务逻辑
public Address setDefaultAddress(Long addressId, Long userId) {
    List<Address> defaults = addressRepository.findDefaultByUserId(userId);
    defaults.forEach(Address::unsetDefault);
    // ...
}
```

---

## 六、DDD 战术模式应用

#### 问题 17：缺少 Factory 模式的独立实现

当前工厂逻辑以静态方法形式嵌入在聚合根中（`User.create()`、`Address.create()`）。对于简单场景这是可接受的，但当创建逻辑变得复杂时（如需要依赖注入、需要查询其他聚合），静态工厂方法会力不从心。

**改进建议：** 当前阶段保持静态工厂方法即可，但如果未来创建逻辑复杂化（如注册时需要初始化积分账户、发送欢迎消息等），应考虑引入独立的 Factory 类。

#### 问题 18：缺少 Specification 模式

当前查询逻辑直接散落在仓储接口中（如 `existsByPhone`、`findByIdAndUserId`），缺乏统一的查询规格抽象。当查询条件复杂化时，会导致仓储接口膨胀。

**改进建议：** 引入 Specification 模式（当前阶段非紧急）：

```java
public interface Specification<T> {
    boolean isSatisfiedBy(T candidate);
}

public class PhoneExistsSpecification implements Specification<User> {
    private final Phone phone;
    private final UserRepository userRepository;
    
    @Override
    public boolean isSatisfiedBy(User candidate) {
        return userRepository.existsByPhone(phone);
    }
}
```

#### 问题 19：缺少聚合版本号（乐观锁）

当前聚合根没有版本号字段，在并发场景下可能导致数据不一致。例如，两个请求同时设置不同的默认地址，可能导致多个默认地址。

**改进建议：** 在聚合根基类中添加版本号：

```java
public abstract class AggregateRoot {
    private Long version;
    // ...
}
```

对应 DO 中添加 `version` 字段，使用 MyBatis-Plus 的 `@Version` 注解。

---

## 七、业务规则表达

#### 问题 20：用户状态变更缺乏业务规则约束

```java
// User.java 第71-77行
public void changeStatus(UserStatus newStatus) {
    if (newStatus == null) {
        throw new IllegalArgumentException("用户状态不能为空");
    }
    this.status = newStatus;
    this.updatedAt = LocalDateTime.now();
}
```

`User.java:71-77` 中 `changeStatus` 方法仅做了空值校验，缺乏业务规则约束，例如：
- 禁用管理员账号是否需要特殊审批？
- 已禁用的用户能否再次禁用？
- 状态转换是否有合法路径（如 ENABLED → DISABLED → ENABLED）？

**改进建议：** 在聚合根内封装状态转换规则：

```java
public void disable() {
    if (this.status == UserStatus.DISABLED) {
        throw new BusinessException(BusinessExceptionEnum.USER_ALREADY_DISABLED);
    }
    this.status = UserStatus.DISABLED;
    this.updatedAt = LocalDateTime.now();
}

public void enable() {
    if (this.status == UserStatus.ENABLED) {
        throw new BusinessException(BusinessExceptionEnum.USER_ALREADY_ENABLED);
    }
    this.status = UserStatus.ENABLED;
    this.updatedAt = LocalDateTime.now();
}
```

#### 问题 21：默认地址逻辑缺乏并发保护

`AddressManagementDomainService.java:22-29` 中 `createAddress` 方法的默认地址处理逻辑：

```java
if (isDefault) {
    addressRepository.clearDefaultAddress(userId);  // 先清除
}
Address address = Address.create(userId, receiverInfo, addressDetail, isDefault);  // 再创建
return addressRepository.save(address);
```

在并发场景下，两个请求可能同时执行 `clearDefaultAddress`，导致最终两个地址都是默认的。需要通过乐观锁或分布式锁来保护。

#### 问题 22：用户注册时手机号唯一性校验与保存之间存在竞态条件

`UserAuthenticationDomainService.java:62-66`：

```java
private void checkPhoneNotExists(Phone phone) {
    if (userRepository.existsByPhone(phone)) {
        throw BusinessExceptionEnum.PHONE_ALREADY_EXISTS.getException();
    }
}
```

在 `checkPhoneNotExists` 和 `userRepository.save()` 之间存在时间窗口，两个并发注册请求可能同时通过校验。虽然数据库唯一索引 (`uk_phone`) 可以兜底，但领域层应更优雅地处理此场景——捕获数据库唯一约束异常并转换为业务异常。

---

## 八、其他重要问题

#### 问题 23：应用服务接口中导入了不必要的类

```java
// UserApplicationService.java 第4-13行
import com.lawyus.snackstore.common.response.PageResult;
import com.lawyus.snackstore.user.application.dto.UserRegisterCommand;
import com.lawyus.snackstore.user.application.vo.LoginViewVO;
import com.lawyus.snackstore.user.application.converter.UserViewConverter;  // 未使用
import com.lawyus.snackstore.user.domain.user.model.entity.User;           // 不应在接口中
import com.lawyus.snackstore.user.domain.user.model.valueobject.Phone;     // 不应在接口中
import com.lawyus.snackstore.user.domain.user.model.valueobject.UserStatus; // 不应在接口中
import com.lawyus.snackstore.user.domain.user.service.UserAuthenticationDomainService; // 不应在接口中
import com.lawyus.snackstore.user.domain.user.repository.UserRepository;   // 不应在接口中
```

`UserApplicationService.java` 接口中导入了领域层的类，但这些类在接口方法签名中并未使用，说明这些导入是从实现类中误留下的。

#### 问题 24：AddressApplicationServiceImpl.updateAddress 硬编码 isDefault 为 null

```java
// AddressApplicationServiceImpl.java 第75行
Address address = addressManagementDomainService.updateAddress(id, userId, receiverInfo, addressDetail, null);
```

`AddressApplicationServiceImpl.java:75` 中 `isDefault` 硬编码为 `null`，但 `AddressUpdateCommand` 中并没有 `isDefault` 字段。这意味着用户无法在更新地址时修改默认状态，只能通过单独的 `setDefaultAddress` 接口。这可能是设计意图，但应在 Command 中显式表达。

---

## 九、改进优先级总结

| 优先级 | 问题编号 | 问题描述 | 影响范围 |
|--------|---------|---------|---------|
| 🔴 P0 | #6 | 领域服务未注册为 Spring Bean，应用无法启动 | 运行时致命 |
| 🔴 P0 | #8 | 应用层直接操作 Redis，基础设施泄漏 | 架构违规 |
| 🔴 P0 | #12 | 领域事件是死代码，未发布未消费 | DDD核心缺失 |
| 🟡 P1 | #1 | Password 值对象依赖 Spring Security | 领域层污染 |
| 🟡 P1 | #7 | 应用服务绕过领域服务直接操作仓储 | 职责混乱 |
| 🟡 P1 | #14 | 仓储接口泄漏分页细节 | 领域层污染 |
| 🟡 P1 | #15 | 仓储 save() 返回新对象，身份断裂 | 数据一致性 |
| 🟡 P1 | #20 | 状态变更缺乏业务规则约束 | 业务逻辑缺失 |
| 🟡 P1 | #21 | 默认地址逻辑缺乏并发保护 | 数据一致性 |
| 🟢 P2 | #2 | ReceiverInfo 未复用 Phone 值对象 | 代码重复 |
| 🟢 P2 | #3 | User 实体字段可变，Password 暴露 getter | 不变性违反 |
| 🟢 P2 | #5 | Address 使用裸 Long 引用 User | 类型安全 |
| 🟢 P2 | #10-11 | 限界上下文边界模糊 | 架构演进性 |
| 🟢 P2 | #13 | 缺少事件发布机制 | DDD完整性 |
| 🟢 P2 | #16 | 仓储方法包含业务语义 | 职责混淆 |
| 🟢 P2 | #19 | 缺少聚合版本号 | 并发安全 |
| 🟢 P3 | #4,9,17,18,22,23,24 | 其他代码质量问题 | 代码质量 |

---

## 十、整体架构改进路线图

### 第一阶段（紧急修复）：
1. 创建 `DomainConfig` 配置类注册领域服务 Bean
2. 抽取 `VerificationCodePort` 和 `TokenPort` 接口，将 Redis 操作从应用层移至基础设施层
3. 实现领域事件发布机制（AggregateRoot 基类 + DomainEventPublisher + Spring 集成）

### 第二阶段（领域模型强化）：
1. 将 `BCryptPasswordEncoder` 从 Password 值对象中解耦
2. 扩展 `UserManagementDomainService`，将用户管理逻辑从应用层下沉
3. 引入 `PageSpecification` 替代仓储中的分页参数
4. 修复仓储 `save()` 方法的身份回填问题

### 第三阶段（架构完善）：
1. 引入 `UserId` 类型化标识
2. 重新评估 Address 聚合根边界
3. 添加聚合版本号（乐观锁）
4. 完善业务规则约束（状态转换、并发保护）
5. ReceiverInfo 复用 Phone 值对象
