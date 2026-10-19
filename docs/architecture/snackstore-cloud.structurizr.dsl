# Snackstore Cloud — 当前态 C4 模型
# 生成方式: system-modeler(场景) + c4model(基础) ；证据与置信度见 snackstore-cloud.evidence.md
# 打开方式: 用 Qoder 的 Structurizr DSL 预览查看；也可导入 Structurizr Lite / Playground
# 范围: 仅 current 状态。短信通道为 unknown（代码中只有 ConsoleSmsSender）
#
# 排版约定（为避免文字与边界重合、内容被裁切）:
# - 视图按问题拆分，单视图节点 <= 11、关系 <= 14，不用 include * 把全量关系塞进一张图
# - 节点 description 控制在 20 字内，连线 label 控制在 12 字内（预览框尺寸固定，长文案会溢出重叠）
# - 字符串内不写花括号与尖括号，避免解析歧义
# - shape 只用官方枚举（本文件仅用 person / cylinder）；不确定项靠 tag + 描述 + 颜色表达
workspace "Snackstore Cloud" "零食电商微服务后端当前态模型" {
    !identifiers hierarchical

    model {
        shopper = person "顾客" "下单与查订单"
        operator = person "管理员" "管商品与看统计"

        snackstore = softwareSystem "Snackstore Cloud" "零食电商微服务后端" {
            gateway = container "api-gateway" "路由与 JWT 鉴权" "Gateway :8080"
            userSvc = container "user-service" "账号/验证码/地址" "Spring Boot :8081"
            productSvc = container "product-service" "商品/分类/库存" "Spring Boot :8082"
            orderSvc = container "order-service" "下单/支付/取消" "Spring Boot :8083"
            searchSvc = container "product-search-service" "商品全文检索" "Spring Boot :8085"
            statsSvc = container "statistics-service" "经营指标聚合" "Spring Boot :8087"

            userDb = container "snackstore_user" "用户与地址库" "MySQL" {
                tags "Database"
            }
            productDb = container "snackstore_product" "商品与库存库" "MySQL" {
                tags "Database"
            }
            orderDb = container "snackstore_order" "订单库" "MySQL" {
                tags "Database"
            }
            redisStore = container "Redis" "会话与缓存" "Redis 8.2" {
                tags "Database"
            }
            esIndex = container "ES product 索引" "商品读模型" "Elasticsearch 9.2" {
                tags "Database"
            }
            kafkaTopic = container "Kafka product-changed" "商品变更事件" "Kafka 4.1" {
                tags "Queue"
            }
        }

        nacos = softwareSystem "Nacos" "注册发现与配置中心" {
            tags "External"
        }
        sentinelDashboard = softwareSystem "Sentinel Dashboard" "流控降级控制台" {
            tags "External"
        }
        smsProvider = softwareSystem "短信通道" "供应商未接入，仅日志打印" {
            tags "External,Unknown"
        }

        # ---------- 使用者 ----------
        shopper -> snackstore "购物"
        operator -> snackstore "运营"

        # ---------- 路由与身份 ----------
        shopper -> snackstore.gateway "HTTPS + JWT"
        operator -> snackstore.gateway "ADMIN 令牌"
        snackstore.gateway -> snackstore.userSvc "order 0"
        snackstore.gateway -> snackstore.searchSvc "order 0"
        snackstore.gateway -> snackstore.productSvc "order 1"
        snackstore.gateway -> snackstore.orderSvc "order 2"
        snackstore.gateway -> snackstore.statsSvc "order 3"
        snackstore.gateway -> snackstore.redisStore "会话校验"
        snackstore.gateway -> nacos "路由热刷新"
        snackstore.gateway -> sentinelDashboard "规则与指标"

        # ---------- 服务间同步调用 ----------
        snackstore.orderSvc -> snackstore.productSvc "取商品与扣库存"
        snackstore.searchSvc -> snackstore.productSvc "重建与降级拉取"
        snackstore.statsSvc -> snackstore.orderSvc "统计接口"
        snackstore.statsSvc -> snackstore.productSvc "商品内部接口"
        snackstore.statsSvc -> snackstore.userSvc "用户数"

        # ---------- 数据所有权 ----------
        snackstore.userSvc -> snackstore.userDb "读写"
        snackstore.orderSvc -> snackstore.orderDb "读写"
        snackstore.productSvc -> snackstore.productDb "读写"
        snackstore.userSvc -> snackstore.redisStore "会话与验证码"
        snackstore.productSvc -> snackstore.redisStore "分类缓存"
        snackstore.statsSvc -> snackstore.redisStore "统计缓存"
        snackstore.searchSvc -> snackstore.esIndex "读写索引"

        # ---------- 异步事件 ----------
        snackstore.productSvc -> snackstore.kafkaTopic "发布事件"
        snackstore.searchSvc -> snackstore.kafkaTopic "订阅事件"

        # ---------- 不确定项 ----------
        snackstore.userSvc -> smsProvider "发验证码 未证实"
    }

    views {
        # V1 只有一个问题：系统边界与外部依赖
        systemContext snackstore "V1_SystemContext" "谁在用这个后端，外部系统在哪" {
            include snackstore
            include shopper
            include operator
            include nacos
            include smsProvider
            include sentinelDashboard
            autoLayout
        }

        # V2 只有一个问题：请求怎么进来、服务之间谁调用谁（不含数据存储）
        container snackstore "V2_ServiceTopology" "网关路由 + 服务间同步调用" {
            include snackstore.gateway
            include snackstore.userSvc
            include snackstore.productSvc
            include snackstore.orderSvc
            include snackstore.searchSvc
            include snackstore.statsSvc
            include shopper
            include operator
            include nacos
            autoLayout
        }

        # V3 只有一个问题：每份数据归谁、事件怎么流向读模型
        container snackstore "V3_DataOwnership" "数据归属与事件流" {
            include snackstore.userSvc
            include snackstore.productSvc
            include snackstore.orderSvc
            include snackstore.searchSvc
            include snackstore.statsSvc
            include snackstore.userDb
            include snackstore.productDb
            include snackstore.orderDb
            include snackstore.redisStore
            include snackstore.esIndex
            include snackstore.kafkaTopic
            autoLayout
        }

        # V4 只有一个问题：身份从网关到下游的信任边界（头注入 + 会话校验）
        container snackstore "V4_TrustBoundary" "身份与信任边界" {
            include snackstore.gateway
            include snackstore.userSvc
            include snackstore.productSvc
            include snackstore.orderSvc
            include snackstore.searchSvc
            include snackstore.statsSvc
            include snackstore.redisStore
            include shopper
            autoLayout
        }

        styles {
            element "Person" {
                shape person
                background #08427b
                color #ffffff
            }
            element "Software System" {
                background #1168bd
                color #ffffff
            }
            element "External" {
                background #999999
                color #ffffff
            }
            element "Unknown" {
                background #BDB76B
                color #1F2937
            }
            element "Database" {
                shape cylinder
                background #2F6B5E
                color #ffffff
            }
            element "Queue" {
                background #8B5CF6
                color #ffffff
            }
        }
    }
}
