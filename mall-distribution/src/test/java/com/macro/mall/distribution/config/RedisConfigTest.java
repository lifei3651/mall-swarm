package com.macro.mall.distribution.config;

import com.macro.mall.distribution.entity.DmsShopProduct;
import com.macro.mall.distribution.vo.ShopProductDetailVO;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

class RedisConfigTest {

    @Test
    void catalogSerializerSupportsBusinessTypesAndJavaTime() {
        RedisTemplate<String, Object> template = new RedisConfig()
                .redisTemplate(mock(RedisConnectionFactory.class));
        DmsShopProduct product = new DmsShopProduct();
        product.setId(9L);
        product.setProductName("缓存测试商品");
        product.setCreateTime(LocalDateTime.of(2026, 8, 11, 13, 30));
        ShopProductDetailVO source = new ShopProductDetailVO();
        source.setProduct(product);
        source.setSkus(List.of());

        @SuppressWarnings("unchecked")
        RedisSerializer<Object> serializer = (RedisSerializer<Object>) template.getValueSerializer();
        byte[] bytes = serializer.serialize(source);
        Object restored = serializer.deserialize(bytes);

        ShopProductDetailVO detail = assertInstanceOf(ShopProductDetailVO.class, restored);
        assertEquals(9L, detail.getProduct().getId());
        assertEquals(product.getCreateTime(), detail.getProduct().getCreateTime());
    }
}
