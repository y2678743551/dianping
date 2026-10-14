package com.app.aspect;

import com.app.annotation.RateLimit;
import com.app.dto.Result;
import com.app.dto.UserDTO;
import com.app.utils.UserHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;


@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    private static final String KEY_PREFIX = "rate:";

    @Resource
    private RedissonClient redissonClient;

    @Around("execution(com.app.dto.Result com.app.controller..*(..)) && @annotation(com.app.annotation.RateLimit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();

        for (RateLimit limit : method.getAnnotationsByType(RateLimit.class)) {
            String key = buildKey(limit, joinPoint);
            if (key == null) {

                continue;
            }

            RRateLimiter limiter = redissonClient.getRateLimiter(key);

            limiter.trySetRate(RateType.OVERALL, limit.count(), limit.window(), toIntervalUnit(limit.unit()));

            if (!limiter.tryAcquire()) {
                log.warn("触发限流: key={}, 配额={}/{}{}", key, limit.count(), limit.window(), limit.unit());
                return Result.fail("请求过于频繁，请稍后再试");
            }
        }

        return joinPoint.proceed();
    }

    private String buildKey(RateLimit limit, ProceedingJoinPoint joinPoint) {
        String key = KEY_PREFIX + limit.prefix();

        return switch (limit.dimension()) {
            case USER -> {
                UserDTO user = UserHolder.getUser();
                yield user == null ? null : key + ":" + user.getId();
            }
            case PARAM -> {
                Object[] args = joinPoint.getArgs();
                int index = limit.paramIndex();
                yield (index < args.length && args[index] != null) ? key + ":" + args[index] : null;
            }
            case GLOBAL -> key;
        };
    }

    private RateIntervalUnit toIntervalUnit(TimeUnit unit) {
        return switch (unit) {
            case MILLISECONDS -> RateIntervalUnit.MILLISECONDS;
            case SECONDS -> RateIntervalUnit.SECONDS;
            case MINUTES -> RateIntervalUnit.MINUTES;
            case HOURS -> RateIntervalUnit.HOURS;
            case DAYS -> RateIntervalUnit.DAYS;
            // NANOSECONDS / MICROSECONDS 精度高于 Redisson 支持的最小粒度，按秒处理
            default -> RateIntervalUnit.SECONDS;
        };
    }
}
