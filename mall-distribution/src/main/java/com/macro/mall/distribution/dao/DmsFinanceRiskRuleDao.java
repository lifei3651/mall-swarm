package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsFinanceRiskRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DmsFinanceRiskRuleDao {

    DmsFinanceRiskRule selectByCode(@Param("ruleCode") String ruleCode);

    List<DmsFinanceRiskRule> selectAll();

    int insert(DmsFinanceRiskRule rule);

    int update(DmsFinanceRiskRule rule);
}
