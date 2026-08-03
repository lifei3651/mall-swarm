package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dao.DmsAgentChangeLogDao;
import com.macro.mall.distribution.dao.DmsAgentAccountDao;
import com.macro.mall.distribution.dao.DmsAgentDao;
import com.macro.mall.distribution.dao.DmsMigrationBaselineDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.entity.DmsAgent;
import com.macro.mall.distribution.entity.DmsAgentChangeLog;
import com.macro.mall.distribution.entity.DmsMigrationBaseline;
import com.macro.mall.distribution.entity.DmsShopMember;
import com.macro.mall.distribution.vo.ImportResultVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MemberMigrationServiceTest {
    @Autowired private AgentService agentService;
    @Autowired private ExternalTeamMigrationService migrationService;
    @Autowired private DmsAgentDao agentDao;
    @Autowired private DmsAgentChangeLogDao changeLogDao;
    @Autowired private DmsShopMemberDao memberDao;
    @Autowired private DmsMigrationBaselineDao baselineDao;
    @Autowired private DmsAgentAccountDao accountDao;

    @Test
    void manualLevelAdjustmentTakesEffectImmediatelyAndWritesLog() {
        agentService.adjustLevel(1L, 6, "测试后台直接调级");

        assertEquals(6, agentDao.selectById(1L).getAgentLevel());
        List<DmsAgentChangeLog> logs = changeLogDao.selectByAgentId(1L);
        assertTrue(logs.stream().anyMatch(log -> Integer.valueOf(5).equals(log.getOldLevel())
                && Integer.valueOf(6).equals(log.getNewLevel())
                && "测试后台直接调级".equals(log.getChangeReason())
                && log.getChangeDetail().contains("future_orders_only")));
    }

    @Test
    void externalTeamMigrationRebuildsTreeAndPreservesHistoricalBaseline() {
        String csv = "外部会员编号,手机号,昵称,外部上级编号,初始级别,历史累计有效商品件数,历史个人业绩,历史团队业绩,备注\n"
                + "EXT-A,13900001001,迁入根会员,,4,150,15000.00,68000.00,\n"
                + "EXT-B,13900001002,迁入下级,EXT-A,2,10,1000.00,3000.00,\n";
        MockMultipartFile file = new MockMultipartFile("file", "external-team.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        ImportResultVO result = migrationService.migrate(file, null);

        assertEquals(2, result.getSuccessCount());
        DmsShopMember rootMember = memberDao.selectByPhone("13900001001");
        DmsShopMember childMember = memberDao.selectByPhone("13900001002");
        assertNotNull(rootMember);
        assertEquals(rootMember.getUserId(), childMember.getInviterId());

        DmsAgent root = agentDao.selectByUserId(rootMember.getUserId());
        DmsAgent child = agentDao.selectByUserId(childMember.getUserId());
        assertEquals(root.getId(), child.getParentId());
        assertEquals(4, root.getAgentLevel());
        assertEquals(2, child.getAgentLevel());

        DmsMigrationBaseline baseline = baselineDao.selectByAgentId(root.getId());
        assertEquals("EXT-A", baseline.getExternalMemberCode());
        assertEquals(150, baseline.getHistoricalOrderCount());
        assertEquals(0, baseline.getHistoricalPersonalPerformance().compareTo(new java.math.BigDecimal("15000.00")));
        assertEquals(150, accountDao.selectByAgentId(root.getId()).getTotalOrders());
        assertEquals(10, accountDao.selectByAgentId(child.getId()).getTotalOrders());
    }
}
