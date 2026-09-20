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

    /**
     * 并发初始化默认规则时只允许首个请求写入，后续请求安全跳过。
     */
    int insertIgnore(DmsFinanceRiskRule rule);

    int update(DmsFinanceRiskRule rule);
}
