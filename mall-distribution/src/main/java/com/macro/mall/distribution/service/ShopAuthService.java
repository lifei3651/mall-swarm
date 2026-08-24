package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dto.ShopLoginDTO;
import com.macro.mall.distribution.dto.ShopRegisterDTO;
import com.macro.mall.distribution.dto.AdminMemberCreateDTO;
import com.macro.mall.distribution.dto.ShopAccountSetupDTO;
import com.macro.mall.distribution.dto.ShopPasswordChangeDTO;
import com.macro.mall.distribution.dto.ShopNicknameUpdateDTO;
import com.macro.mall.distribution.dto.ShopPhoneUpdateDTO;
import com.macro.mall.distribution.dto.ShopInviteBindDTO;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.vo.ShopAuthVO;
import com.macro.mall.distribution.vo.AgentInfoVO;
import com.macro.mall.distribution.vo.AdminMemberVO;

import java.util.List;

public interface ShopAuthService {

    ShopAuthVO register(ShopRegisterDTO dto);

    ShopAuthVO register(ShopRegisterDTO dto, String surface);

    /** 公开商城注册：只创建购物账号，不在注册阶段建立团队关系。 */
    ShopAuthVO registerPublic(ShopRegisterDTO dto);

    ShopAuthVO login(ShopLoginDTO dto);

    ShopAuthVO login(ShopLoginDTO dto, String surface);

    DmsShopMember me(String authorization);

    DmsShopMember resolveMember(String authorization);

    DmsShopMember requireMember(String authorization);

    /** 敏感能力必须校验服务端会话签发来源，不能信任客户端临时请求头。 */
    void requireSurface(String authorization, String requiredSurface);

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

    DmsShopMember updateNickname(DmsShopMember member, ShopNicknameUpdateDTO dto);

    boolean updatePhone(DmsShopMember member, ShopPhoneUpdateDTO dto);

    /** 团队 H5 首次进入时绑定唯一直属邀请关系；绑定后不可自行修改。 */
    DmsShopMember bindInviter(DmsShopMember member, ShopInviteBindDTO dto);

    /**
     * 重置会员密码（忘记密码）
     * @param phone 手机号
     * @param smsCode 短信验证码
     * @param newPassword 新密码
     */
    void resetPassword(String phone, String smsCode, String newPassword);

    boolean unlockMember(Long id);
}
