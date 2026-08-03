package com.macro.mall.distribution.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.dao.DmsShopMemberSessionDao;
import com.macro.mall.distribution.dto.AgentRegisterDTO;
import com.macro.mall.distribution.dto.AgentSwitchLineDTO;
import com.macro.mall.distribution.dto.AdminMemberCreateDTO;
import com.macro.mall.distribution.dto.ShopAccountSetupDTO;
import com.macro.mall.distribution.dto.ShopLoginDTO;
import com.macro.mall.distribution.dto.ShopPasswordChangeDTO;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
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
    private final StringRedisTemplate redisTemplate;
    private final AgentService agentService;
    private final LoginCaptchaService loginCaptchaService;
    private final SmsVerificationService smsVerificationService;

    /**
     * 获取短信验证码 Redis Key
     */
    private String getSmsCodeKey(int bizType, String phone) {
        return "sms:" + bizType + ":" + phone;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopAuthVO register(ShopRegisterDTO dto) {
        validateRegister(dto);
        dto.setPhone(dto.getPhone().trim());
        dto.setUsername(dto.getUsername() == null ? null : dto.getUsername().trim());
        if (memberDao.selectByPhone(dto.getPhone()) != null) {
            Asserts.fail("该手机号已注册，请直接登录或使用其他手机号");
        }
        if (memberDao.selectByUsername(dto.getPhone()) != null) {
            Asserts.fail("该手机号已被其他账号占用，请更换手机号或联系管理员");
        }
        if (dto.getUsername() != null && !dto.getUsername().isBlank()
                && memberDao.selectByAccount(dto.getUsername()) != null) {
            Asserts.fail("该用户名已被使用，请更换用户名");
        }

        // 强制验证短信验证码
        if (dto.getSmsCode() == null || dto.getSmsCode().isBlank()) {
            Asserts.fail("请输入短信验证码");
        }
        String cacheCode = redisTemplate.opsForValue().get(getSmsCodeKey(SMS_BIZ_TYPE_REGISTER, dto.getPhone()));
        if (cacheCode == null || !cacheCode.equals(dto.getSmsCode())) {
            Asserts.fail("验证码错误或已过期");
        }
        // 验证通过，删除已使用的验证码
        redisTemplate.delete(getSmsCodeKey(SMS_BIZ_TYPE_REGISTER, dto.getPhone()));

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
        member.setNickname(dto.getNickname() == null || dto.getNickname().isBlank()
                ? dto.getUsername() : dto.getNickname().trim());
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
        String username = dto.getUsername() == null ? "" : dto.getUsername().trim();
        if (username.length() < 2 || username.length() > 64) {
            Asserts.fail("登录账号需为2至64个字符");
        }
        if (username.equals(dto.getPhone())) {
            Asserts.fail("登录账号不能与手机号相同");
        }
        String initialPassword = dto.getPassword() == null ? "" : dto.getPassword();
        if (!initialPassword.isBlank() && (initialPassword.length() < 6 || initialPassword.length() > 32)) {
            Asserts.fail("初始密码需要6至32位");
        }
        if (memberDao.selectByAccount(dto.getPhone()) != null) Asserts.fail("该手机号已被注册或用作登录账号");
        if (memberDao.selectByAccount(username) != null) Asserts.fail("登录账号已存在");
        String nickname = dto.getNickname() == null ? "" : dto.getNickname().trim();
        if (nickname.length() > 64) Asserts.fail("昵称最多64个字符");
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
        member.setNickname(nickname.isBlank() ? username : nickname);
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
        if (AgentLevelEnum.getByValue(level) == null) Asserts.fail("会员级别不正确");
        if (reason == null || reason.isBlank()) Asserts.fail("请输入调级原因");
        DmsShopMember member = memberDao.selectById(memberId);
        if (member == null) Asserts.fail("商城账号不存在");
        AgentInfoVO agent = agentService.getAgentByUserId(member.getUserId());
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
        if (dto == null || dto.getUsername() == null || dto.getUsername().trim().length() < 2) Asserts.fail("会员账号至少2个字符");
        if (dto.getPassword() == null || dto.getPassword().length() < 6) Asserts.fail("密码至少需要6位");
        DmsShopMember same = memberDao.selectByUsername(dto.getUsername().trim());
        if (same != null && !same.getId().equals(member.getId())) Asserts.fail("会员账号已存在");
        memberDao.updateAccount(member.getId(), dto.getUsername().trim(), hash(dto.getPassword()));
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
            if (dto.getSmsCode() == null || dto.getSmsCode().isBlank()) {
                Asserts.fail("验证码不能为空");
            }
            String cacheCode = redisTemplate.opsForValue().get(getSmsCodeKey(SMS_BIZ_TYPE_LOGIN, account));
            if (cacheCode == null || !cacheCode.equals(dto.getSmsCode())) {
                Asserts.fail("验证码错误或已过期");
            }
            // 验证通过，删除已使用的验证码
            redisTemplate.delete(getSmsCodeKey(SMS_BIZ_TYPE_LOGIN, account));
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

        // 验证短信验证码
        String cacheCode = redisTemplate.opsForValue().get(getSmsCodeKey(SMS_BIZ_TYPE_RESET_PASSWORD, phone));
        if (cacheCode == null || !cacheCode.equals(smsCode)) {
            Asserts.fail("验证码错误或已过期");
        }

        // 查找会员
        DmsShopMember member = memberDao.selectByPhone(phone);
        if (member == null) {
            Asserts.fail("该手机号未注册");
        }

        // 更新密码（使用专用方法确保更新 password_hash 字段）
        memberDao.updatePassword(member.getId(), hash(newPassword));
        memberDao.clearLoginLock(member.getId());
        sessionDao.disableByMemberId(member.getId());

        // 删除已使用的验证码
        redisTemplate.delete(getSmsCodeKey(SMS_BIZ_TYPE_RESET_PASSWORD, phone));
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
        if (username.isEmpty()) {
            Asserts.fail("请输入用户名");
        }
        if (username.length() < 2 || username.length() > 64) {
            Asserts.fail("用户名需为2至64个字符");
        }
        if (dto.getNickname() != null && dto.getNickname().trim().length() > 64) {
            Asserts.fail("昵称最多64个字符");
        }
        if (dto.getPassword() == null || dto.getPassword().length() < 6 || dto.getPassword().length() > 32) {
            Asserts.fail("登录密码需为6至32位");
        }
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
