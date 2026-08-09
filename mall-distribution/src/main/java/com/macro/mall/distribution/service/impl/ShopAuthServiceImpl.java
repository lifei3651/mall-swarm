package com.macro.mall.distribution.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.macro.mall.common.sms.SmsBusinessType;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsShopMemberSessionDao;
import com.macro.mall.distribution.dto.AgentRegisterDTO;
import com.macro.mall.distribution.dto.AgentSwitchLineDTO;
import com.macro.mall.distribution.dto.AdminMemberCreateDTO;
import com.macro.mall.distribution.dto.ShopAccountSetupDTO;
import com.macro.mall.distribution.dto.ShopLoginDTO;
import com.macro.mall.distribution.dto.ShopPasswordChangeDTO;
import com.macro.mall.distribution.dto.ShopNicknameUpdateDTO;
import com.macro.mall.distribution.dto.ShopPhoneUpdateDTO;
import com.macro.mall.distribution.dto.AgentUpdateDTO;
import com.macro.mall.distribution.dto.ShopRegisterDTO;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsShopMemberSession;
import com.macro.mall.distribution.service.AgentService;
import com.macro.mall.distribution.service.LoginCaptchaService;
import com.macro.mall.distribution.service.ShopAuthService;
import com.macro.mall.distribution.service.SmsVerificationService;
import com.macro.mall.distribution.vo.AgentInfoVO;
import com.macro.mall.distribution.vo.AdminMemberVO;
import com.macro.mall.distribution.vo.ShopAuthVO;
import com.macro.mall.distribution.enums.AgentLevelEnum;
import com.macro.mall.distribution.util.PhoneNumberUtils;
import com.macro.mall.distribution.util.MemberNicknameUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ShopAuthServiceImpl implements ShopAuthService {
    private static final int MAX_FAILED_LOGIN_COUNT = 5;

    private static final int SESSION_DAYS = 7;
    // 统一短信验证码 Key 格式：sms:{bizType}:{phone}
    private static final int SMS_BIZ_TYPE_REGISTER = 1;
    private static final int SMS_BIZ_TYPE_LOGIN = 2;
    private static final int SMS_BIZ_TYPE_RESET_PASSWORD = 3;

    private final DmsShopMemberDao memberDao;
    private final DmsShopMemberSessionDao sessionDao;
    private final AgentService agentService;
    private final LoginCaptchaService loginCaptchaService;
    private final SmsVerificationService smsVerificationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopAuthVO register(ShopRegisterDTO dto) {
        validateRegister(dto);
        dto.setPhone(dto.getPhone().trim());
        dto.setUsername(normalizeLoginAccount(dto.getUsername()));
        if (memberDao.selectByPhone(dto.getPhone()) != null) {
            Asserts.fail("该手机号已注册，请直接登录或使用其他手机号");
        }
        if (memberDao.selectByUsername(dto.getPhone()) != null) {
            Asserts.fail("该手机号已被其他账号占用，请更换手机号或联系管理员");
        }
        if (dto.getUsername() != null && !dto.getUsername().isBlank()
                && memberDao.selectByAccount(dto.getUsername()) != null) {
            Asserts.fail("该登录账号已被使用，请更换登录账号");
        }

        // 强制验证短信验证码（含错误次数限制，连续错误会作废验证码）
        smsVerificationService.verifyAndConsume(dto.getPhone(), dto.getSmsCode(), SMS_BIZ_TYPE_REGISTER);

        // 新部署且还没有会员时，允许创建唯一的创始会员；之后始终要求有效邀请码。
        // countForFoundingMember 使用行锁，避免并发注册时出现两个根节点。
        Long inviterId = null;
        boolean foundingMember = memberDao.countForFoundingMember() == 0;
        if (!foundingMember) {
            if (dto.getInviteCode() == null || dto.getInviteCode().isBlank()) {
                Asserts.fail("请输入邀请码");
            }
            String inviteCode = dto.getInviteCode().trim().toUpperCase(java.util.Locale.ROOT);
            DmsShopMember inviter = memberDao.selectByInviteCode(inviteCode);
            if (inviter == null) {
                // 兼容旧版本为正式会员另外生成的邀请码，避免历史二维码和链接失效。
                AgentInfoVO legacyInviter = agentService.getAgentByInviteCode(inviteCode);
                if (legacyInviter != null && Integer.valueOf(1).equals(legacyInviter.getStatus())) {
                    inviter = memberDao.selectByUserId(legacyInviter.getUserId());
                }
            }
            if (inviter == null || !Integer.valueOf(1).equals(inviter.getStatus())) {
                Asserts.fail("邀请码无效");
            }
            inviterId = inviter.getUserId();
        }

        DmsShopMember member = new DmsShopMember();
        member.setUserId(IdUtil.getSnowflakeNextId());
        member.setPhone(dto.getPhone());
        member.setUsername(dto.getUsername());
        member.setPasswordHash(hash(dto.getPassword()));
        member.setNickname(dto.getUsername());
        member.setInviteCode(IdUtil.fastSimpleUUID().substring(0, 8).toUpperCase());
        member.setInviterId(inviterId);
        member.setStatus(1);
        memberDao.insert(member);

        // 注册只创建商城登录账号；完成首笔有效支付或后台授予后，才进入奖金体系成为一级“会员”。
        return createSession(member);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopMember createAdminMember(AdminMemberCreateDTO dto) {
        if (dto == null || !PhoneNumberUtils.isValidMainlandMobile(dto.getPhone())) {
            Asserts.fail("请输入正确的11位手机号");
        }
        dto.setPhone(PhoneNumberUtils.normalize(dto.getPhone()));
        String username = normalizeLoginAccount(dto.getUsername());
        if (username.equals(dto.getPhone())) {
            Asserts.fail("登录账号不能与手机号相同");
        }
        String initialPassword = dto.getPassword() == null ? "" : dto.getPassword();
        if (!initialPassword.isBlank() && (initialPassword.length() < 6 || initialPassword.length() > 32)) {
            Asserts.fail("初始密码需要6至32位");
        }
        if (memberDao.selectByAccount(dto.getPhone()) != null) Asserts.fail("该手机号已被注册或用作登录账号");
        if (memberDao.selectByAccount(username) != null) Asserts.fail("登录账号已存在");
        String nickname = dto.getNickname() == null || dto.getNickname().isBlank()
                ? username : MemberNicknameUtils.normalize(dto.getNickname());
        DmsShopMember inviter = null;
        if (dto.getInviterUserId() != null) {
            inviter = memberDao.selectByUserId(dto.getInviterUserId());
            if (inviter == null) Asserts.fail("邀请会员不存在");
        }
        DmsShopMember member = new DmsShopMember();
        member.setUserId(IdUtil.getSnowflakeNextId());
        member.setPhone(dto.getPhone());
        member.setUsername(username);
        member.setPasswordHash(hash(initialPassword.isBlank() ? IdUtil.fastSimpleUUID() : initialPassword));
        member.setNickname(nickname);
        member.setInviteCode(IdUtil.fastSimpleUUID().substring(0, 8).toUpperCase());
        member.setInviterId(inviter == null ? null : inviter.getUserId());
        member.setStatus(1);
        memberDao.insert(member);
        if (Boolean.TRUE.equals(dto.getActivateDistribution())) {
            activateMember(member.getUserId(), dto.getInitialLevel(), dto.getReason() == null ? "后台新增会员并授予推广资格" : dto.getReason());
        }
        return sanitize(member);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentInfoVO activateMember(Long userId, Integer initialLevel, String reason) {
        DmsShopMember member = memberDao.selectByUserId(userId);
        // 兼容旧后台页面曾展示的“会员表ID”；最终统一换成系统用户ID处理。
        if (member == null) member = memberDao.selectById(userId);
        if (member == null) Asserts.fail("会员不存在");
        int target = initialLevel == null ? 1 : initialLevel;
        if (AgentLevelEnum.getByValue(target) == null) Asserts.fail("会员级别不正确");
        Long canonicalUserId = member.getUserId();
        AgentInfoVO existing = agentService.getAgentByUserId(canonicalUserId);
        if (existing == null) {
            AgentRegisterDTO dto = new AgentRegisterDTO();
            dto.setUserId(canonicalUserId); dto.setAgentName(member.getNickname()); dto.setPhone(member.getPhone());
            dto.setInitialLevel(target);
            dto.setReason(reason == null || reason.isBlank() ? "授予推广资格" : reason.trim());
            boolean selfActivatedByOrder = reason != null
                    && reason.startsWith("完成首笔有效支付订单");
            dto.setSourceType(reason != null && reason.startsWith("外部团队平移") ? 4 : (selfActivatedByOrder ? 1 : 3));
            // 直属邀请关系只认一代。B尚未成为会员时，C不能越过B挂到A名下。
            AgentInfoVO directInviter = member.getInviterId() == null
                    ? null : agentService.getAgentByUserId(member.getInviterId());
            if (directInviter != null) dto.setInviteCode(directInviter.getInviteCode());
            existing = agentService.register(dto);
        }
        if (target > existing.getAgentLevel()) {
            agentService.adjustLevel(existing.getId(), target, reason == null ? "授予推广资格" : reason);
            existing = agentService.getAgentById(existing.getId());
        }
        restoreDirectInvitees(existing);
        return existing;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentInfoVO adjustMemberLevel(Long memberId, Integer level, String reason) {
        if (reason == null || reason.isBlank()) Asserts.fail("请输入调级原因");
        DmsShopMember member = memberDao.selectById(memberId);
        if (member == null) Asserts.fail("商城账号不存在");
        AgentInfoVO agent = agentService.getAgentByUserId(member.getUserId());
        if (level != null && level == 0) {
            // 调整为非会员：取消推广资格（下级团队自动移交原上级，余额与历史数据保留）。
            if (agent == null) Asserts.fail("该账号尚未进入奖金体系，无需取消会员资格");
            agentService.deactivate(agent.getId(), reason.trim());
            return null;
        }
        if (AgentLevelEnum.getByValue(level) == null) Asserts.fail("会员级别不正确");
        if (agent == null) {
            return activateMember(member.getUserId(), level, reason.trim());
        }
        return agentService.adjustLevel(agent.getId(), level, reason.trim());
    }

    /**
     * 如果C比直属邀请人B更早完成首单，C会暂时没有推广上级，绝不错误归到A。
     * B以后成为会员时，把B直接邀请且已经激活的会员移回B名下；历史订单快照不变，未来订单按新关系计算。
     */
    private void restoreDirectInvitees(AgentInfoVO inviter) {
        List<DmsShopMember> invitees = memberDao.selectByInviterId(inviter.getUserId());
        if (invitees == null || invitees.isEmpty()) return;
        for (DmsShopMember inviteeMember : invitees) {
            AgentInfoVO invitee = agentService.getAgentByUserId(inviteeMember.getUserId());
            if (invitee == null || Objects.equals(invitee.getParentId(), inviter.getId())) continue;
            AgentSwitchLineDTO dto = new AgentSwitchLineDTO();
            dto.setAgentId(invitee.getId());
            dto.setNewParentAgentId(inviter.getId());
            dto.setReason("直属邀请人完成首单，恢复直接推荐关系（历史业绩和奖金不变）");
            agentService.switchLine(dto);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopMember setupAccount(DmsShopMember member, ShopAccountSetupDTO dto) {
        if (member == null) Asserts.fail("请先登录");
        if (dto == null) Asserts.fail("请输入登录账号");
        String username = normalizeLoginAccount(dto.getUsername());
        if (dto.getPassword() == null || dto.getPassword().length() < 6) Asserts.fail("密码至少需要6位");
        DmsShopMember same = memberDao.selectByUsername(username);
        if (same != null && !same.getId().equals(member.getId())) Asserts.fail("登录账号已存在");
        memberDao.updateAccount(member.getId(), username, hash(dto.getPassword()));
        return sanitize(memberDao.selectById(member.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changePassword(DmsShopMember member, ShopPasswordChangeDTO dto) {
        if (member == null) Asserts.fail("请先登录");
        if (dto == null || dto.getCurrentPassword() == null || dto.getCurrentPassword().isBlank()) {
            Asserts.fail("请输入当前登录密码");
        }
        if (dto.getNewPassword() == null || dto.getNewPassword().length() < 6 || dto.getNewPassword().length() > 32) {
            Asserts.fail("新登录密码需要6至32位");
        }
        DmsShopMember current = memberDao.selectById(member.getId());
        if (current == null || !checkPassword(dto.getCurrentPassword(), current.getPasswordHash())) {
            Asserts.fail("当前登录密码不正确");
        }
        if (checkPassword(dto.getNewPassword(), current.getPasswordHash())) {
            Asserts.fail("新登录密码不能与当前密码相同");
        }
        smsVerificationService.verifyAndConsume(current.getPhone(), dto.getSmsCode(), 8);
        memberDao.updatePassword(current.getId(), hash(dto.getNewPassword()));
        memberDao.clearLoginLock(current.getId());
        sessionDao.disableByMemberId(current.getId());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopMember updateNickname(DmsShopMember member, ShopNicknameUpdateDTO dto) {
        if (member == null) Asserts.fail("请先登录");
        if (dto == null) Asserts.fail("请输入昵称");
        DmsShopMember current = memberDao.selectById(member.getId());
        if (current == null) Asserts.fail("会员不存在");
        String nickname = MemberNicknameUtils.normalize(dto.getNickname());
        if (Objects.equals(nickname, current.getNickname())) {
            return sanitize(current);
        }
        if (memberDao.updateNickname(current.getId(), nickname) <= 0) {
            Asserts.fail("昵称保存失败，请刷新后重试");
        }
        AgentInfoVO agent = agentService.getAgentByUserId(current.getUserId());
        if (agent != null && (Objects.equals(agent.getAgentName(), current.getNickname())
                || Objects.equals(agent.getAgentName(), current.getUsername())
                || Objects.equals(agent.getAgentName(), current.getPhone()))) {
            AgentUpdateDTO update = new AgentUpdateDTO();
            update.setAgentName(nickname);
            agentService.updateAgentInfo(agent.getId(), update);
        }
        return sanitize(memberDao.selectById(current.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updatePhone(DmsShopMember member, ShopPhoneUpdateDTO dto) {
        if (member == null) Asserts.fail("请先登录");
        if (dto == null) Asserts.fail("请输入新手机号");
        DmsShopMember current = memberDao.selectById(member.getId());
        if (current == null) Asserts.fail("会员不存在");
        String newPhone = PhoneNumberUtils.normalize(dto.getNewPhone());
        if (!PhoneNumberUtils.isValidMainlandMobile(newPhone)) Asserts.fail("请输入正确的11位新手机号");
        if (Objects.equals(current.getPhone(), newPhone)) Asserts.fail("新手机号不能与当前手机号相同");
        DmsShopMember conflict = memberDao.selectByAccount(newPhone);
        if (conflict != null && !Objects.equals(conflict.getId(), current.getId())) {
            Asserts.fail("该手机号已被其他账号使用");
        }

        // 先确认新号码可用，再确认当前号码归属；两项都通过后才允许换绑。
        smsVerificationService.verifyAndConsume(newPhone, dto.getNewPhoneSmsCode(), SmsBusinessType.CHANGE_PHONE_NEW);
        smsVerificationService.verifyAndConsume(current.getPhone(), dto.getCurrentPhoneSmsCode(), SmsBusinessType.CHANGE_PHONE_CURRENT);

        String oldPhone = current.getPhone();
        if (memberDao.updatePhoneAndDefaults(current.getId(), oldPhone, newPhone) <= 0) {
            Asserts.fail("手机号修改失败，请刷新后重试");
        }
        AgentInfoVO agent = agentService.getAgentByUserId(current.getUserId());
        if (agent != null) {
            AgentUpdateDTO update = new AgentUpdateDTO();
            update.setPhone(newPhone);
            if (Objects.equals(agent.getAgentName(), oldPhone)) update.setAgentName(newPhone);
            agentService.updateAgentInfo(agent.getId(), update);
        }
        sessionDao.disableByMemberId(current.getId());
        return true;
    }

    @Override
    // 不包裹外层事务：密码错误时失败次数必须保留，不能随异常一起回滚。
    public ShopAuthVO login(ShopLoginDTO dto) {
        if (dto == null || dto.getAccount() == null || dto.getAccount().isBlank()) {
            Asserts.fail("账号不能为空");
        }

        String loginType = dto.getLoginType() == null ? "password" : dto.getLoginType();
        String account = dto.getAccount().trim();
        if ("sms".equals(loginType) && !PhoneNumberUtils.isValidMainlandMobile(account)) {
            Asserts.fail("请输入正确的11位手机号");
        }
        DmsShopMember member = memberDao.selectByAccount(account);
        if (member == null) {
            Asserts.fail("账号不存在");
        }
        if (member.getLockTime() != null) {
            Asserts.fail("账号因连续密码错误已锁定，请通过找回密码或联系后台管理员解除锁定");
        }

        if ("sms".equals(loginType)) {
            // 短信验证码登录
            smsVerificationService.verifyAndConsume(account, dto.getSmsCode(), SMS_BIZ_TYPE_LOGIN);
        } else {
            // 密码登录
            if (dto.getPassword() == null || dto.getPassword().isBlank()) {
                Asserts.fail("密码不能为空");
            }
            loginCaptchaService.verify("shop", dto.getCaptchaId(), dto.getCaptchaCode());
            if (!checkPassword(dto.getPassword(), member.getPasswordHash())) {
                memberDao.increaseFailedLogin(member.getId(), MAX_FAILED_LOGIN_COUNT);
                DmsShopMember refreshed = memberDao.selectById(member.getId());
                if (refreshed != null && refreshed.getLockTime() != null) {
                    Asserts.fail("密码连续错误5次，账号已锁定，请找回密码或联系后台管理员");
                }
                Asserts.fail("账号或密码错误");
            }
        }

        if (!Integer.valueOf(1).equals(member.getStatus())) {
            Asserts.fail("账号已禁用");
        }
        memberDao.updateLastLoginTime(member.getId());
        // 单账号单会话：新登录成功后使该会员此前的全部会话失效。
        sessionDao.disableByMemberId(member.getId());
        return createSession(member);
    }

    @Override
    public DmsShopMember me(String authorization) {
        return sanitize(requireMember(authorization));
    }

    @Override
    public DmsShopMember resolveMember(String authorization) {
        String token = stripToken(authorization);
        if (token == null) {
            return null;
        }
        DmsShopMemberSession session = sessionDao.selectByToken(hashToken(token));
        if (session == null) session = sessionDao.selectByToken(token);
        if (session == null || !Integer.valueOf(1).equals(session.getStatus())
                || session.getExpireTime() == null || session.getExpireTime().isBefore(LocalDateTime.now())) {
            return null;
        }
        DmsShopMember member = memberDao.selectById(session.getMemberId());
        if (member == null || !Integer.valueOf(1).equals(member.getStatus())) {
            return null;
        }
        return member;
    }

    @Override
    public DmsShopMember requireMember(String authorization) {
        DmsShopMember member = resolveMember(authorization);
        if (member == null) {
            Asserts.fail("请先登录");
        }
        return member;
    }

    @Override
    public boolean logout(String authorization) {
        String token = stripToken(authorization);
        if (token == null) return false;
        int updated = sessionDao.disableByToken(hashToken(token));
        return updated > 0 || sessionDao.disableByToken(token) > 0;
    }

    @Override
    public List<AdminMemberVO> listAdminMembers(String keyword, Integer status,
                                                 Integer promotionActivated, Integer agentLevel) {
        List<AdminMemberVO> members = memberDao.selectAdminList(keyword, status, promotionActivated, agentLevel);
        members.forEach(member -> {
            AgentLevelEnum level = AgentLevelEnum.getByValue(member.getAgentLevel());
            member.setAgentLevelName(level == null ? null : level.getName());
        });
        return members;
    }

    @Override
    public DmsShopMember getAdminMember(Long id) {
        DmsShopMember member = memberDao.selectById(id);
        if (member == null) {
            Asserts.fail("会员不存在");
        }
        return sanitize(member);
    }

    @Override
    public boolean updateMemberStatus(Long id, Integer status) {
        if (memberDao.selectById(id) == null) {
            Asserts.fail("会员不存在");
        }
        return memberDao.updateStatus(id, status == null ? 1 : status) > 0;
    }

    @Override
    public void resetPassword(String phone, String smsCode, String newPassword) {
        phone = PhoneNumberUtils.normalize(phone);
        if (!PhoneNumberUtils.isValidMainlandMobile(phone)) {
            Asserts.fail("请输入正确的11位手机号");
        }
        if (smsCode == null || smsCode.isBlank()) {
            Asserts.fail("验证码不能为空");
        }
        if (newPassword == null || newPassword.length() < 6) {
            Asserts.fail("密码至少需要6位");
        }

        // 验证短信验证码（含错误次数限制）
        smsVerificationService.verifyAndConsume(phone, smsCode, SMS_BIZ_TYPE_RESET_PASSWORD);

        // 查找会员
        DmsShopMember member = memberDao.selectByPhone(phone);
        if (member == null) {
            Asserts.fail("该手机号未注册");
        }

        // 更新密码（使用专用方法确保更新 password_hash 字段）
        memberDao.updatePassword(member.getId(), hash(newPassword));
        memberDao.clearLoginLock(member.getId());
        sessionDao.disableByMemberId(member.getId());

    }

    @Override
    public boolean unlockMember(Long id) {
        if (id == null || memberDao.selectById(id) == null) Asserts.fail("会员不存在");
        return memberDao.clearLoginLock(id) > 0;
    }

    private ShopAuthVO createSession(DmsShopMember member) {
        DmsShopMemberSession session = new DmsShopMemberSession();
        session.setMemberId(member.getId());
        session.setUserId(member.getUserId());
        String rawToken = IdUtil.fastSimpleUUID() + IdUtil.fastSimpleUUID();
        session.setToken(hashToken(rawToken));
        session.setStatus(1);
        session.setExpireTime(LocalDateTime.now().plusDays(SESSION_DAYS));
        sessionDao.insert(session);

        ShopAuthVO vo = new ShopAuthVO();
        vo.setToken(rawToken);
        vo.setExpireTime(session.getExpireTime());
        vo.setMember(sanitize(member));
        return vo;
    }

    private void validateRegister(ShopRegisterDTO dto) {
        if (dto == null || !PhoneNumberUtils.isValidMainlandMobile(dto.getPhone())) {
            Asserts.fail("请输入正确的11位手机号");
        }
        String username = dto.getUsername() == null ? "" : dto.getUsername().trim();
        normalizeLoginAccount(username);
        if (dto.getPassword() == null || dto.getPassword().length() < 6 || dto.getPassword().length() > 32) {
            Asserts.fail("登录密码需为6至32位");
        }
    }

    private String normalizeLoginAccount(String value) {
        String account = value == null ? "" : value.trim();
        if (account.isEmpty()) Asserts.fail("请输入登录账号");
        if (account.length() < 4 || account.length() > 20) {
            Asserts.fail("登录账号需为4至20位");
        }
        if (!Character.isLetter(account.charAt(0)) || account.charAt(0) > 127) {
            Asserts.fail("登录账号必须以英文字母开头");
        }
        if (!account.matches("^[A-Za-z][A-Za-z0-9_]{3,19}$")) {
            Asserts.fail("登录账号仅支持英文字母、数字和下划线");
        }
        return account;
    }

    private String hash(String password) {
        return BCrypt.hashpw(password);
    }

    private boolean checkPassword(String password, String hashedPassword) {
        return BCrypt.checkpw(password, hashedPassword);
    }

    private String hashToken(String token) {
        return SecureUtil.sha256(token);
    }

    private String stripToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        return authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
    }

    private DmsShopMember sanitize(DmsShopMember member) {
        if (member == null) {
            return null;
        }
        DmsShopMember copy = new DmsShopMember();
        copy.setId(member.getId());
        copy.setUserId(member.getUserId());
        copy.setPhone(member.getPhone());
        copy.setUsername(member.getUsername());
        copy.setNickname(member.getNickname());
        copy.setAvatarUrl(member.getAvatarUrl());
        copy.setStatus(member.getStatus());
        copy.setLastLoginTime(member.getLastLoginTime());
        copy.setCreateTime(member.getCreateTime());
        copy.setUpdateTime(member.getUpdateTime());
        return copy;
    }
}
