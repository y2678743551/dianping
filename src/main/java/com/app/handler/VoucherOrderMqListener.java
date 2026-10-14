package com.app.handler;

import com.app.config.RabbitMQConfig;
import com.app.entity.VoucherOrder;
import com.app.service.IVoucherOrderService;
import com.rabbitmq.client.Channel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 秒杀下单消息消费者，替代原来的 Redis Stream 消费线程（VoucherOrderHandler）。
 * 手动 ack：处理成功才 ack；抛异常则 nack 且不重回队列，靠死信队列兜住，
 * 避免 createVoucherOrder 里已经扣过库存的失败场景被无限重投。
 */
@Slf4j
@Component
public class VoucherOrderMqListener {

    @Resource
    private IVoucherOrderService voucherOrderService;
    @Resource
    private RedissonClient redissonClient;

    @RabbitListener(queues = RabbitMQConfig.ORDER_QUEUE)
    public void onMessage(VoucherOrder voucherOrder, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        Long userId = voucherOrder.getUserId();
        log.debug("消费到下单消息: orderId={}, userId={}, voucherId={}",
                voucherOrder.getId(), userId, voucherOrder.getVoucherId());

        RLock lock = redissonClient.getLock("order:" + userId);
        if (!lock.tryLock()) {

            log.warn("未抢到下单锁，丢弃消息: orderId={}, userId={}", voucherOrder.getId(), userId);
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            voucherOrderService.createVoucherOrder(voucherOrder);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("下单处理失败，消息进入死信队列: orderId={}, userId={}, voucherId={}",
                    voucherOrder.getId(), userId, voucherOrder.getVoucherId(), e);
            channel.basicNack(deliveryTag, false, false);
        } finally {
            lock.unlock();
        }
    }
}
