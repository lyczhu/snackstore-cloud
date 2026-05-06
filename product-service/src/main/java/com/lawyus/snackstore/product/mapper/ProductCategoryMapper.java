package com.lawyus.snackstore.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lawyus.snackstore.product.model.entity.ProductCategory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductCategoryMapper extends BaseMapper<ProductCategory> {
}
