# Snackstore Cloud Backend

基于 Spring Boot 3 + Spring Cloud 的分布式微服务后端，提供用户、商品、订单等核心业务能力。

## 技术栈

| 技术 | 版本         |
|------|------------|
| Java | 25         |
| Spring Boot | 3.5.15     |
| Spring Cloud | 2025.0.2   |
| Spring Cloud Alibaba | 2025.0.0.0 |
| MyBatis-Plus | 3.5.15     |
| JWT | 0.12.6     |

## 模块架构

```
backend/
├── common/              # 通用工具模块（异常处理、统一响应等）
├── api-gateway/         # API 网关（路由、认证、日志）
├── user-service/        # 用户服务
├── product-service/     # 商品服务
└── order-service/       # 订单服务
```

## 模块说明

### common
通用模块，提供：
- 统一响应结果封装（Result、PageResult）
- 业务异常处理（BusinessException、GlobalExceptionHandler）
- JWT 工具类

### api-gateway
API 网关服务：
- 请求路由转发
- JWT 认证校验
- 跨域配置
- 访问日志记录

### user-service
用户服务：
- 用户注册登录
- 收货地址管理
- 用户认证授权

### product-service
商品服务：
- 商品分类管理
- 商品信息管理
- 商品查询接口

### order-service
订单服务：
- 订单创建与查询
- 订单项管理
- 远程调用用户/商品服务

## 开发环境

### 前置要求
- JDK 25+
- Maven 3.9+
- MySQL 8.0+
- Nacos（服务注册与配置中心）

### 启动顺序
1. 启动 Nacos 服务端
2. 依次启动各微服务模块

### 常用命令

```bash
# 编译项目
mvn clean compile

# 跳过测试打包
mvn clean package -DskipTests

# 单模块打包
mvn clean package -DskipTests -pl module-name -am
```

## 数据库

使用 Flyway 进行数据库版本管理，迁移脚本位于各模块的 `src/main/resources/db/migration/` 目录下。

## API 文档

各服务启动后，访问 Swagger UI：
- API Gateway: http://localhost:9000/swagger-ui.html
