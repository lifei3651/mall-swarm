package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsAdminSession;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface DmsAdminSessionDao {

    DmsAdminSession selectByToken(@Param("token") String token);

    int insert(DmsAdminSession session);

    int disableByToken(@Param("token") String token);

    int disableByAdminId(@Param("adminId") Long adminId);

    List<DmsAdminSession> selectActiveByAdminId(@Param("adminId") Long adminId);
}
