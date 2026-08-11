package com.macro.mall.distribution.service;

import com.alibaba.druid.pool.DruidDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/** 核心表容量与连接池水位只读预警，不修改数据、不阻断交易。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseCapacityMonitor {
    private static final String CAPACITY_SQL = """
            SELECT table_name, table_rows, data_length, index_length
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name IN ('dms_shop_order','dms_shop_order_item','dms_shop_after_sale',
                                 'dms_member_asset_flow','dms_operation_log')
            """;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    @Value("${database.monitor.row-warning-threshold:1000000}") private long rowWarningThreshold;
    @Value("${database.monitor.size-warning-mb:2048}") private long sizeWarningMegabytes;
    @Value("${database.monitor.pool-warning-percent:80}") private int poolWarningPercent;

    @Scheduled(cron = "${database.monitor.capacity-cron:0 15 3 * * ?}")
    public void inspect() {
        inspectTableCapacity();
        inspectConnectionPool();
    }

    void inspectTableCapacity() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(CAPACITY_SQL);
            for (Map<String, Object> row : rows) {
                String table = String.valueOf(value(row, "table_name"));
                long tableRows = number(value(row, "table_rows"));
                long megabytes = (number(value(row, "data_length")) + number(value(row, "index_length"))) / 1024L / 1024L;
                if (tableRows >= rowWarningThreshold || megabytes >= sizeWarningMegabytes) {
                    log.warn("DB_CAPACITY_WARNING table={} estimatedRows={} sizeMb={} rowThreshold={} sizeThresholdMb={}",
                            table, tableRows, megabytes, rowWarningThreshold, sizeWarningMegabytes);
                }
            }
        } catch (Exception ex) {
            log.warn("DB_CAPACITY_CHECK_FAILED reason={}", ex.getClass().getSimpleName());
        }
    }

    void inspectConnectionPool() {
        try {
            DruidDataSource druid = dataSource.isWrapperFor(DruidDataSource.class)
                    ? dataSource.unwrap(DruidDataSource.class)
                    : (dataSource instanceof DruidDataSource source ? source : null);
            if (druid == null || druid.getMaxActive() <= 0) return;
            int active = druid.getActiveCount();
            int percent = active * 100 / druid.getMaxActive();
            if (percent >= poolWarningPercent) {
                log.warn("DB_POOL_CAPACITY_WARNING active={} maxActive={} usagePercent={} thresholdPercent={}",
                        active, druid.getMaxActive(), percent, poolWarningPercent);
            }
        } catch (Exception ex) {
            log.warn("DB_POOL_CAPACITY_CHECK_FAILED reason={}", ex.getClass().getSimpleName());
        }
    }

    private Object value(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value != null ? value : row.get(key.toUpperCase());
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
