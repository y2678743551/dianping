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
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
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


    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static{
        SECKILL_SCRIPT=new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
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
        Long userId= voucherOrder.getUserId();

        long count=query().eq("user_id",userId).eq("voucher_id",voucherOrder.getVoucherId()).count();
        if (count > 0) {
            log.error("已经购买过");
            return;
        }

        boolean success=seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        if(!success){
            log.error("库存不足");
            return;
        }

        save(voucherOrder);

    }
}
