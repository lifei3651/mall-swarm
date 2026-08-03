package com.macro.mall.mapper;

import com.macro.mall.model.UmsMember;
import com.macro.mall.model.UmsMemberExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface UmsMemberMapper {
    long countByExample(UmsMemberExample example);

    int deleteByExample(UmsMemberExample example);

    int deleteByPrimaryKey(Long id);

    int insert(UmsMember row);

    int insertSelective(UmsMember row);

    List<UmsMember> selectByExample(UmsMemberExample example);

    UmsMember selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") UmsMember row, @Param("example") UmsMemberExample example);

    int updateByExample(@Param("row") UmsMember row, @Param("example") UmsMemberExample example);

    int updateByPrimaryKeySelective(UmsMember row);

    int updateByPrimaryKey(UmsMember row);

    /**
     * 原子性增加积分（避免并发竞态）
     * @param id 会员ID
     * @param integration 要增加的积分
     * @return 影响行数
     */
    int addIntegration(@Param("id") Long id, @Param("integration") Integer integration);
}