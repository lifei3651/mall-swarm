package com.macro.mall.distribution.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.distribution.dao.DmsShopOrderDao;
import com.macro.mall.distribution.entity.DmsShopOrder;
import com.macro.mall.distribution.vo.ShopOrderVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ShopOrderServiceRemarkTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DmsShopOrderDao orderDao;
    @Autowired private ShopService shopService;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void serviceRemarkIsSearchableAuditedAndHiddenFromMemberOrders() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO dms_shop_order
                (id,order_no,tenant_id,user_id,receiver_name,receiver_phone,receiver_address,
                 total_amount,freight_amount,discount_amount,pay_amount,total_pv,total_cost,status,remark)
                VALUES (9910001,'SERVICE_REMARK_9910001',1,9910002,'备注客户','13800000000','测试地址',
                        99,0,0,99,0,0,1,'客户要求尽快发货')
                """);

        assertTrue(shopService.updateOrderServiceRemark(9910001L, "  已电话确认，周末配送  "));
        DmsShopOrder saved = orderDao.selectById(9910001L);
        assertEquals("已电话确认，周末配送", saved.getServiceRemark());
        assertEquals(1, orderDao.selectList("周末配送", null, null).size());

        List<ShopOrderVO> adminOrders = shopService.listAdminOrders("SERVICE_REMARK_9910001", null, null);
        assertEquals("已电话确认，周末配送", adminOrders.get(0).getServiceRemark());

        ShopOrderVO memberOrder = shopService.listOrders(9910002L, null).get(0);
        assertNull(memberOrder.getServiceRemark());
        String memberJson = objectMapper.writeValueAsString(memberOrder);
        assertFalse(memberJson.contains("serviceRemark"));
        assertFalse(memberJson.contains("周末配送"));

        Integer auditCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM dms_operation_log
                WHERE module_name='SHOP_ORDER' AND operation_type='SERVICE_REMARK_UPDATE'
                  AND target_type='SHOP_ORDER' AND target_id='9910001'
                """, Integer.class);
        assertEquals(1, auditCount);

        assertTrue(shopService.updateOrderServiceRemark(9910001L, ""));
        assertNull(orderDao.selectById(9910001L).getServiceRemark());
    }
}
