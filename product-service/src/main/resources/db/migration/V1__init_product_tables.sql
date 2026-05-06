CREATE DATABASE IF NOT EXISTS snackstore_product DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE snackstore_product;

CREATE TABLE IF NOT EXISTS `t_product_category` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    `name` VARCHAR(50) NOT NULL COMMENT '分类名称',
    `sort` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品分类表';

CREATE TABLE IF NOT EXISTS `t_product` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '商品ID',
    `category_id` BIGINT DEFAULT NULL COMMENT '分类ID',
    `name` VARCHAR(200) NOT NULL COMMENT '商品名称',
    `cover_image` VARCHAR(500) DEFAULT NULL COMMENT '封面图片URL',
    `price` DECIMAL(10,2) NOT NULL COMMENT '价格',
    `stock` INT NOT NULL DEFAULT 0 COMMENT '库存',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '简介',
    `detail` TEXT DEFAULT NULL COMMENT '详情',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-下架 1-上架',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表';

INSERT INTO `t_product_category` (`name`, `sort`, `status`) VALUES
('坚果炒货', 1, 1),
('肉脯卤味', 2, 1),
('糖果巧克力', 3, 1),
('饼干糕点', 4, 1),
('膨化食品', 5, 1),
('蜜饯果干', 6, 1);

INSERT INTO `t_product` (`category_id`, `name`, `cover_image`, `price`, `stock`, `description`, `status`) VALUES
(1, '每日坚果混合装 750g', '/images/product1.jpg', 59.90, 200, '7种坚果果干混合，营养均衡', 1),
(2, '蜜汁猪肉脯 500g', '/images/product2.jpg', 39.90, 150, '传统蜜汁工艺，鲜香可口', 1),
(3, '比利时巧克力礼盒', '/images/product3.jpg', 128.00, 80, '进口可可豆，丝滑醇香', 1),
(4, '曲奇饼干礼盒 1kg', '/images/product4.jpg', 49.90, 120, '黄油曲奇，酥脆美味', 1),
(5, '薯片大礼包 800g', '/images/product5.jpg', 35.90, 300, '多种口味，快乐分享', 1),
(6, '芒果干 250g', '/images/product6.jpg', 29.90, 180, '新鲜芒果制作，酸甜可口', 1);
