package com.lawyus.snackstore.user.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lawyus.snackstore.user.infrastructure.persistence.do_.AddressDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AddressMapper extends BaseMapper<AddressDO> {
}
