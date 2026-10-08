package com.app.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.app.dto.Result;
import com.app.entity.ShopType;
import com.app.mapper.ShopTypeMapper;
import com.app.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.app.utils.RedisConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
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
@Slf4j
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeList() {
        String key = RedisConstants.CACHE_SHOP_TYPE_KEY;
        String shopType=stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isNotBlank(shopType)){
            List<ShopType> typeList= JSONUtil.toList(shopType,ShopType.class);
            return Result.ok(typeList);
        }
        if(shopType!=null){
             return Result.fail("未找到商店类型");
        }
        List<ShopType> typeList =   query().orderByAsc("sort").list();
        if(typeList==null||typeList.isEmpty()){
            stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);
            return Result.fail("未找到商店类型");
        }
        String s=JSONUtil.toJsonStr(typeList);
        log.debug("店铺类型:{}",s);
        stringRedisTemplate.opsForValue().set(key,s,RedisConstants.CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);
        return Result.ok(typeList);
    }
}
