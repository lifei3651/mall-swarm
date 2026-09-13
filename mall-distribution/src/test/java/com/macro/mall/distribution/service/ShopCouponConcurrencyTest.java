package com.macro.mall.distribution.service;
import com.macro.mall.common.tenant.TenantContext;
import com.macro.mall.distribution.dao.DmsShopCouponDao;
import com.macro.mall.distribution.dao.DmsShopMemberDao;
import com.macro.mall.distribution.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test")
class ShopCouponConcurrencyTest {
    @Autowired ShopCouponService service;
    @Autowired JdbcTemplate db;
    @Autowired DmsShopCouponDao dao;
    @Autowired DmsShopMemberDao members;
    @Autowired TransactionTemplate tx;
    @Test void lastCouponAndSameClaimCanOnlyBeAcquiredOnceUnderConcurrency() throws Exception {
        long coupon=99887766001L,user1=99887766002L,user2=99887766003L;
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            db.update("INSERT INTO dms_shop_coupon(id,tenant_id,title,merchant_name,scope_type,product_ids_json,business_types_json,amount,minimum_amount,merchant_percent,bonus_basis,refund_rule,starts_at,ends_at,total_count,per_member_limit,status) VALUES(?,1,'并发测试','平台自营','ALL','[]','[\"NORMAL\"]',1,0,0,'NET','FULL_RETURN',DATEADD('HOUR',-1,CURRENT_TIMESTAMP),DATEADD('DAY',1,CURRENT_TIMESTAMP),1,1,'PUBLISHED')",coupon);
            for(long u:new long[]{user1,user2})db.update("INSERT INTO dms_shop_member(user_id,phone,password_hash,status) VALUES(?,?,'test-fixture',1)",u,"139"+String.valueOf(u).substring(3));
            CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
            java.util.List<Future<Long>> tasks=new java.util.ArrayList<>();
            for(long u:new long[]{user1,user2})tasks.add(pool.submit(()->{TenantContext.setTenantId(1L);try{DmsShopMember m=members.selectByUserId(u);ready.countDown();go.await();return service.claim(m,coupon,"concurrent-coupon-"+u).getClaimId();}catch(com.macro.mall.common.exception.ApiException exhausted){return null;}finally{TenantContext.clear();}}));
            assertTrue(ready.await(5,TimeUnit.SECONDS));go.countDown();
            Long first=tasks.get(0).get(10,TimeUnit.SECONDS),second=tasks.get(1).get(10,TimeUnit.SECONDS);
            assertTrue((first==null) != (second==null));
            long claim=first==null?second:first,winner=first==null?user2:user1;
            TenantContext.setTenantId(1L);DmsShopMember m=members.selectByUserId(winner);
            assertEquals(claim,service.claim(m,coupon,"concurrent-coupon-"+winner).getClaimId());
            assertEquals(1,dao.get(1L,coupon).getIssuedCount());
            CountDownLatch reserveGo=new CountDownLatch(1);
            Future<Integer> a=pool.submit(()->{reserveGo.await();return tx.execute(s->dao.reserve(1L,m.getId(),claim,1L));});
            Future<Integer> b=pool.submit(()->{reserveGo.await();return tx.execute(s->dao.reserve(1L,m.getId(),claim,2L));});
            reserveGo.countDown();assertEquals(1,a.get(10,TimeUnit.SECONDS)+b.get(10,TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();pool.awaitTermination(10,TimeUnit.SECONDS);TenantContext.clear();
            db.update("DELETE FROM dms_shop_coupon_claim WHERE coupon_id=?",coupon);
            db.update("DELETE FROM dms_shop_coupon WHERE id=?",coupon);
            db.update("DELETE FROM dms_shop_member WHERE user_id IN (?,?)",user1,user2);
        }
    }
}
