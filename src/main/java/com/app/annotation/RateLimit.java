package com.app.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 令牌桶限流。同一个方法可以叠多个，按不同维度分别限流。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RateLimit.List.class)
public @interface RateLimit {

    /** 限流 key 前缀，最终 key 会拼上维度后缀 */
    String prefix();

    /** 一个窗口内允许通过的最大请求数 */
    long count() default 10L;

    /** 窗口大小 */
    long window() default 1L;

    /** 窗口时间单位 */
    TimeUnit unit() default TimeUnit.SECONDS;

    /** 限流维度 */
    Dimension dimension() default Dimension.USER;

    /** dimension = PARAM 时，取第几个方法参数拼进 key */
    int paramIndex() default 0;

    enum Dimension {
        /** 按当前登录用户限流 */
        USER,
        /** 按方法参数限流 */
        PARAM,
        /** 全局限流，不拼后缀 */
        GLOBAL
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface List {
        RateLimit[] value();
    }
}
