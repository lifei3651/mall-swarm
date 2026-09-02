package com.macro.mall.distribution.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsShopMemberSessionDao;
import com.macro.mall.distribution.dto.AdminMemberPasswordResetDTO;
import com.macro.mall.distribution.dto.AdminMemberPhoneUpdateDTO;
import com.macro.mall.distribution.dto.AgentUpdateDTO;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.service.AdminMemberSecurityService;
import com.macro.mall.distribution.service.AgentService;
import com.macro.mall.distribution.service.OperationLogService;
import com.macro.mall.distribution.vo.AgentInfoVO;
import com.macro.mall.distribution.util.PhoneNumberUtils;
import com.macro.mall.distribution.security.MemberPasswordPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AdminMemberSecurityServiceImpl implements AdminMemberSecurityService {

    private final DmsShopMemberDao memberDao;
    private final DmsShopMemberSessionDao sessionDao;
    private final AgentService agentService;
    private final OperationLogService operationLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updatePhone(Long memberId, AdminMemberPhoneUpdateDTO dto) {
        DmsShopMember member = requireMember(memberId);
        if (dto == null || !PhoneNumberUtils.isValidMainlandMobile(dto.getPhone())) {
            Asserts.fail("请输入正确的11位手机号");
        }
        if (dto.getReason() == null || dto.getReason().trim().isEmpty()) {
            Asserts.fail("请填写修改手机号的原因");
        }
        String newPhone = dto.getPhone().trim();
        String oldPhone = member.getPhone();
        if (Objects.equals(oldPhone, newPhone)) {
            Asserts.fail("新手机号不能与当前手机号相同");
        }
        DmsShopMember conflict = memberDao.selectByAccount(newPhone);
        if (conflict != null && !Objects.equals(conflict.getId(), memberId)) {
            Asserts.fail("该手机号已被其他账号使用");
        }

        int updated = memberDao.updatePhoneAndDefaults(memberId, oldPhone, newPhone);
        if (updated <= 0) Asserts.fail("手机号修改失败，请刷新后重试");

        AgentInfoVO agent = agentService.getAgentByUserId(member.getUserId());
        if (agent != null) {
            AgentUpdateDTO update = new AgentUpdateDTO();
            update.setPhone(newPhone);
            if (Objects.equals(agent.getAgentName(), oldPhone)) update.setAgentName(newPhone);
            agentService.updateAgentInfo(agent.getId(), update);
        }

        sessionDao.disableByMemberId(memberId);
        operationLogService.log("MEMBER_SECURITY", "PHONE_UPDATE", "SHOP_MEMBER", String.valueOf(memberId),
                "手机号：" + maskPhone(oldPhone), "手机号：" + maskPhone(newPhone), dto.getReason().trim());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean resetLoginPassword(Long memberId, AdminMemberPasswordResetDTO dto) {
        DmsShopMember member = requireMember(memberId);
        if (dto == null) Asserts.fail("请输入新登录密码");
        MemberPasswordPolicy.validate(dto.getNewPassword(), member.getUsername(), member.getPhone());
        if (dto.getReason() == null || dto.getReason().trim().isEmpty()) {
            Asserts.fail("请填写重置登录密码的原因");
        }
        if (member.getPasswordHash() != null && member.getPasswordHash().startsWith("$2")
                && BCrypt.checkpw(dto.getNewPassword(), member.getPasswordHash())) {
            Asserts.fail("新登录密码不能与当前密码相同");
        }

        String passwordHash = BCrypt.hashpw(dto.getNewPassword());
        if (memberDao.updatePassword(memberId, passwordHash) <= 0) {
            Asserts.fail("登录密码重置失败，请刷新后重试");
        }
        sessionDao.disableByMemberId(memberId);
        operationLogService.log("MEMBER_SECURITY", "LOGIN_PASSWORD_RESET", "SHOP_MEMBER", String.valueOf(memberId),
                null, "登录密码已重置，全部旧会话已失效", dto.getReason().trim());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unlockPaymentPassword(Long memberId) {
        DmsShopMember member = requireMember(memberId);
        if (memberDao.clearPayPasswordLock(memberId) <= 0) {
            Asserts.fail("支付密码锁定解除失败，请刷新后重试");
        }
        int failedCount = member.getPayPasswordFailedCount() == null ? 0 : member.getPayPasswordFailedCount();
        operationLogService.log("MEMBER_SECURITY", "PAYMENT_PASSWORD_UNLOCK", "SHOP_MEMBER", String.valueOf(memberId),
                "支付密码错误次数：" + failedCount + "，锁定状态："
                        + (member.getPayPasswordLockTime() == null ? "正常" : "已锁定"),
                "支付密码错误次数：0，锁定状态：正常", "后台人工解除支付密码锁定");
        return true;
    }

    private DmsShopMember requireMember(Long memberId) {
        if (memberId == null) Asserts.fail("会员不存在");
        DmsShopMember member = memberDao.selectById(memberId);
        if (member == null) Asserts.fail("会员不存在");
        return member;
    }

    private String maskPhone(String phone) {
        if (phone == null || !phone.matches("^\\d{11}$")) return "-";
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
