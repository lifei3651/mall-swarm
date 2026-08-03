package com.macro.mall.mapper;

import com.macro.mall.model.UmsSmsCode;
import org.apache.ibatis.annotations.Param;

public interface UmsSmsCodeMapper {
    int insert(UmsSmsCode row);
    UmsSmsCode selectLatestByPhoneAndBizType(@Param("phone") String phone, @Param("bizType") Integer bizType);
    int updateStatusById(@Param("id") Long id, @Param("status") Integer status);
    int countByPhoneInMinutes(@Param("phone") String phone, @Param("minutes") Integer minutes);
}
