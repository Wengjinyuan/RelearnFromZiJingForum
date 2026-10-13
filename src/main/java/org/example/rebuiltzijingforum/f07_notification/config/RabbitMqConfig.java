package org.example.rebuiltzijingforum.f07_notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;

/** 仅 forum.rabbit.enabled=true 时声明持久化主队列与死信队列；默认 H2 教学版不连 RabbitMQ。 */
@Configuration
@EnableRabbit
@ConditionalOnProperty(name = "forum.rabbit.enabled", havingValue = "true")
public class RabbitMqConfig {
    public static final String EXCHANGE = "forum.notification.x";
    public static final String MAIN_QUEUE = "forum.notification.q";
    public static final String DEAD_EXCHANGE = "forum.notification.dead.x";
    public static final String DEAD_QUEUE = "forum.notification.dead.q";

    /** 生产者把通知事件发到持久化直连交换机，routingKey=created。 */
    @Bean
    public DirectExchange notificationExchange() { return new DirectExchange(EXCHANGE, true, false); }
    /** 主队列处理失败后进入独立死信交换机。 */
    @Bean
    public DirectExchange notificationDeadExchange() { return new DirectExchange(DEAD_EXCHANGE, true, false); }
    /** 主队列 durable；死信参数告诉 RabbitMQ 失败消息往哪里转。 */
    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(MAIN_QUEUE).deadLetterExchange(DEAD_EXCHANGE).deadLetterRoutingKey("dead").build();
    }
    /** 死信队列也持久化，供管理员排查不可处理的事件。 */
    @Bean
    public Queue notificationDeadQueue() { return QueueBuilder.durable(DEAD_QUEUE).build(); }
    /** created 消息路由到主队列。 */
    @Bean
    public Binding notificationBinding() { return BindingBuilder.bind(notificationQueue()).to(notificationExchange()).with("created"); }
    /** dead 消息路由到死信队列。 */
    @Bean
    public Binding notificationDeadBinding() { return BindingBuilder.bind(notificationDeadQueue()).to(notificationDeadExchange()).with("dead"); }
}
