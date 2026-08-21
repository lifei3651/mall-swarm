package com.macro.mall.distribution.service;

import com.macro.mall.distribution.entity.DmsFlashSaleActivity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/** 秒杀入口的 Redis 原子库存闸门。数据库原子扣减仍是最终库存真相。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlashSaleStockGate {

    public enum Result { ACQUIRED, SOLD_OUT, DUPLICATE, FALLBACK }

    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('EXISTS',KEYS[2])==1 then return -2 end; "
                    + "local raw=redis.call('GET',KEYS[1]); if not raw then return -3 end; "
                    + "local stock=tonumber(raw); "
                    + "local qty=tonumber(ARGV[1]); if stock < qty then return -1 end; "
                    + "redis.call('DECRBY',KEYS[1],qty); "
                    + "redis.call('SET',KEYS[2],ARGV[1],'EX',ARGV[2]); return stock-qty;",
            Long.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('DEL',KEYS[2])~=1 then return -1 end; "
                    + "if redis.call('EXISTS',KEYS[1])==1 then return redis.call('INCRBY',KEYS[1],ARGV[1]) end; "
                    + "redis.call('SET',KEYS[1],ARGV[2],'EX',ARGV[3]); return tonumber(ARGV[2]);",
            Long.class);
    private static final DefaultRedisScript<Long> RESTORE_STOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('EXISTS',KEYS[1])==1 then return redis.call('INCRBY',KEYS[1],ARGV[1]) end; "
                    + "redis.call('SET',KEYS[1],ARGV[2],'EX',ARGV[3]); return tonumber(ARGV[2]);",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    public Result acquire(DmsFlashSaleActivity activity, Long userId, int quantity) {
        String stockKey = stockKey(activity);
        String memberKey = memberKey(activity, userId);
        long ttl = ttlSeconds(activity);
        try {
            Long result = redisTemplate.execute(ACQUIRE_SCRIPT, List.of(stockKey, memberKey),
                    String.valueOf(quantity), String.valueOf(ttl));
            if (result == null) return Result.FALLBACK;
            if (result == -2L) return Result.DUPLICATE;
            // Redis 重启、活动配置刷新或缓存主动重建期间直接回退数据库原子守卫，
            // 不能把“缓存暂时不存在”误报为售罄，也不能用请求里的旧活动快照重建库存。
            if (result == -3L) return Result.FALLBACK;
            if (result < 0L) return Result.SOLD_OUT;
            return Result.ACQUIRED;
        } catch (RuntimeException ex) {
            log.warn("秒杀Redis闸门不可用，降级为数据库原子扣减：{}", ex.getClass().getSimpleName());
            return Result.FALLBACK;
        }
    }

    public void release(DmsFlashSaleActivity activity, Long userId, int quantity) {
        if (activity == null || userId == null) return;
        try {
            redisTemplate.execute(RELEASE_SCRIPT,
                    List.of(stockKey(activity), memberKey(activity, userId)), String.valueOf(quantity),
                    String.valueOf(Math.max(0, activity.getAvailableStock())), String.valueOf(ttlSeconds(activity)));
        } catch (RuntimeException ex) {
            log.warn("秒杀Redis库存回补失败，将在缓存重建后恢复：{}", ex.getClass().getSimpleName());
        }
    }

    /** 已支付后的退款只回补库存，不释放会员唯一参与标记。 */
    public void restoreStockOnly(DmsFlashSaleActivity activity, int quantity) {
        if (activity == null || quantity <= 0) return;
        try {
            redisTemplate.execute(RESTORE_STOCK_SCRIPT, List.of(stockKey(activity)),
                    String.valueOf(quantity), String.valueOf(Math.max(0, activity.getAvailableStock())),
                    String.valueOf(ttlSeconds(activity)));
        } catch (RuntimeException ex) {
            log.warn("秒杀退款Redis库存回补失败，将在缓存重建后恢复：{}", ex.getClass().getSimpleName());
        }
    }

    public void reset(DmsFlashSaleActivity activity) {
        if (activity == null || activity.getId() == null) return;
        try {
            // 配置保存后的活动对象是数据库最新值，直接写入准确库存；若 Redis 重启导致键缺失，
            // 抢购入口会安全降级到数据库，不再由并发请求使用过期快照懒初始化。
            redisTemplate.opsForValue().set(stockKey(activity),
                    String.valueOf(Math.max(0, activity.getAvailableStock())),
                    Duration.ofSeconds(ttlSeconds(activity)));
        } catch (RuntimeException ex) {
            log.warn("秒杀Redis库存缓存重建失败，将降级为数据库原子扣减：{}", ex.getClass().getSimpleName());
        }
    }

    private String stockKey(DmsFlashSaleActivity activity) {
        return "shop:flash:" + activity.getTenantId() + ":" + activity.getId() + ":stock";
    }

    private String memberKey(DmsFlashSaleActivity activity, Long userId) {
        return "shop:flash:" + activity.getTenantId() + ":" + activity.getId() + ":member:" + userId;
    }

    private long ttlSeconds(DmsFlashSaleActivity activity) {
        LocalDateTime end = activity.getEndTime() == null ? LocalDateTime.now().plusHours(1) : activity.getEndTime();
        return Math.max(60, Duration.between(LocalDateTime.now(), end.plusDays(1)).getSeconds());
    }
}
