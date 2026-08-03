package com.macro.mall.mapper;

import com.macro.mall.model.SmsGroupOrder;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface SmsGroupOrderMapper {
    int insert(SmsGroupOrder row);
    int updateByPrimaryKey(SmsGroupOrder row);
    SmsGroupOrder selectByPrimaryKey(Long id);
    List<SmsGroupOrder> selectByGroupNo(@Param("groupNo") String groupNo);
    List<SmsGroupOrder> selectByMemberId(@Param("memberId") Long memberId);
    List<SmsGroupOrder> selectByGroupBuyId(@Param("groupBuyId") Long groupBuyId);
    int countByGroupBuyIdAndMemberId(@Param("groupBuyId") Long groupBuyId, @Param("memberId") Long memberId);
    int countActiveByGroupNo(@Param("groupNo") String groupNo);
}
