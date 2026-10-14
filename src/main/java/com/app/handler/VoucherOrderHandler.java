package com.app.handler;

import cn.hutool.core.bean.BeanUtil;
import com.app.entity.VoucherOrder;
import com.app.service.ISeckillVoucherService;
import com.app.service.IVoucherOrderService;
import com.app.service.IVoucherService;
import jakarta.annotation.Resource;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
@Slf4j
//@Component
public class VoucherOrderHandler implements SmartLifecycle {
    @Resource
    private IVoucherOrderService voucherOrderService;
    @Resource private StringRedisTemplate stringRedisTemplate;
    @Resource private RedissonClient redissonClient;
    String queueName="stream.orders";
    private volatile boolean running=false;
    private static final ExecutorService SECKILL_ORDER_EXECUTOR= Executors.newSingleThreadExecutor();


    @Override
    public void start() {
        running=true;
        SECKILL_ORDER_EXECUTOR.submit(this::run);
    }

    @Override
    public void stop() {
        running=false;
        SECKILL_ORDER_EXECUTOR.shutdown();
        try {
            if (!SECKILL_ORDER_EXECUTOR.awaitTermination(500, TimeUnit.MILLISECONDS))
                SECKILL_ORDER_EXECUTOR.shutdownNow();
        } catch (InterruptedException e) {
            SECKILL_ORDER_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    @Override
    public boolean isRunning() {
        return running;
    }
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId= voucherOrder.getUserId();
        log.debug("消费到下单消息，准备抢锁: orderId={}, userId={}, voucherId={}",
                voucherOrder.getId(), userId, voucherOrder.getVoucherId());
        RLock lock =redissonClient.getLock("order:"+userId);

        // new SimpleRedisLock("order:"+userId,stringRedisTemplate);
        boolean isLock=lock.tryLock();
        if(!isLock){

            log.error("已经购买过");
            return ;
        }
        try{

            voucherOrderService.createVoucherOrder(voucherOrder);
        }finally {
            lock.unlock();
        }
    }
    private void run() {
        MapRecord<String, Object, Object> record=null;
        while (running){
            try {
                List<@NonNull MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1"),
                        StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                        StreamOffset.create(queueName, ReadOffset.lastConsumed())
                );
                if(list==null||list.isEmpty()){
                    continue;
                }
                record= list.get(0);
                Map<Object,Object> value=record.getValue();
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(),true);

                handleVoucherOrder(voucherOrder);

            } catch (Exception e) {
                log.error("处理订单异常:",e);
                handlerPendingList();
            } finally {
                if (record!=null)
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
            }
        }
    }
    private void handlerPendingList(){
        MapRecord<String, Object, Object> record=null;
        while (running){
            try {
                List<@NonNull MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1"),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(queueName, ReadOffset.from("0"))
                );
                if(list==null||list.isEmpty()){
                    break;
                }
                record = list.get(0);
                Map<Object,Object> value=record.getValue();

                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(),true);

                handleVoucherOrder(voucherOrder);
                stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
            } catch (Exception e) {
                log.error("处理订单异常:",e);
                break;
            }finally {
                if (record!=null)
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
            }
        }
    }


}