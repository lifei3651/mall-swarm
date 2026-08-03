package com.macro.mall.mapper;

import com.macro.mall.model.OmsFinanceFlow;
import java.util.List;

public interface OmsFinanceFlowMapper {
    int insert(OmsFinanceFlow record);

    OmsFinanceFlow selectByPrimaryKey(Long id);

    List<OmsFinanceFlow> selectAll();

    int updateByPrimaryKey(OmsFinanceFlow record);

    int deleteByPrimaryKey(Long id);
}
