package com.lawyus.snackstore.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lawyus.snackstore.order.model.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {
}
