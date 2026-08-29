package com.macro.mall.distribution.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.macro.mall.common.sms.SmsBusinessType;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsShopMemberSessionDao;
import com.macro.mall.distribution.dao.DmsTenantDao;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dto.AgentRegisterDTO;
import com.macro.mall.distribution.dto.AgentSwitchLineDTO;
import com.macro.mall.distribution.dto.AdminMemberCreateDTO;
import com.macro.mall.distribution.dto.ShopAccountSetupDTO;
import com.macro.mall.distribution.dto.ShopLoginDTO;
import com.macro.mall.distribution.dto.ShopPasswordChangeDTO;
import com.macro.mall.distribution.dto.ShopNicknameUpdateDTO;
import com.macro.mall.distribution.dto.ShopPhoneUpdateDTO;
import com.macro.mall.distribution.dto.ShopInviteBindDTO;
import com.macro.mall.distribution.dto.AgentUpdateDTO;
import com.macro.mall.distribution.dto.ShopRegisterDTO;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.entity.DmsShopMemberSession;
import com.macro.mall.distribution.entity.DmsTenant;
import com.macro.mall.distribution.service.AgentService;
import com.macro.mall.distribution.service.LoginCaptchaService;
import com.macro.mall.distribution.service.ShopAuthService;
import com.macro.mall.distribution.service.SmsVerificationService;
import com.macro.mall.distribution.vo.AgentInfoVO;
import com.macro.mall.distribution.vo.AdminMemberVO;
import com.macro.mall.distribution.vo.ShopAuthVO;
import com.macro.mall.distribution.enums.AgentLevelEnum;
import com.macro.mall.distribution.enums.AgentSourceTypeEnum;
import com.macro.mall.distribution.enums.PromotionJoinModeEnum;
import com.macro.mall.distribution.util.PhoneNumberUtils;
import com.macro.mall.distribution.util.MemberNicknameUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.macro.mall.distribution.service.MemberMessageService;
import com.macro.mall.distribution.service.MemberMessageEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ShopAuthServiceImpl implements ShopAuthService {
    private static final int MAX_FAILED_LOGIN_COUNT = 5;
    private static final int LOGIN_LOCK_MINUTES = 15;
    /** 不存在账号也执行一次同成本校验，降低基于响应时间的账号枚举。 */
    private static final String DUMMY_PASSWORD_HASH = BCrypt.hashpw("invalid-login-placeholder");
    private static final String GENERIC_LOGIN_ERROR = "账号或登录凭证错误";

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
    private final DmsTenantDao tenantDao;
    private final MemberMessageService memberMessageService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopAuthVO register(ShopRegisterDTO dto) {
        return register(dto, "integrated");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopAuthVO register(ShopRegisterDTO dto, String surface) {
        return registerInternal(dto, true, true, normalizeSurface(surface, "team"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopAuthVO registerPublic(ShopRegisterDTO dto) {
        // 普通入口继续只创建购物账号；通过邀请二维码进入时，注册提交本身即为一次性关系确认。
        // 页面只展示脱敏邀请人和不可自行修改提示，不在公开商城展示任何奖金制度。
        boolean invitedRegistration = dto != null && dto.getInviteCode() != null && !dto.getInviteCode().isBlank();
        return registerInternal(dto, invitedRegistration, false, "public");
    }

    private ShopAuthVO registerInternal(ShopRegisterDTO dto, boolean requireInvitation,
                                        boolean allowFoundingMember, String surface) {
        validateRegister(dto);
        dto.setPhone(dto.getPhone().trim());
        dto.setUsername(normalizeLoginAccount(dto.getUsername()));

        // 先证明手机号归属，再告知已注册或登录账号冲突，避免匿名枚举会员名单。
        smsVerificationService.verifyAndConsume(dto.getPhone(), dto.getSmsCode(), SMS_BIZ_TYPE_REGISTER);
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

        // 团队 H5 必须携带邀请；公开商城普通入口不绑定，扫码邀请入口在本次注册中一次性绑定。
        Long inviterId = null;
        boolean foundingMember = requireInvitation && allowFoundingMember
                && memberDao.countForFoundingTeamMember() == 0;
        if (requireInvitation && !foundingMember) {
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
        member.setTeamOptIn(requireInvitation ? 1 : 0);
        memberDao.insert(member);

        // 邀请关系在注册交易内一次性绑定；是否同时开通推广资格，由客户业务模式独立决定。
        DmsTenant tenant = tenantDao.selectById(TenantContext.getTenantId());
        PromotionJoinModeEnum joinMode = PromotionJoinModeEnum.forExisting(
                tenant == null ? null : tenant.getPromotionJoinMode());
        if (requireInvitation && joinMode.autoOnInvite()) {
            activateMember(member.getUserId(), 1,
                    foundingMember ? "团队首位成员注册后自动开通推广资格" : "受邀注册后自动开通推广资格");
        }
        return createSession(member, surface);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopMember bindInviter(DmsShopMember member, ShopInviteBindDTO dto) {
        if (member == null) Asserts.unauthorized("请先登录");
        if (dto == null || dto.getInviteCode() == null || dto.getInviteCode().isBlank()) {
            Asserts.fail("请输入邀请码");
        }
        DmsTenant tenant = lockAgentMutationScope();
        DmsShopMember current = memberDao.selectByIdForUpdate(member.getId());
        if (current == null || !Integer.valueOf(1).equals(current.getStatus())) Asserts.fail("会员不存在或不可用");
        if (current.getInviterId() != null) Asserts.fail("直属邀请关系已经绑定，不能自行修改");

        String code = dto.getInviteCode().trim().toUpperCase(java.util.Locale.ROOT);
        DmsShopMember inviter = memberDao.selectByInviteCode(code);
        if (inviter == null) {
            AgentInfoVO legacyInviter = agentService.getAgentByInviteCode(code);
            if (legacyInviter != null && Integer.valueOf(1).equals(legacyInviter.getStatus())) {
                inviter = memberDao.selectByUserId(legacyInviter.getUserId());
            }
        }
        if (inviter == null || !Integer.valueOf(1).equals(inviter.getStatus())) Asserts.fail("邀请码无效");
        if (Objects.equals(inviter.getUserId(), current.getUserId())) Asserts.fail("不能绑定自己的邀请码");

        AgentInfoVO currentAgent = agentService.getAgentByUserId(current.getUserId());
        if (currentAgent != null && currentAgent.getParentId() != null) {
            Asserts.fail("当前账号已经存在团队上级，不能重复绑定");
        }
        if (memberDao.bindInviterIdIfAbsent(current.getId(), inviter.getUserId()) <= 0) {
            Asserts.fail("直属邀请关系已经绑定，请刷新后查看");
        }
        // 首次进入团队端并主动绑定关系，视为团队业务参与选择；资格仍由客户模式决定。
        memberDao.markTeamOptIn(current.getId());

        if (PromotionJoinModeEnum.forExisting(tenant.getPromotionJoinMode()).autoOnInvite()
                && currentAgent == null) {
            currentAgent = activateMember(current.getUserId(), 1, "首次绑定直属邀请关系后自动开通推广资格");
        }

        AgentInfoVO inviterAgent = agentService.getAgentByUserId(inviter.getUserId());
        if (currentAgent != null && inviterAgent != null) {
            AgentSwitchLineDTO switchLine = new AgentSwitchLineDTO();
            switchLine.setAgentId(currentAgent.getId());
            switchLine.setNewParentAgentId(inviterAgent.getId());
            switchLine.setReason("公开商城账号首次进入团队H5绑定直属邀请关系");
            agentService.switchLine(switchLine);
        }
        return sanitize(memberDao.selectById(current.getId()));
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
        // 后台“会员管理”属于团队业务入口；是否自动开通资格仍遵循当前客户模式。
        member.setTeamOptIn(1);
        memberDao.insert(member);
        if (Boolean.TRUE.equals(dto.getActivateDistribution())) {
            activateMember(member.getUserId(), dto.getInitialLevel(), dto.getReason() == null ? "后台新增会员并授予推广资格" : dto.getReason());
        }
        return sanitize(member);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentInfoVO activateMember(Long userId, Integer initialLevel, String reason) {
        lockAgentMutationScope();
        DmsShopMember member = memberDao.selectByUserId(userId);
        // 兼容旧后台页面曾展示的“会员表ID”；最终统一换成系统用户ID处理。
        if (member == null) member = memberDao.selectById(userId);
        if (member == null) Asserts.fail("会员不存在");
        memberDao.markTeamOptIn(member.getId());
        int target = initialLevel == null ? 1 : initialLevel;
        if (AgentLevelEnum.getByValue(target) == null) Asserts.fail("会员级别不正确");
        Long canonicalUserId = member.getUserId();
        AgentInfoVO existing = agentService.getAgentByUserId(canonicalUserId);
        if (existing == null) {
            AgentRegisterDTO dto = new AgentRegisterDTO();
            dto.setUserId(canonicalUserId); dto.setAgentName(member.getNickname()); dto.setPhone(member.getPhone());
            dto.setInitialLevel(target);
            dto.setReason(reason == null || reason.isBlank() ? "授予推广资格" : reason.trim());
            boolean activatedByOrder = reason != null && reason.startsWith("完成首笔有效支付订单");
            boolean activatedByInvite = reason != null && (reason.startsWith("受邀注册")
                    || reason.startsWith("首次绑定直属邀请关系") || reason.startsWith("团队首位成员注册"));
            dto.setSourceType(reason != null && reason.startsWith("外部团队平移")
                    ? AgentSourceTypeEnum.BATCH_IMPORT.getValue()
                    : activatedByOrder ? AgentSourceTypeEnum.SELF_REGISTER.getValue()
                    : activatedByInvite ? AgentSourceTypeEnum.SCAN_CODE.getValue()
                    : AgentSourceTypeEnum.ADMIN_ADD.getValue());
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
     * 如果C比直属邀请人B更早开通推广资格，C会暂时没有推广上级，绝不错误归到A。
     * B以后开通资格时，把B直接邀请且已经激活的会员移回B名下；历史订单快照不变，未来订单按新关系计算。
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
            dto.setReason("直属邀请人开通推广资格，恢复直接推荐关系（历史业绩和奖金不变）");
            agentService.switchLine(dto);
        }
    }

    private DmsTenant lockAgentMutationScope() {
        DmsTenant tenant = tenantDao.selectByIdForUpdate(TenantContext.getTenantId());
        if (tenant == null) {
            Asserts.fail("商城客户配置不存在，暂不能修改会员关系");
        }
        return tenant;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopMember setupAccount(DmsShopMember member, ShopAccountSetupDTO dto) {
        if (member == null) Asserts.unauthorized("请先登录");
        if (dto == null) Asserts.fail("请输入登录账号");
        String username = normalizeLoginAccount(dto.getUsername());
        if (dto.getPassword() == null || dto.getPassword().length() < 6) Asserts.fail("密码至少需要6位");
        DmsShopMember current = memberDao.selectByIdForUpdate(member.getId());
        if (current == null) Asserts.fail("会员不存在");
        if (current.getUsername() != null && !current.getUsername().isBlank()
                && !Objects.equals(current.getUsername(), current.getPhone())) {
            Asserts.fail("登录账号已经设置，如需修改密码请使用账号安全功能");
        }
        DmsShopMember same = memberDao.selectByUsername(username);
        if (same != null && !same.getId().equals(current.getId())) Asserts.fail("登录账号已存在");
        if (memberDao.updateAccount(current.getId(), username, hash(dto.getPassword())) <= 0) {
            Asserts.fail("登录账号已经设置，请刷新后使用账号安全功能");
        }
        // 凭据发生变化后使包括当前会话在内的全部旧会话失效，要求使用新账号重新登录。
        sessionDao.disableByMemberId(current.getId());
        return sanitize(memberDao.selectById(current.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changePassword(DmsShopMember member, ShopPasswordChangeDTO dto) {
        if (member == null) Asserts.unauthorized("请先登录");
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
        memberMessageService.publish(new MemberMessageEvent(TenantContext.getTenantId(), current.getUserId(),
                "LOGIN_PASSWORD_CHANGED:" + current.getId() + ":" + System.currentTimeMillis(),
                "LOGIN_PASSWORD_CHANGED", "ACCOUNT_SECURITY", "ACCOUNT_SECURITY", current.getId(), null,
                LocalDateTime.now()));
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsShopMember updateNickname(DmsShopMember member, ShopNicknameUpdateDTO dto) {
        if (member == null) Asserts.unauthorized("请先登录");
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
        if (member == null) Asserts.unauthorized("请先登录");
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
        memberMessageService.publish(new MemberMessageEvent(TenantContext.getTenantId(), current.getUserId(),
                "PHONE_CHANGED:" + current.getId() + ":" + System.currentTimeMillis(),
                "PHONE_CHANGED", "ACCOUNT_SECURITY", "ACCOUNT_SECURITY", current.getId(), null,
                LocalDateTime.now()));
        return true;
    }

    @Override
    // 不包裹外层事务：密码错误时失败次数必须保留，不能随异常一起回滚。
    public ShopAuthVO login(ShopLoginDTO dto) {
        return login(dto, "integrated");
    }

    @Override
    // 不包裹外层事务：密码错误时失败次数必须保留，不能随异常一起回滚。
    public ShopAuthVO login(ShopLoginDTO dto, String surface) {
        if (dto == null || dto.getAccount() == null || dto.getAccount().isBlank()) {
            Asserts.fail("账号不能为空");
        }

        String loginType = dto.getLoginType() == null ? "password" : dto.getLoginType();
        String account = dto.getAccount().trim();
        if ("sms".equals(loginType) && !PhoneNumberUtils.isValidMainlandMobile(account)) {
            Asserts.fail("请输入正确的11位手机号");
        }
        DmsShopMember member = memberDao.selectByAccount(account);

        if ("sms".equals(loginType)) {
            // 短信验证码登录
            smsVerificationService.verifyAndConsume(account, dto.getSmsCode(), SMS_BIZ_TYPE_LOGIN);
            // 验证码通过后，手机号归属已经得到确认，可以准确引导本人完成注册；
            // 验证码未通过时由 verifyAndConsume 直接失败，不能借此枚举已注册账号。
            if (member == null) {
                Asserts.fail("该手机号尚未注册，请先注册账号");
            }
            if (hasActiveLoginLock(member) || !Integer.valueOf(1).equals(member.getStatus())) {
                Asserts.fail(GENERIC_LOGIN_ERROR);
            }
        } else {
            // 密码登录
            if (dto.getPassword() == null || dto.getPassword().isBlank()) {
                Asserts.fail("密码不能为空");
            }
            loginCaptchaService.verify("shop", dto.getCaptchaId(), dto.getCaptchaCode());
            String passwordHash = member == null || member.getPasswordHash() == null || member.getPasswordHash().isBlank()
                    ? DUMMY_PASSWORD_HASH : member.getPasswordHash();
            boolean passwordMatches = checkPassword(dto.getPassword(), passwordHash);
            if (member == null || hasActiveLoginLock(member) || !passwordMatches) {
                if (member == null || member.getLockTime() != null) Asserts.fail(GENERIC_LOGIN_ERROR);
                memberDao.increaseFailedLogin(member.getId(), MAX_FAILED_LOGIN_COUNT);
                Asserts.fail(GENERIC_LOGIN_ERROR);
            }
        }

        if (!Integer.valueOf(1).equals(member.getStatus())) {
            Asserts.fail(GENERIC_LOGIN_ERROR);
        }
        memberDao.updateLastLoginTime(member.getId());
        // 单账号单会话：新登录成功后使该会员此前的全部会话失效。
        sessionDao.disableByMemberId(member.getId());
        return createSession(member, normalizeSurface(surface, "public"));
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
            Asserts.unauthorized("请先登录");
        }
        return member;
    }

    @Override
    public void requireSurface(String authorization, String requiredSurface) {
        String token = stripToken(authorization);
        if (token == null) Asserts.unauthorized("请先登录");
        DmsShopMemberSession session = sessionDao.selectByToken(hashToken(token));
        if (session == null || !Integer.valueOf(1).equals(session.getStatus())
                || session.getExpireTime() == null || session.getExpireTime().isBefore(LocalDateTime.now())) {
            Asserts.unauthorized("请先登录");
        }
        if (!normalizeSurface(requiredSurface, "integrated").equals(session.getSurface())) {
            Asserts.fail("当前版本不提供余额互转，请使用三合一版");
        }
    }

    @Override
    public boolean logout(String authorization) {
        String token = stripToken(authorization);
        if (token == null) return false;
        return sessionDao.disableByToken(hashToken(token)) > 0;
    }

    @Override
    public List<AdminMemberVO> listAdminMembers(String keyword, Integer status,
                                                 Integer promotionActivated, Integer agentLevel) {
        List<AdminMemberVO> members = memberDao.selectAdminList(keyword, status, promotionActivated, agentLevel);
        members.forEach(member -> {
            AgentLevelEnum level = AgentLevelEnum.getByValue(member.getAgentLevel());
            member.setAgentLevelName(level == null ? null : level.getName());
            member.setPaymentPasswordLocked(member.getPaymentPasswordLockTime() != null
                    && member.getPaymentPasswordLockTime().plusMinutes(30).isAfter(LocalDateTime.now()));
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
    @Transactional(rollbackFor = Exception.class)
    public boolean updateMemberStatus(Long id, Integer status) {
        int target = status == null ? 1 : status;
        if (target != 0 && target != 1) Asserts.fail("会员状态不正确");
        DmsShopMember member = memberDao.selectByIdForUpdate(id);
        if (member == null) {
            Asserts.fail("会员不存在");
        }
        if (Integer.valueOf(target).equals(member.getStatus())) return true;
        return memberDao.updateStatus(id, target) > 0;
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
        memberMessageService.publish(new MemberMessageEvent(TenantContext.getTenantId(), member.getUserId(),
                "LOGIN_PASSWORD_RESET:" + member.getId() + ":" + System.currentTimeMillis(),
                "LOGIN_PASSWORD_CHANGED", "ACCOUNT_SECURITY", "ACCOUNT_SECURITY", member.getId(), null,
                LocalDateTime.now()));

    }

    @Override
    public boolean unlockMember(Long id) {
        if (id == null || memberDao.selectById(id) == null) Asserts.fail("会员不存在");
        return memberDao.clearLoginLock(id) > 0;
    }

    private ShopAuthVO createSession(DmsShopMember member, String surface) {
        DmsShopMemberSession session = new DmsShopMemberSession();
        session.setMemberId(member.getId());
        session.setUserId(member.getUserId());
        String rawToken = IdUtil.fastSimpleUUID() + IdUtil.fastSimpleUUID();
        session.setToken(hashToken(rawToken));
        session.setSurface(normalizeSurface(surface, "public"));
        session.setStatus(1);
        session.setExpireTime(LocalDateTime.now().plusDays(SESSION_DAYS));
        sessionDao.insert(session);

        ShopAuthVO vo = new ShopAuthVO();
        vo.setToken(rawToken);
        vo.setExpireTime(session.getExpireTime());
        vo.setMember(sanitize(member));
        return vo;
    }

    private String normalizeSurface(String value, String fallback) {
        String surface = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (surface) {
            case "public", "team", "integrated" -> surface;
            default -> fallback;
        };
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

    /**
     * 商城账号与后台账号采用相同的 15 分钟临时锁定策略。过期锁只在登录时按主键清除，
     * 保留错误计数的防爆破能力，同时避免攻击者通过连续错误密码永久锁死他人账号。
     */
    private boolean hasActiveLoginLock(DmsShopMember member) {
        if (member == null || member.getLockTime() == null) return false;
        LocalDateTime expiredBefore = LocalDateTime.now().minusMinutes(LOGIN_LOCK_MINUTES);
        if (member.getLockTime().isAfter(expiredBefore)) return true;
        // 只清除本次读取到的过期锁；并发请求刚形成的新锁不能被旧请求覆盖。
        if (memberDao.clearExpiredLoginLock(member.getId(), expiredBefore) != 1) return true;
        member.setFailedLoginCount(0);
        member.setLockTime(null);
        return false;
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
