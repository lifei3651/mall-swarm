package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsAdminUser;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DmsAdminUserDao {

    List<DmsAdminUser> list(@Param("keyword") String keyword, @Param("status") Integer status,
                            @Param("merchantId") Long merchantId);

    DmsAdminUser selectById(@Param("id") Long id);

    DmsAdminUser selectByUsername(@Param("username") String username);

    DmsAdminUser selectByUsernameAndPortal(@Param("username") String username,
                                           @Param("portal") String portal);

    int insert(DmsAdminUser user);

    int update(DmsAdminUser user);

    int updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash,
                       @Param("salt") String salt, @Param("mustChangePassword") Integer mustChangePassword);

    int updateTemporaryPassword(@Param("id") Long id, @Param("passwordHash") String passwordHash,
                                @Param("salt") String salt,
                                @Param("credentialExpiresAt") LocalDateTime credentialExpiresAt);

    /** 原子认领一次性临时凭据；只有未使用且未过期的凭据能成功一次。 */
    int consumeTemporaryCredential(@Param("id") Long id, @Param("now") LocalDateTime now);

    int updateDefaultLogisticsCompany(@Param("id") Long id,
                                      @Param("defaultLogisticsCompany") String defaultLogisticsCompany);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int updateLastLoginTime(@Param("id") Long id);
    int increaseFailedLogin(@Param("id") Long id, @Param("lockThreshold") Integer lockThreshold);
    int clearLoginLock(@Param("id") Long id);
    int clearExpiredLoginLock(@Param("id") Long id, @Param("expiredBefore") LocalDateTime expiredBefore);
}
