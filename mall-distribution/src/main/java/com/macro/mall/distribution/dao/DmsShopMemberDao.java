package com.macro.mall.distribution.dao;

import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.vo.AdminMemberVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DmsShopMemberDao {

    DmsShopMember selectById(@Param("id") Long id);

    DmsShopMember selectByUserId(@Param("userId") Long userId);

    DmsShopMember selectByPhone(@Param("phone") String phone);

    DmsShopMember selectByUsername(@Param("username") String username);

    DmsShopMember selectByAccount(@Param("account") String account);

    DmsShopMember selectByInviteCode(@Param("inviteCode") String inviteCode);

    /**
     * Locks the membership set while a founding member is being created. This
     * prevents two concurrent empty-store registrations from both becoming roots.
     */
    long countForFoundingMember();

    List<DmsShopMember> selectList(@Param("keyword") String keyword,
                                   @Param("status") Integer status);

    List<AdminMemberVO> selectAdminList(@Param("keyword") String keyword,
                                        @Param("status") Integer status,
                                        @Param("promotionActivated") Integer promotionActivated,
                                        @Param("agentLevel") Integer agentLevel);

    List<DmsShopMember> selectByInviterId(@Param("inviterId") Long inviterId);

    int insert(DmsShopMember member);

    int update(DmsShopMember member);

    int updateLastLoginTime(@Param("id") Long id);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);
    int updatePhoneAndDefaults(@Param("id") Long id, @Param("oldPhone") String oldPhone,
                               @Param("newPhone") String newPhone);
    int updateNickname(@Param("id") Long id, @Param("nickname") String nickname);
    int updateAccount(@Param("id") Long id, @Param("username") String username,
                      @Param("passwordHash") String passwordHash);
    int updateInviterId(@Param("id") Long id, @Param("inviterId") Long inviterId);
    int increaseFailedLogin(@Param("id") Long id, @Param("lockThreshold") Integer lockThreshold);
    int clearLoginLock(@Param("id") Long id);

    int updatePayPassword(@Param("id") Long id, @Param("payPasswordHash") String payPasswordHash);

    int increaseFailedPayPassword(@Param("id") Long id, @Param("lockThreshold") Integer lockThreshold);

    int clearPayPasswordLockIfCount(@Param("id") Long id,
                                    @Param("expectedFailedCount") Integer expectedFailedCount);

    /** 后台人工解除支付密码错误锁定，不修改支付密码本身。 */
    int clearPayPasswordLock(@Param("id") Long id);
}
