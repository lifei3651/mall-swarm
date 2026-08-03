package com.macro.mall.mapper;

import com.macro.mall.model.SmsGroupBuy;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface SmsGroupBuyMapper {
    int insert(SmsGroupBuy row);
    int updateByPrimaryKey(SmsGroupBuy row);
    int deleteByPrimaryKey(Long id);
    SmsGroupBuy selectByPrimaryKey(Long id);
    List<SmsGroupBuy> selectAll();
    List<SmsGroupBuy> selectByStatus(@Param("status") Integer status);
}
