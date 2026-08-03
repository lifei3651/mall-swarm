package com.macro.mall.mapper;

import com.macro.mall.model.SmsNotice;
import com.macro.mall.model.SmsNoticeExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface SmsNoticeMapper {
    long countByExample(SmsNoticeExample example);
    int deleteByExample(SmsNoticeExample example);
    int deleteByPrimaryKey(Long id);
    int insert(SmsNotice row);
    int insertSelective(SmsNotice row);
    List<SmsNotice> selectByExample(SmsNoticeExample example);
    SmsNotice selectByPrimaryKey(Long id);
    int updateByExampleSelective(@Param("row") SmsNotice row, @Param("example") SmsNoticeExample example);
    int updateByExample(@Param("row") SmsNotice row, @Param("example") SmsNoticeExample example);
    int updateByPrimaryKeySelective(SmsNotice row);
    int updateByPrimaryKey(SmsNotice row);
}
