package com.app.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.app.dto.Result;
import com.app.entity.VoucherOrder;
import com.app.mapper.VoucherOrderMapper;
import com.app.service.ISeckillVoucherService;
import com.app.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.app.utils.RedisWorker;
import com.app.utils.UserHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.BeanUtils;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisWorker redisWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static{
        SECKILL_SCRIPT=new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    private IVoucherOrderService proxy;
    //private BlockingQueue<VoucherOrder> orderTasks=new ArrayBlockingQueue<>(1024*1024);
    private static final ExecutorService SECKILL_ORDER_EXECUTOR= Executors.newSingleThreadExecutor();

    private class VoucherOrderHandler implements SmartLifecycle {
        String queueName="stream.orders";
        private boolean running=false;
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
                if (SECKILL_ORDER_EXECUTOR.awaitTermination(500, TimeUnit.MILLISECONDS))
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
        private void run() {
            while (true){
                try {
                    List<@NonNull MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    if(list==null||list.isEmpty()){
                        continue;
                    }
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object,Object> value=record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(),true);
                    handleVoucherOrder(voucherOrder);
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常:",e);
                    handlerPendingList();
                }
            }
        }

        private void handlerPendingList(){
            while (true){
                try {
                    List<@NonNull MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    if(list==null||list.isEmpty()){
                        break;
                    }
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object,Object> value=record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(),true);
                    handleVoucherOrder(voucherOrder);
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常:",e);

                }
            }
        }


    }
//    @PostConstruct
//    private void init(){
//        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
//    }
//    private class VoucherOrderHandler implements Runnable{
//
//        @Override
//        public void run() {
//            while (true){
//                try {
//                    VoucherOrder voucherOrder=orderTasks.take();
//                    handleVoucherOrder(voucherOrder);
//                } catch (InterruptedException e) {
//                    log.error("处理订单异常:",e);
//                }
//            }
//        }
//    }
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId= voucherOrder.getUserId();
        RLock lock =redissonClient.getLock("order:"+userId);

        // new SimpleRedisLock("order:"+userId,stringRedisTemplate);
        boolean isLock=lock.tryLock();
        if(!isLock){

            log.error("已经购买过");
            return ;
        }
        try{

            proxy.createVoucherOrder(voucherOrder);
        }finally {
            lock.unlock();
        }
    }
    @Override
    public Result secondKillVocher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();

        Long orderId = redisWorker.nextId("order");

        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                orderId.toString());
        if (result != 0) {
            return Result.fail(result == 1 ? "库存不足" : "不能重复下单");

        }

        proxy = (IVoucherOrderService) AopContext.currentProxy();
        return Result.ok(orderId);
    }
//    @Override
//    public Result secondKillVocher(Long voucherId) {
//        Long userId = UserHolder.getUser().getId();
//        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
//                Collections.emptyList(),
//                voucherId.toString(),
//                userId.toString());
//        if (result != 0) {
//            return Result.fail(result == 1 ? "库存不足" : "不能重复下单");
//
//        }
//        long orderId = redisWorker.nextId("order");
//        VoucherOrder voucherOrder=new VoucherOrder();
//
//        voucherOrder.setId(orderId);
//
//        voucherOrder.setUserId(userId);
//        voucherOrder.setVoucherId(voucherId);
//
//        orderTasks.add(voucherOrder);
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
//        return Result.ok(orderId);
//    }
//    @Override
//
//    public Result secondKillVocher(Long voucherId) {
//        SeckillVoucher voucher=seckillVoucherService.getById(voucherId);
//
//        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
//            return Result.fail("秒杀未开始");
//        }
//        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
//            return Result.fail("秒杀已结束");
//        }
//
//        if(voucher.getStock()<1){
//            return Result.fail("库存不足");
//        }
//        Long userId= UserHolder.getUser().getId();
//        RLock lock =redissonClient.getLock("order:"+userId);
//
//        // new SimpleRedisLock("order:"+userId,stringRedisTemplate);
//        boolean isLock=lock.tryLock();
//        if(!isLock){
//            return Result.fail("已经购买过");
//        }
//        try{
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }finally {
//            lock.unlock();
//        }
//    }
    @Transactional
    @Override
    public void createVoucherOrder(VoucherOrder voucherOrder){
        Long userId= UserHolder.getUser().getId();

        long count=query().eq("user_id",userId).eq("voucher_id",voucherOrder).count();
        if (count > 0) {
            log.error("已经购买过");
        }
        boolean success=seckillVoucherService.update().setSql("stock=stock-1")
                .eq("voucher_id",voucherOrder).gt("stock",0).update();
        if(!success){
            log.error("库存不足");
        }

        save(voucherOrder);

    }
}
