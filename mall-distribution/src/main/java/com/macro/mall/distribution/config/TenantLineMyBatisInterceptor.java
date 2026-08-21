package com.macro.mall.distribution.config;

import com.macro.mall.common.tenant.TenantContext;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

@Intercepts({
        @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
public class TenantLineMyBatisInterceptor implements Interceptor {

    private static final Set<String> TENANT_TABLES = Set.of(
            // 商城相关（这些表有 tenant_id 字段）
            "dms_shop_product",
            "dms_shop_category",
            "dms_shop_banner",
            "dms_shop_notice",
            "dms_shop_order",
            "dms_shop_trade",
            "dms_shop_order_shipment",
            "dms_shop_product_review"
    );

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler statementHandler = unwrap((StatementHandler) invocation.getTarget());
        BoundSql boundSql = statementHandler.getBoundSql();
        String sql = boundSql.getSql();
        String scopedSql = addTenantCondition(sql);
        if (!sql.equals(scopedSql)) {
            Field sqlField = BoundSql.class.getDeclaredField("sql");
            sqlField.setAccessible(true);
            sqlField.set(boundSql, scopedSql);
        }
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }

    private StatementHandler unwrap(StatementHandler statementHandler) {
        MetaObject metaObject = SystemMetaObject.forObject(statementHandler);
        while (metaObject.hasGetter("h")) {
            Object object = metaObject.getValue("h");
            metaObject = SystemMetaObject.forObject(object);
        }
        if (metaObject.hasGetter("target")) {
            Object object = metaObject.getValue("target");
            metaObject = SystemMetaObject.forObject(object);
        }
        if (metaObject.hasGetter("delegate")) {
            return (StatementHandler) metaObject.getValue("delegate");
        }
        return statementHandler;
    }

    private String addTenantCondition(String sql) {
        if (sql == null || sql.isBlank()) {
            return sql;
        }
        String normalized = sql.replaceAll("\\s+", " ").trim();
        String lowerSql = normalized.toLowerCase(Locale.ROOT);
        // 只有在 WHERE 条件中已经存在 tenant_id 过滤时才跳过
        if (hasTenantInWhereClause(lowerSql)) {
            return sql;
        }
        String table = findTenantTable(lowerSql);
        if (table == null) {
            return sql;
        }
        if (lowerSql.startsWith("select ")) {
            return appendSelectTenantCondition(normalized);
        }
        if (lowerSql.startsWith("update ") || lowerSql.startsWith("delete ")) {
            return appendWriteTenantCondition(normalized);
        }
        return sql;
    }

    private boolean hasTenantInWhereClause(String lowerSql) {
        // 查找 WHERE 关键字的位置
        int whereIndex = lowerSql.indexOf(" where ");
        if (whereIndex < 0) {
            return false;
        }
        // 检查 WHERE 子句中是否有 tenant_id 条件
        String afterWhere = lowerSql.substring(whereIndex + 7);
        return afterWhere.contains("tenant_id =") || afterWhere.contains("tenant_id=")
                || afterWhere.contains("tenant_id, 1) =") || afterWhere.contains("tenant_id,1)=");
    }

    private String findTenantTable(String lowerSql) {
        for (String table : TENANT_TABLES) {
            if (lowerSql.contains(" " + table + " ") || lowerSql.endsWith(" " + table)
                    || lowerSql.contains("`" + table + "`")) {
                return table;
            }
        }
        return null;
    }

    private String appendSelectTenantCondition(String sql) {
        String lowerSql = sql.toLowerCase(Locale.ROOT);
        int orderIndex = keywordIndex(lowerSql, " order by ");
        int groupIndex = keywordIndex(lowerSql, " group by ");
        int limitIndex = keywordIndex(lowerSql, " limit ");
        int forUpdateIndex = keywordIndex(lowerSql, " for update");
        int suffixIndex = firstPositive(orderIndex, groupIndex, limitIndex, forUpdateIndex);
        String mainSql = suffixIndex < 0 ? sql : sql.substring(0, suffixIndex);
        String suffixSql = suffixIndex < 0 ? "" : sql.substring(suffixIndex);
        String connector = mainSql.toLowerCase(Locale.ROOT).contains(" where ") ? " AND " : " WHERE ";
        return mainSql + connector + "tenant_id = " + TenantContext.getTenantId() + suffixSql;
    }

    private String appendWriteTenantCondition(String sql) {
        String lowerSql = sql.toLowerCase(Locale.ROOT);
        if (!lowerSql.contains(" where ")) {
            return sql + " WHERE tenant_id = " + TenantContext.getTenantId();
        }
        return sql + " AND tenant_id = " + TenantContext.getTenantId();
    }

    private int keywordIndex(String lowerSql, String keyword) {
        return lowerSql.indexOf(keyword);
    }

    private int firstPositive(int... indexes) {
        int result = -1;
        for (int index : indexes) {
            if (index >= 0 && (result < 0 || index < result)) {
                result = index;
            }
        }
        return result;
    }
}
