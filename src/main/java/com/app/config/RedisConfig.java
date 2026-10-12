package com.app.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {

    private final String REDIS_PORT;
    private final String REDIS_HOST;
    private final String REDIS_PASSWORD;

    RedisConfig(@Value("${spring.data.redis.host}") String host,
                @Value("${spring.data.redis.port}") String port, @Value("${spring.data.redis.password}") String password) {
        REDIS_HOST = host;
        REDIS_PORT = port;
        REDIS_PASSWORD = password;
    }
    @Bean
    public RedissonClient redissonClient(){
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + REDIS_HOST + ":" + REDIS_PORT).setPassword(REDIS_PASSWORD);
        return Redisson.create(config);
    }
}
