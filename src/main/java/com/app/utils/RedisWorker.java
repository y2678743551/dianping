package com.app.utils;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
@Component
@Slf4j
public class RedisWorker {
    private static final long BEGIN_TIMESTAMP=1640995200L;
    private static final int countBits=32;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix){
        LocalDateTime now=LocalDateTime.now();
        long nowSecond=now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp=nowSecond-BEGIN_TIMESTAMP;

        String date=now.format(DateTimeFormatter.ofPattern("yyy:MM:dd"));
        Long increment = stringRedisTemplate.opsForValue().increment("icr" + keyPrefix + ":" + date);
        return timeStamp<<countBits|increment;
    }
}
