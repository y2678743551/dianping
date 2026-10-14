package com.app.config;

import com.app.entity.Shop;
import com.app.service.IShopService;
import com.app.utils.RedisConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时把已存在的店铺 id 装载进布隆过滤器。
 */
@Slf4j
@Component
public class BloomFilterInitializer implements CommandLineRunner {

    @Resource
    private IShopService shopService;
    @Resource
    private RBloomFilter<Long> shopBloomFilter;

    @Override
    public void run(String... args) {
        boolean inited = shopBloomFilter.tryInit(RedisConstants.BLOOM_SHOP_SIZE, RedisConstants.BLOOM_SHOP_ERROR_RATE);
        if (!inited) {
            log.info("店铺布隆过滤器已存在，跳过数据装载");
            return;
        }
        List<Shop> shops = shopService.list();
        shops.forEach(shop -> shopBloomFilter.add(shop.getId()));
        log.info("店铺布隆过滤器初始化完成，已装载 {} 个店铺 id", shops.size());
    }
}
