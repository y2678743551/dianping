package com.app.config;

import com.app.utils.RedisConstants;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 店铺布隆过滤器。
 */
@Configuration
public class BloomFilterConfig {

    @Bean
    public RBloomFilter<Long> shopBloomFilter(RedissonClient redissonClient) {
        return redissonClient.getBloomFilter(RedisConstants.BLOOM_SHOP_KEY);
    }
}
