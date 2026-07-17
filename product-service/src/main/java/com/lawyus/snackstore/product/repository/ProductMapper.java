package com.lawyus.snackstore.product.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lawyus.snackstore.product.model.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    @Select("SELECT COUNT(*) FROM t_product WHERE deleted = 0")
    Long countValidProducts();
}
