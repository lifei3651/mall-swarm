package com.macro.mall.mapper;

import com.macro.mall.model.OmsLogisticsTrace;
import java.util.List;

public interface OmsLogisticsTraceMapper {
    int insert(OmsLogisticsTrace record);

    OmsLogisticsTrace selectByPrimaryKey(Long id);

    List<OmsLogisticsTrace> selectAll();

    int updateByPrimaryKey(OmsLogisticsTrace record);

    int deleteByPrimaryKey(Long id);
}
