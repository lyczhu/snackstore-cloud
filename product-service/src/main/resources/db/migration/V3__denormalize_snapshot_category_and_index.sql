USE snackstore_product;

ALTER TABLE `t_product_snapshot`
    ADD COLUMN `category_name` VARCHAR(100) DEFAULT NULL COMMENT '分类名称（冗余，快照时刻冻结）' AFTER `category_id`,
    ADD COLUMN `category_sort` INT DEFAULT NULL COMMENT '分类排序（冗余，快照时刻冻结）' AFTER `category_name`,
    ADD INDEX `idx_product_id_created_at` (`product_id`, `created_at`);

UPDATE `t_product_snapshot` s
INNER JOIN `t_product_category` c ON s.`category_id` = c.`id`
SET s.`category_name` = c.`name`,
    s.`category_sort` = c.`sort`
WHERE s.`category_name` IS NULL;
