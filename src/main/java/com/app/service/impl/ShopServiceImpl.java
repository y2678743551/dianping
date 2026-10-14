package com.app.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.app.dto.Result;
import com.app.entity.Shop;
import com.app.mapper.ShopMapper;
import com.app.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.app.utils.CacheClient;
import com.app.utils.RedisConstants;
import com.app.utils.RedisData;
import com.app.utils.SystemConstants;
import jakarta.annotation.Resource;
import org.redisson.api.RBloomFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private CacheClient cacheClient;
    @Resource
    private RBloomFilter<Long> shopBloomFilter;
    @Override
    public Result queryById(Long id) {
        // 布隆过滤器挡在最前面：一定不存在的 id 不进 Redis、不进 DB
        if (!shopBloomFilter.contains(id)) {
            return Result.fail("店铺不存在");
        }
        Shop shop=cacheClient.queryWithMutex(RedisConstants.CACHE_SHOP_KEY,id,Shop.class,this::getById,TimeUnit.MINUTES);
        if(shop!=null)
            return Result.ok(shop);
        return Result.fail("店铺不存在");
    }
    public Shop queryWithMutex(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY+id;
        String shopJson=stringRedisTemplate.opsForValue().get(key);
        Shop shop=new Shop();
        if(StrUtil.isNotBlank(shopJson)){

            shop= JSONUtil.toBean(shopJson,Shop.class);
            return shop;
        }
        if (shopJson!=null){

            return null;

        }

        String lockKey=RedisConstants.LOCK_SHOP_KEY+id;
        try{
            boolean flag = tryLock(lockKey);
            if (!flag) {

                Thread.sleep(50);
                return queryWithMutex(id);
            }
            shop = getById(id);

            if (shop == null) {
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);

                return null;

            }
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        }catch (InterruptedException e){
            throw new RuntimeException(e);
        }
        finally {
            unlock(lockKey);
        }


        return shop;
    }
    private static final ExecutorService CACHE_EXECUTOR= Executors.newFixedThreadPool(10);
    public Shop queryWithLogicalExpire(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY+id;
        String shopJson=stringRedisTemplate.opsForValue().get(key);

        if(StrUtil.isBlank(shopJson)){

            return null;
        }
        RedisData redisData=JSONUtil.toBean(shopJson,RedisData.class);
        Shop shop=JSONUtil.toBean((JSONObject) redisData.getData(),Shop.class);
        LocalDateTime time=redisData.getExpireTime();

        if(time.isAfter(LocalDateTime.now())){
            return shop;
        }
        String lockKey=RedisConstants.LOCK_SHOP_KEY+id;

        boolean flag = tryLock(lockKey);


        if (flag) {
            try {
                CACHE_EXECUTOR.submit(() ->
                {
                    this.saveShop2Redis(id, 20L);
                });
            }catch (Exception e){
                throw new RuntimeException(e);
            }finally {
                unlock(lockKey);
            }


        }


        return shop;
    }


    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id=shop.getId();
        if(id==null){
            return Result.fail("店铺id不存在");
        }
        updateById(shop);
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY+id);
        cacheClient.evictLocalCache(RedisConstants.CACHE_SHOP_KEY, id);
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        if(x==null||y==null){
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        int from =(current-1)*SystemConstants.DEFAULT_PAGE_SIZE;
        int end=current*SystemConstants.DEFAULT_PAGE_SIZE;
        String key=RedisConstants.SHOP_GEO_KEY+typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));
        if(results==null){

            return Result.ok();
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();

        if(list.size()<from){

            return Result.ok();
        }
        Map<String,Distance> map=new HashMap<>(list.size());

        List<Long>ids=new ArrayList<>(list.size());


        list.stream().skip(from).forEach(result->{
            String name = result.getContent().getName();
            ids.add(Long.valueOf(name));
            Distance distance=result.getDistance();
            map.put(name, distance);
        });
        String join = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("order by field(id," + join + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(map.get(shop.getId().toString()).getValue());
        }
        return Result.ok(shops);
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag);
    }
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
    private void saveShop2Redis(Long id,Long expireSeconds){
        Shop shop=getById(id);
        RedisData data=new RedisData();
        data.setData(id);
        data.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(data),RedisConstants.CACHE_SHOP_TYPE_TTL,TimeUnit.MINUTES);
    }
}
