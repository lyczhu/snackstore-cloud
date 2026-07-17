package com.lawyus.snackstore.product.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lawyus.snackstore.product.model.entity.ProductSnapshot;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductSnapshotMapper extends BaseMapper<ProductSnapshot> {
}
