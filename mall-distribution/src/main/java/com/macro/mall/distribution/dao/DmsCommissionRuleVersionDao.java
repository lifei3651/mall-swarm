package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsCommissionRuleVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DmsCommissionRuleVersionDao {

    List<DmsCommissionRuleVersion> selectByTenantId(@Param("tenantId") Long tenantId);

    DmsCommissionRuleVersion selectActiveByTenantId(@Param("tenantId") Long tenantId);

    DmsCommissionRuleVersion selectActiveByTenantIdForUpdate(@Param("tenantId") Long tenantId);

    DmsCommissionRuleVersion selectById(@Param("tenantId") Long tenantId, @Param("id") Long id);

    /** 当前读冻结版本，避免支付事务的旧 RR 快照看不到刚生效的不可变规则。 */
    DmsCommissionRuleVersion selectByIdForUpdate(@Param("tenantId") Long tenantId, @Param("id") Long id);

    int insert(DmsCommissionRuleVersion version);

    /** 必须在持有租户配置行锁的事务内调用；不改变历史配置快照。 */
    int deactivateActive(@Param("tenantId") Long tenantId);

}
