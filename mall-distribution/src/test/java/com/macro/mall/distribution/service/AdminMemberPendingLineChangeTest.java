package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dao.DmsLineChangeApplicationDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.vo.AdminMemberVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminMemberPendingLineChangeTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DmsShopMemberDao memberDao;
    @Autowired private DmsLineChangeApplicationDao lineChangeApplicationDao;
    @Autowired private SqlSessionTemplate sqlSessionTemplate;

    @Test
    void adminListExposesPendingMoveAndClearsItAfterProcessing() {
        long memberId = 9001L;
        long userId = 9901L;
        long agentId = 9001L;
        jdbcTemplate.update("INSERT INTO dms_shop_member(id,user_id,phone,username,password_hash,nickname,invite_code,status) VALUES(?,?,?,?,?,?,?,1)",
                memberId, userId, "13900009001", "pending-member", "test-hash", "待移线会员", "P9001");
        jdbcTemplate.update("INSERT INTO dms_agent(id,user_id,agent_code,agent_name,agent_level,level_depth,invite_code,status,source_type) VALUES(?,?,?,?,1,1,?,1,3)",
                agentId, userId, "AG-PENDING-9001", "待移线会员", "AP9001");
        jdbcTemplate.update("INSERT INTO dms_line_change_application(apply_no,agent_id,new_parent_agent_id,reason,status,applicant_id,applicant_name,effective_time,before_snapshot) VALUES(?,?,?,?,0,?,?,CURRENT_TIMESTAMP,?)",
                "LINE-PENDING-9001", agentId, 1L, "测试待处理状态", 1L, "admin", "{}");

        AdminMemberVO pending = memberDao.selectAdminList("13900009001", null, null, null).get(0);
        assertTrue(pending.getHasPendingLineChange());
        assertTrue(lineChangeApplicationDao.selectPendingAgentIds(List.of(agentId)).contains(agentId));

        jdbcTemplate.update("UPDATE dms_line_change_application SET status=3 WHERE agent_id=?", agentId);
        sqlSessionTemplate.clearCache();
        AdminMemberVO processed = memberDao.selectAdminList("13900009001", null, null, null).get(0);
        assertFalse(processed.getHasPendingLineChange());
        assertTrue(lineChangeApplicationDao.selectPendingAgentIds(List.of(agentId)).isEmpty());
    }
}
