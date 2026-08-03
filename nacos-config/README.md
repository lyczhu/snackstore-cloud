# Nacos 配置备份

本目录为各微服务 Nacos 配置中心配置的**本地备份**，方便查看和维护配置内容。

> 实际运行时，各服务通过 `spring.config.import` 从 Nacos 配置中心加载配置，本目录中的文件**不会被应用直接读取**。

## 文件说明

| 文件 | 对应服务 | 说明                                               |
|------|---------|--------------------------------------------------|
| `application.yml` | 全局共享 | 所有服务共享的公共配置（MyBatis-Plus、SpringDoc、Actuator 等）   |
| `api-gateway.yml` | api-gateway | API 网关配置（端口 8080、路由规则、Sentinel、JWT）              |
| `user-service.yml` | user-service | 用户服务配置（端口 8081、数据源、Redis、Flyway、JWT）             |
| `product-service.yml` | product-service | 商品服务配置（端口 8082、数据源、Redis、Kafka、Flyway）           |
| `order-service.yml` | order-service | 订单服务配置（端口 8083、数据源、Redis、Kafka、OpenFeign、Flyway） |
| `product-search-service.yml` | product-search-service | 商品搜索服务配置（端口 8085、Elasticsearch、Kafka、OpenFeign）  |
| `statistics-service.yml` | statistics-service | 统计服务配置（端口 8087、Redis、OpenFeign、Sentinel）         |
| `sentinel/api-gateway-flow-rules.json` | api-gateway | 网关流控规则（data-id: `api-gateway-flow-rules`，group: `SENTINEL_GROUP`，namespace: `public`，rule-type: gw-flow） |
| `sentinel/api-gateway-degrade-rules.json` | api-gateway | 网关熔断规则（data-id: `api-gateway-degrade-rules`，group: `SENTINEL_GROUP`，namespace: `public`，rule-type: degrade） |

## 配置加载链路

各服务 classpath 下的 `application.yml` 仅保留连接 Nacos 的必要信息，配置加载顺序为：

1. 本地 `src/main/resources/application.yml` — Nacos 连接信息
2. Nacos `application.yml` — 全局共享配置
3. Nacos `{服务名}.yml` — 服务专属配置
4. Nacos `{服务名}-{profile}.yml` — 环境专属配置（优先级最高）

## 注意事项

- 修改配置时，请同步更新 **Nacos 配置中心** 和 **本目录备份**
- 敏感信息（密码、密钥等）使用环境变量占位符 `${ENV_VAR:默认值}` 格式，避免硬编码
- 环境变量：`NACOS_SERVER_ADDR`、`NACOS_USERNAME`、`NACOS_PASSWORD`、`NACOS_NAMESPACE`、`SPRING_PROFILES_ACTIVE` 等
- Sentinel 规则文件（如 `api-gateway-flow-rules.json`）需手动创建到 Nacos 对应 data-id（namespace `public`、group `SENTINEL_GROUP`），仓库文件仅为备份/基线
