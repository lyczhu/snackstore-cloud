package com.lawyus.snackstore.order.mapper;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lawyus.snackstore.order.model.entity.Order;
import com.lawyus.snackstore.order.model.vo.OrderTrendVO;
import com.lawyus.snackstore.order.model.vo.ProductSalesVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    @Select("SELECT DATE(created_at) AS date, COUNT(*) AS orderCount, SUM(total_amount) AS orderAmount "
            + "FROM t_order WHERE created_at >= #{startTime} AND created_at < #{endTime} "
            + "GROUP BY DATE(created_at) ORDER BY date")
    List<OrderTrendVO> selectOrderTrend(@Param("startTime") LocalDateTime startTime,
                                        @Param("endTime") LocalDateTime endTime);

    @Select("SELECT product_id AS productId, product_name AS productName, SUM(quantity) AS quantity, "
            + "SUM(product_price * quantity) AS amount FROM t_order_item "
            + "GROUP BY product_id, product_name ORDER BY quantity DESC LIMIT #{limit}")
    List<ProductSalesVO> selectProductSalesTop(@Param("limit") int limit);
}
