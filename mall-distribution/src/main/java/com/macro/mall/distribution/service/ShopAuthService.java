package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dto.ShopLoginDTO;
import com.macro.mall.distribution.dto.ShopRegisterDTO;
import com.macro.mall.distribution.dto.AdminMemberCreateDTO;
import com.macro.mall.distribution.dto.ShopAccountSetupDTO;
import com.macro.mall.distribution.dto.ShopPasswordChangeDTO;
import com.macro.mall.distribution.dto.ShopNicknameUpdateDTO;
import com.macro.mall.distribution.dto.ShopPhoneUpdateDTO;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.vo.ShopAuthVO;
import com.macro.mall.distribution.vo.AgentInfoVO;
import com.macro.mall.distribution.vo.AdminMemberVO;

import java.util.List;

public interface ShopAuthService {

    ShopAuthVO register(ShopRegisterDTO dto);

    ShopAuthVO register(ShopRegisterDTO dto, String surface);

    /** 公开商城注册：普通入口创建购物账号；邀请链接在同一事务中创建账号并绑定邀请人。 */
    ShopAuthVO registerPublic(ShopRegisterDTO dto);

    ShopAuthVO login(ShopLoginDTO dto);

    ShopAuthVO login(ShopLoginDTO dto, String surface);

    /** 微信官方手机号已由服务端验证后，登录既有账号或创建普通商城账号。 */
    ShopAuthVO loginOrRegisterWechat(String verifiedPhone, String inviteCode);

    /** 已绑定微信身份的会员免密码换取小程序商城会话。 */
    ShopAuthVO loginWechatMember(Long memberId);

    DmsShopMember me(String authorization);

    /** 按入口校验账号是否具备访问资格，团队入口只允许已受邀或后台开通的账号。 */
    DmsShopMember me(String authorization, String surface);

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

    /**
     * 重置会员密码（忘记密码）
     * @param phone 手机号
     * @param smsCode 短信验证码
     * @param newPassword 新密码
     * @param captchaId 图形验证码编号
     * @param captchaCode 图形验证码
     */
    void resetPassword(String phone, String smsCode, String newPassword, String captchaId, String captchaCode);

    boolean unlockMember(Long id);
}
