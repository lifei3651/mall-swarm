package com.macro.mall.distribution.service.impl;

import com.macro.mall.distribution.dao.DmsAgentRelationDao;
import com.macro.mall.distribution.entity.DmsAgentRelation;
import com.macro.mall.distribution.service.AgentRelationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 代理关系服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRelationServiceImpl implements AgentRelationService {

    private final DmsAgentRelationDao relationDao;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean bindRelation(Long userId, Long agentId, Long parentUserId, Long parentAgentId, Integer bindType) {
        // 1. 防止自己绑自己
        if (agentId.equals(parentAgentId)) {
            log.warn("不能自己绑定自己: agentId={}", agentId);
            return false;
        }

        // 2. 检查是否已存在有效关系
        List<DmsAgentRelation> existingRelations = relationDao.selectValidRelationsByUserId(userId);
        for (DmsAgentRelation existing : existingRelations) {
            if (existing.getParentAgentId().equals(parentAgentId)) {
                log.warn("关系已存在: userId={}, parentAgentId={}", userId, parentAgentId);
                return false;
            }
        }

        // 3. 防止循环绑定（检查 parentAgentId 是否是 agentId 的下级）
        List<DmsAgentRelation> descendants = relationDao.selectAllDescendants(agentId);
        for (DmsAgentRelation desc : descendants) {
            if (desc.getAgentId().equals(parentAgentId)) {
                log.warn("检测到循环绑定: agentId={}, parentAgentId={}", agentId, parentAgentId);
                return false;
            }
        }

        // 查询上级的所有有效关系（用于建立多级关系）
        List<DmsAgentRelation> parentRelations = relationDao.selectValidRelationsByUserId(parentUserId);

        // 创建直属关系
        DmsAgentRelation directRelation = new DmsAgentRelation();
        directRelation.setUserId(userId);
        directRelation.setAgentId(agentId);
        directRelation.setParentUserId(parentUserId);
        directRelation.setParentAgentId(parentAgentId);
        directRelation.setRelationLevel(1);
        directRelation.setRelationPath(parentAgentId + "/" + agentId);
        directRelation.setIsValid(1);
        directRelation.setBindType(bindType);
        directRelation.setBindTime(LocalDateTime.now());
        relationDao.insert(directRelation);

        // 创建全部间接上级关系。组织关系不限制层级，奖金发放层数由奖金规则单独控制。
        for (DmsAgentRelation parentRelation : parentRelations) {
            // 兼容历史数据中的“本人 level=0”占位关系，它不是上级关系。
            if (parentRelation.getParentAgentId() == null || parentRelation.getRelationLevel() == null
                    || parentRelation.getRelationLevel() < 1) {
                continue;
            }
            DmsAgentRelation indirectRelation = new DmsAgentRelation();
            indirectRelation.setUserId(userId);
            indirectRelation.setAgentId(agentId);
            indirectRelation.setParentUserId(parentRelation.getParentUserId());
            indirectRelation.setParentAgentId(parentRelation.getParentAgentId());
            indirectRelation.setRelationLevel(parentRelation.getRelationLevel() + 1);
            indirectRelation.setRelationPath(parentRelation.getRelationPath() + "/" + agentId);
            indirectRelation.setIsValid(1);
            indirectRelation.setBindType(bindType);
            indirectRelation.setBindTime(LocalDateTime.now());
            relationDao.insert(indirectRelation);
        }

        log.info("绑定代理关系成功: userId={}, agentId={}, parentUserId={}, parentAgentId={}",
                userId, agentId, parentUserId, parentAgentId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unbindRelation(Long userId, Long parentUserId, String unbindReason) {
        int result = relationDao.invalidRelation(userId, parentUserId, unbindReason);
        log.info("解绑代理关系: userId={}, parentUserId={}, result={}", userId, parentUserId, result);
        return result > 0;
    }

    @Override
    public List<DmsAgentRelation> getValidRelationsByUserId(Long userId) {
        return relationDao.selectValidRelationsByUserId(userId);
    }

    @Override
    public List<DmsAgentRelation> getDirectChildren(Long parentAgentId) {
        return relationDao.selectDirectChildren(parentAgentId);
    }

    @Override
    public List<DmsAgentRelation> getAllDescendants(Long parentAgentId) {
        return relationDao.selectAllDescendants(parentAgentId);
    }

    @Override
    public int getTeamMemberCount(Long agentId) {
        List<DmsAgentRelation> descendants = relationDao.selectAllDescendants(agentId);
        // 去重，因为同一个用户可能有多条关系记录
        Set<Long> userIds = new HashSet<>();
        for (DmsAgentRelation relation : descendants) {
            userIds.add(relation.getUserId());
        }
        return userIds.size();
    }

    @Override
    public int[] getLevelMemberCounts(Long agentId) {
        List<DmsAgentRelation> descendants = relationDao.selectAllDescendants(agentId);
        int[] counts = new int[3]; // [一级人数, 二级人数, 三级人数]

        // 按层级统计，去重
        Set<Long> level1Users = new HashSet<>();
        Set<Long> level2Users = new HashSet<>();
        Set<Long> level3Users = new HashSet<>();

        for (DmsAgentRelation relation : descendants) {
            switch (relation.getRelationLevel()) {
                case 1:
                    level1Users.add(relation.getUserId());
                    break;
                case 2:
                    level2Users.add(relation.getUserId());
                    break;
                case 3:
                    level3Users.add(relation.getUserId());
                    break;
                default:
                    break;
            }
        }

        counts[0] = level1Users.size();
        counts[1] = level2Users.size();
        counts[2] = level3Users.size();

        return counts;
    }
}
