package com.lawyus.snackstore.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lawyus.snackstore.product.model.entity.Product;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}
