package com.app.utils;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

@Component
@Slf4j
public class RedisWorker {
    private static final long BEGIN_TIMESTAMP=1640995200L;
    private static final int COUNT_BITS=32;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private static final DefaultRedisScript<Long> INCR_ID_SCRIPT;
    static{
        INCR_ID_SCRIPT=new DefaultRedisScript<>();
        INCR_ID_SCRIPT.setLocation(new ClassPathResource("incr-id.lua"));
        INCR_ID_SCRIPT.setResultType(Long.class);
    }
    public long nextId(String keyPrefix){
        LocalDateTime now=LocalDateTime.now();
        long nowSecond=now.toEpochSecond(ZoneOffset.UTC);
        long timestamp=nowSecond-BEGIN_TIMESTAMP;
        String date=now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        Long increment = stringRedisTemplate.execute(INCR_ID_SCRIPT, Collections.singletonList("icr" + keyPrefix + ":" + date), "86400");
        if (increment == null) {
            throw new IllegalStateException("id 生成失败，redis 返回 null");
        }

        return timestamp<<COUNT_BITS|increment;
    }
}
