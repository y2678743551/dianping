package com.app.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Slf4j
@Configuration
public class RabbitMQConfig {

    /** 下单交换机 */
    public static final String ORDER_EXCHANGE = "seckill.order.exchange";
    /** 下单队列 */
    public static final String ORDER_QUEUE = "seckill.order.queue";
    /** 下单路由键 */
    public static final String ORDER_ROUTING_KEY = "seckill.order";

    /** 死信交换机 */
    public static final String DLX_EXCHANGE = "seckill.order.dlx.exchange";
    /** 死信队列 */
    public static final String DLX_QUEUE = "seckill.order.dlx.queue";
    /** 死信路由键 */
    public static final String DLX_ROUTING_KEY = "seckill.order.dlx";

    @Bean
    public DirectExchange orderExchange() {
        return ExchangeBuilder.directExchange(ORDER_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue orderQueue() {
        return QueueBuilder.durable(ORDER_QUEUE)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding orderBinding() {
        return BindingBuilder.bind(orderQueue()).to(orderExchange()).with(ORDER_ROUTING_KEY);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return ExchangeBuilder.directExchange(DLX_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue dlxQueue() {
        return QueueBuilder.durable(DLX_QUEUE).build();
    }

    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(dlxQueue()).to(dlxExchange()).with(DLX_ROUTING_KEY);
    }


    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }


    @Bean
    public RabbitTemplate.ConfirmCallback rabbitConfirmCallback() {
        return (correlationData, ack, cause) -> {
            if (!ack) {
                log.error("下单消息未被 broker 确认: correlationData={}, cause={}", correlationData, cause);
            }
        };
    }
}
