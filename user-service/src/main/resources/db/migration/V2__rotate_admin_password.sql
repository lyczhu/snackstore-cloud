-- 轮换种子管理员口令：V1 硬编码哈希口令不可知且大概率弱口令，替换为强口令哈希
-- 默认强口令: S@ckstoreAdmin!2026#Xy7q（见 user-service/docs/ADMIN_ACCOUNT.md）
-- 生产环境可在启动时通过环境变量 ADMIN_DEFAULT_PASSWORD 覆盖（见 AdminPasswordInitializer）
USE snackstore_user;

UPDATE `t_user`
SET `password` = '$2a$10$LtlS2zWayu5cironYNpTg.M9MjejK2wKPCFKrc3.fmBzsDFWU77gK'
WHERE `phone` = '13800000000' AND `role` = 'ADMIN' AND `deleted` = 0;
