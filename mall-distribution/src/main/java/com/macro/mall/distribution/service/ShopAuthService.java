package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dto.ShopLoginDTO;
import com.macro.mall.distribution.dto.ShopRegisterDTO;
import com.macro.mall.distribution.dto.AdminMemberCreateDTO;
import com.macro.mall.distribution.dto.ShopAccountSetupDTO;
import com.macro.mall.distribution.dto.ShopPasswordChangeDTO;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.vo.ShopAuthVO;
import com.macro.mall.distribution.vo.AgentInfoVO;
import com.macro.mall.distribution.vo.AdminMemberVO;

import java.util.List;

public interface ShopAuthService {

    ShopAuthVO register(ShopRegisterDTO dto);

    ShopAuthVO login(ShopLoginDTO dto);

    DmsShopMember me(String authorization);

    DmsShopMember resolveMember(String authorization);

    DmsShopMember requireMember(String authorization);

    boolean logout(String authorization);

    List<AdminMemberVO> listAdminMembers(String keyword, Integer status,
                                         Integer promotionActivated, Integer agentLevel);

    DmsShopMember getAdminMember(Long id);

    boolean updateMemberStatus(Long id, Integer status);

    DmsShopMember createAdminMember(AdminMemberCreateDTO dto);

    AgentInfoVO activateMember(Long userId, Integer initialLevel, String reason);

    /** 统一会员列表调级：未进入体系则开通，已进入体系则直接升/降级。 */
    AgentInfoVO adjustMemberLevel(Long memberId, Integer level, String reason);

    DmsShopMember setupAccount(DmsShopMember member, ShopAccountSetupDTO dto);

    boolean changePassword(DmsShopMember member, ShopPasswordChangeDTO dto);

    /**
     * 重置会员密码（忘记密码）
     * @param phone 手机号
     * @param smsCode 短信验证码
     * @param newPassword 新密码
     */
    void resetPassword(String phone, String smsCode, String newPassword);

    boolean unlockMember(Long id);
}
