package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsDistributionSetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DmsDistributionSettingDao {

    DmsDistributionSetting selectByKey(@Param("settingKey") String settingKey);

    List<DmsDistributionSetting> selectAll();

    int insert(DmsDistributionSetting setting);

    int updateByKey(DmsDistributionSetting setting);
}
