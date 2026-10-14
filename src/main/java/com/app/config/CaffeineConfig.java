package com.app.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 本地一级缓存。
 */
@Configuration
public class CaffeineConfig {


    private static final long MAX_SIZE = 1000L;

    private static final long EXPIRE_MINUTES = 5L;

    @Bean
    public Cache<String, Object> localCache() {
        return Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(MAX_SIZE)
                .expireAfterWrite(EXPIRE_MINUTES, TimeUnit.MINUTES)
                .build();
    }
}
