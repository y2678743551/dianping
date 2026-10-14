package com.app.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.annotation.Resource;
import org.springframework.cglib.core.internal.Function;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
@Component
public class CacheClient
{   @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private Cache<String, Object> localCache;


    private static final String LOCAL_CACHE_PREFIX = "cache:";


    private void set(String key,Object value,Long time,TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time,unit);

    }
    public <R ,ID> R queryWithMutex(String prefix, ID id, Class<R> type, Function<ID,R> dbFallback,TimeUnit unit) {
        String key = prefix+id;

        // 1. 本地一级缓存
        Object local = getLocalCache(key);
        if (local != null) {
            return type.cast(local);
        }

        // 2. Redis
        String shopJson=stringRedisTemplate.opsForValue().get(key);

        if(StrUtil.isNotBlank(shopJson)){

            R result = JSONUtil.toBean(shopJson,type);
            putLocalCache(key, result);
            return result;

        }
        if (shopJson!=null){

            return null;

        }
        R shop = dbFallback.apply(id);
        String lockKey=RedisConstants.LOCK_SHOP_KEY+id;
        try{
            boolean flag = tryLock(lockKey);
            if (!flag) {

                Thread.sleep(50);
                return queryWithMutex(prefix,id,type,dbFallback,unit);
            }


            if (shop == null) {
                set(key, "", RedisConstants.CACHE_NULL_TTL, unit);

                return null;

            }
            }catch (InterruptedException e){
            throw new RuntimeException(e);
        }
        finally {
            unlock(lockKey);
        }
        set(key,shop, RedisConstants.CACHE_SHOP_TTL, unit);
        putLocalCache(key, shop);

        return shop;
    }


    private Object getLocalCache(String key) {
        if (!key.startsWith(LOCAL_CACHE_PREFIX)) {
            return null;
        }
        return localCache.getIfPresent(key);
    }


    private void putLocalCache(String key, Object value) {
        if (value != null && key.startsWith(LOCAL_CACHE_PREFIX)) {
            localCache.put(key, value);
        }
    }


    public void evictLocalCache(String prefix, Object id) {
        localCache.invalidate(prefix + id);
    }


    private static final ExecutorService CACHE_EXECUTOR= Executors.newFixedThreadPool(10);


    public <R,ID>R queryWithLogicalExpire(String prefix, ID id, Class<R> type,
                                          Function<ID,R> dbFallback,String lockPrefix,TimeUnit unit) {
        String key = prefix+id;
        String shopJson=stringRedisTemplate.opsForValue().get(key);

        if(StrUtil.isBlank(shopJson)){

            return null;
        }
        RedisData redisData=JSONUtil.toBean(shopJson,RedisData.class);
        R shop=JSONUtil.toBean((JSONObject) redisData.getData(),type);
        LocalDateTime time=redisData.getExpireTime();

        if(time.isAfter(LocalDateTime.now())){
            return shop;
        }
        String lockKey=lockPrefix+id;

        boolean flag = tryLock(lockKey);


        if (flag) {
            try {
                CACHE_EXECUTOR.submit(() ->
                {   R r=dbFallback.apply(id);
                    this.saveShop2Redis(id, r,20L);
                });
            }catch (Exception e){
                throw new RuntimeException(e);
            }finally {
                unlock(lockKey);
            }


        }


        return shop;
    }
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag);
    }
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
    private <R>void saveShop2Redis(R id,Object value,Long expireSeconds){

        RedisData data=new RedisData();
        data.setData(value);
        data.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id, JSONUtil.toJsonStr(data),RedisConstants.CACHE_SHOP_TYPE_TTL,TimeUnit.MINUTES);
    }
}
