package com.macro.mall.distribution.service;

import com.macro.mall.distribution.dao.DmsShopAfterSaleItemDao;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Execute the production mapper against isolated, minimal MySQL-compatible tables. */
class RefundHistoryMapperTest {
    @Test void validatesEachHistoricalLineRatherThanTrustingItsNetSum() throws Exception {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:refund-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE dms_shop_order_item(id BIGINT, order_id BIGINT, product_id BIGINT, sku_id BIGINT, quantity INT)");
        jdbc.execute("CREATE TABLE dms_shop_after_sale(id BIGINT, order_id BIGINT, apply_type INT, status INT)");
        jdbc.execute("CREATE TABLE dms_shop_after_sale_item(after_sale_id BIGINT, order_id BIGINT, order_item_id BIGINT, product_id BIGINT, sku_id BIGINT, refund_quantity INT)");
        jdbc.update("INSERT INTO dms_shop_order_item VALUES (10,1,100,NULL,1),(20,1,200,201,3)");
        jdbc.update("INSERT INTO dms_shop_after_sale VALUES (1,1,1,1),(2,1,2,0)");
        jdbc.update("INSERT INTO dms_shop_after_sale_item VALUES (1,1,10,100,NULL,-1),(2,1,10,100,NULL,2)");
        var config = new Configuration(new Environment("test", new JdbcTransactionFactory(), dataSource));
        try (var input = getClass().getResourceAsStream("/mapper/DmsShopAfterSaleItemMapper.xml")) {
            new XMLMapperBuilder(input, config, "refund-items", config.getSqlFragments()).parse();
        }
        try (SqlSession session = new SqlSessionFactoryBuilder().build(config).openSession(true)) {
            var dao = session.getMapper(DmsShopAfterSaleItemDao.class);
            assertEquals(1, dao.countInvalidReservedItemsByOrderId(1L), "negative plus positive must not cancel out");
            jdbc.update("UPDATE dms_shop_after_sale SET status=3 WHERE id=1");
            session.clearCache();
            assertEquals(1, dao.countInvalidReservedItemsByOrderId(1L), "positive 2 still exceeds original 1");
            jdbc.update("UPDATE dms_shop_after_sale_item SET refund_quantity=1 WHERE after_sale_id=2");
            session.clearCache();
            assertEquals(0, dao.countInvalidReservedItemsByOrderId(1L));
            jdbc.update("INSERT INTO dms_shop_after_sale_item VALUES (2,1,20,200,201,1),(2,1,20,200,201,2)");
            session.clearCache();
            assertEquals(0, dao.countInvalidReservedItemsByOrderId(1L), "legal duplicates and partial refunds remain valid");
            jdbc.update("UPDATE dms_shop_after_sale_item SET sku_id=999 WHERE order_item_id=20");
            session.clearCache();
            assertEquals(1, dao.countInvalidReservedItemsByOrderId(1L), "SKU ownership mismatch");
            jdbc.update("UPDATE dms_shop_after_sale_item SET sku_id=201, refund_quantity=2147483647 WHERE order_item_id=20");
            session.clearCache();
            assertEquals(1, dao.countInvalidReservedItemsByOrderId(1L), "SQL sum must not wrap to int");
            jdbc.update("UPDATE dms_shop_after_sale SET apply_type=3,status=1 WHERE id=2");
            session.clearCache();
            assertEquals(0, dao.countInvalidReservedItemsByOrderId(1L), "completed exchange does not reserve future refund quantities");
        } finally { jdbc.execute("SHUTDOWN"); }
    }
}
