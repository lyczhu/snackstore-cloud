USE snackstore_product;

CREATE TABLE IF NOT EXISTS `t_product_snapshot` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '快照ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `category_id` BIGINT DEFAULT NULL COMMENT '分类ID',
    `name` VARCHAR(200) NOT NULL COMMENT '商品名称',
    `cover_image` VARCHAR(500) DEFAULT NULL COMMENT '封面图片URL',
    `price` DECIMAL(10,2) NOT NULL COMMENT '价格',
    `stock` INT NOT NULL DEFAULT 0 COMMENT '库存',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '简介',
    `detail` TEXT DEFAULT NULL COMMENT '详情',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-下架 1-上架',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品快照表';
