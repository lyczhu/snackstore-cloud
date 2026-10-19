# 管理员账号说明

## 种子管理员

- 手机号：`13800000000`
- 角色：`ADMIN`
- 初始口令（V1 迁移创建，哈希固定且口令不可知，已废弃）

## V2 迁移轮换

`V2__rotate_admin_password.sql` 将种子管理员口令轮换为强口令哈希（24 位，含大小写字母/数字/符号）：

```
默认口令: S@ckstoreAdmin!2026#Xy7q
```

> 该口令为开发/演示用途。**生产环境必须通过环境变量覆盖**（见下节），不要使用仓库中的默认口令。

## 生产环境：环境变量覆盖

user-service 启动时（`AdminPasswordInitializer`）若检测到环境变量 `ADMIN_DEFAULT_PASSWORD`，会将管理员口令同步为该值：

```bash
ADMIN_DEFAULT_PASSWORD=your-strong-password docker compose up -d
```

- 口令从密钥库/CI 密钥注入，不落入仓库；
- 长度建议 ≥ 16 位，长度不足 12 位时启动日志会告警；
- 每次启动都会比对，密码哈希已一致时跳过更新。

## 登录入口

- 管理员登录：`POST /api/auth/admin/login`（网关白名单，需在白名单内）
- 普通用户登录：`POST /api/auth/login`
