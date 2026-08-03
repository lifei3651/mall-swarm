package com.macro.mall.distribution.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.distribution.dao.*;
import com.macro.mall.distribution.dto.BonusSimulationDTO;
import com.macro.mall.distribution.entity.*;
import com.macro.mall.distribution.enums.AgentStatusEnum;
import com.macro.mall.distribution.service.BonusEngineConfigService;
import com.macro.mall.distribution.service.OperationLogService;
import com.macro.mall.distribution.service.PerformanceService;
import com.macro.mall.distribution.util.MemberAccountUtils;
import com.macro.mall.distribution.vo.BonusSimulationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.macro.mall.distribution.service.impl.NewRetailBonusPolicy.*;

@Service
@RequiredArgsConstructor
public class BonusEngineConfigServiceImpl implements BonusEngineConfigService {

    private final DmsTenantDisplayConfigDao displayConfigDao;
    private final DmsProductPvConfigDao productPvConfigDao;
    private final DmsOrderPvDetailDao orderPvDetailDao;
    private final DmsBonusCalculationSnapshotDao snapshotDao;
    private final DmsAgentDao agentDao;
    private final DmsAgentRelationDao relationDao;
    private final DmsCommissionRuleVersionDao ruleVersionDao;
    private final DmsShopMemberDao shopMemberDao;
    private final PerformanceService performanceService;
    private final TenantDisplayConfigSupport displayConfigSupport;

    @Override
    public DmsTenantDisplayConfig getDisplayConfig(Long tenantId) {
        if (tenantId == null) {
            Asserts.fail("租户ID不能为空");
        }
        return displayConfigSupport.prepareForRead(displayConfigDao.selectByTenantId(tenantId), tenantId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsTenantDisplayConfig saveDisplayConfig(DmsTenantDisplayConfig config) {
        if (config.getTenantId() == null) {
            Asserts.fail("租户ID不能为空");
        }
        displayConfigSupport.prepareForSave(config);
        DmsTenantDisplayConfig oldConfig = displayConfigDao.selectByTenantId(config.getTenantId());
        if (oldConfig == null) {
            displayConfigDao.insert(config);
        } else {
            config.setId(oldConfig.getId());
            displayConfigDao.update(config);
        }
        return displayConfigSupport.prepareForRead(displayConfigDao.selectByTenantId(config.getTenantId()), config.getTenantId());
    }

    @Override
    public List<DmsProductPvConfig> listProductPvConfigs(Long tenantId, String keyword, Integer status) {
        if (tenantId == null) {
            Asserts.fail("租户ID不能为空");
        }
        return productPvConfigDao.selectList(tenantId, keyword, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DmsProductPvConfig saveProductPvConfig(DmsProductPvConfig config) {
        if (config.getTenantId() == null) {
            Asserts.fail("租户ID不能为空");
        }
        if (config.getProductId() == null) {
            Asserts.fail("商品ID不能为空");
        }
        if (config.getProductName() == null || config.getProductName().isBlank()) {
            Asserts.fail("商品名称不能为空");
        }
        if (config.getPvValue() == null) {
            config.setPvValue(BigDecimal.ZERO);
        }
        if (config.getBvValue() == null) {
            config.setBvValue(BigDecimal.ZERO);
        }
        if (config.getCostAmount() == null) {
            config.setCostAmount(BigDecimal.ZERO);
        }
        if (config.getStatus() == null) {
            config.setStatus(1);
        }
        if (config.getId() == null) {
            productPvConfigDao.insert(config);
        } else {
            productPvConfigDao.update(config);
        }
        return productPvConfigDao.selectById(config.getId());
    }

    @Override
    public boolean updateProductPvStatus(Long id, Integer status) {
        return productPvConfigDao.updateStatus(id, status) > 0;
    }

    @Override
    public boolean deleteProductPvConfig(Long id) {
        return productPvConfigDao.deleteById(id) > 0;
    }

    @Override
    public List<DmsOrderPvDetail> listOrderPvDetails(Long orderId) {
        return orderPvDetailDao.selectByOrderId(orderId);
    }

    @Override
    public List<DmsBonusCalculationSnapshot> listCalculationSnapshots(Long orderId) {
        return snapshotDao.selectByOrderId(orderId);
    }

    @Override
    public BonusSimulationVO simulate(BonusSimulationDTO dto) {
        if (dto.getOrderMemberKey() != null && !dto.getOrderMemberKey().isBlank()) {
            DmsAgent resolved = agentDao.selectById(performanceService.resolveAgentId(dto.getOrderMemberKey()));
            dto.setOrderUserId(resolved.getUserId());
        }
        if (dto.getOrderUserId() == null) {
            Asserts.fail("请输入下单会员登录账号或手机号");
        }
        if (dto.getOrderAmount() == null || dto.getOrderAmount().compareTo(BigDecimal.ZERO) <= 0) {
            Asserts.fail("订单金额必须大于0");
        }
        DmsAgent orderAgent = agentDao.selectByUserId(dto.getOrderUserId());
        if (orderAgent == null) {
            Asserts.fail("下单用户不是代理");
        }
        Long tenantId = dto.getTenantId() == null ? 1L : dto.getTenantId();
        DmsCommissionRuleVersion version = ruleVersionDao.selectActiveByTenantId(tenantId);
        if (version == null || !VERSION_NO.equals(version.getVersionNo())) {
            Asserts.fail("当前客户未启用唯一的新零售正式奖金版本");
        }
        if (dto.getRuleVersionId() != null && !dto.getRuleVersionId().equals(version.getId())) {
            Asserts.fail("只能验证当前启用的新零售正式奖金版本");
        }
        List<BonusSimulationVO.BonusReceiverVO> receivers = new ArrayList<>();
        BigDecimal totalBonus = BigDecimal.ZERO;
        Set<Long> paidDirectorIds = new HashSet<>();
        Set<Integer> paidLevels = new HashSet<>();
        for (DmsAgentRelation relation : relationDao.selectValidRelationsByUserId(dto.getOrderUserId())) {
            if (relation.getRelationLevel() == null || relation.getRelationLevel() < 1) continue;
            DmsAgent parent = agentDao.selectById(relation.getParentAgentId());
            if (parent == null || !AgentStatusEnum.NORMAL.getValue().equals(parent.getStatus())) continue;

            if (relation.getRelationLevel() == 1) {
                BonusSimulationVO.BonusReceiverVO direct = buildSimulationReceiver(parent,
                        relation.getRelationLevel(), DIRECT_REWARD, "直推奖",
                        directRate(parent.getAgentLevel()), dto.getOrderAmount());
                if (direct != null) {
                    receivers.add(direct);
                    totalBonus = totalBonus.add(direct.getBonusAmount());
                }
            }

            if (paidDirectorIds.add(parent.getId()) && paidLevels.add(parent.getAgentLevel())) {
                BonusSimulationVO.BonusReceiverVO share = buildSimulationReceiver(parent,
                        relation.getRelationLevel(), DIRECTOR_SHARE, "董事团队分红",
                        directorShareRate(parent.getAgentLevel()), dto.getOrderAmount());
                if (share != null) {
                    receivers.add(share);
                    totalBonus = totalBonus.add(share.getBonusAmount());
                }
            }
        }
        BonusSimulationVO vo = new BonusSimulationVO();
        vo.setOrderAmount(dto.getOrderAmount());
        vo.setTotalBonus(totalBonus);
        vo.setReceivers(receivers);
        return vo;
    }

    private BonusSimulationVO.BonusReceiverVO buildSimulationReceiver(
            DmsAgent parent, Integer relationLevel, String bonusType, String bonusName,
            BigDecimal rate, BigDecimal orderAmount) {
        if (rate.compareTo(BigDecimal.ZERO) <= 0) return null;
        BigDecimal bonusAmount = orderAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        BonusSimulationVO.BonusReceiverVO receiver = new BonusSimulationVO.BonusReceiverVO();
        receiver.setAgentId(parent.getId());
        receiver.setUserId(parent.getUserId());
        DmsShopMember member = shopMemberDao.selectByUserId(parent.getUserId());
        receiver.setMemberAccount(MemberAccountUtils.display(member));
        receiver.setAgentName(parent.getAgentName());
        receiver.setRelationLevel(relationLevel);
        receiver.setBonusType(bonusType);
        receiver.setBonusName(bonusName);
        receiver.setRate(rate);
        receiver.setBonusAmount(bonusAmount);
        return receiver;
    }

}
