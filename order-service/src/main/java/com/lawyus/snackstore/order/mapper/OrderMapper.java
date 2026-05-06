package com.lawyus.snackstore.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lawyus.snackstore.order.model.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    @Select("SELECT COUNT(*) FROM t_order WHERE DATE(created_at) = CURDATE()")
    Long countTodayOrders();
}
