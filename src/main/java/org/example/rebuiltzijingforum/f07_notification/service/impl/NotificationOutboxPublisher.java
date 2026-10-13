package org.example.rebuiltzijingforum.f07_notification.service.impl;

import org.example.rebuiltzijingforum.f07_notification.config.RabbitMqConfig;
import org.example.rebuiltzijingforum.f07_notification.mapper.NotificationOutboxMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/** 提交后的独立发布器；失败保留 Outbox 行，下一次定时扫描重试。 */
@Service
@ConditionalOnProperty(name = "forum.rabbit.enabled", havingValue = "true")
public class NotificationOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxPublisher.class);
    private final NotificationOutboxMapper outbox;
    private final RabbitTemplate rabbit;

    public NotificationOutboxPublisher(NotificationOutboxMapper outbox, RabbitTemplate rabbit) {
        this.outbox = outbox; this.rabbit = rabbit;
    }

    /** publisher confirm 是 broker 收到消息的证据；不能只因为 convertAndSend 没抛异常就删掉 Outbox。 */
    @Scheduled(fixedDelayString = "${forum.rabbit.publish-delay-ms:1000}")
    public void publishPending() {
        for (NotificationOutboxMapper.PendingEvent event : outbox.pending(100)) {
            try {
                CorrelationData confirmation = new CorrelationData(Long.toString(event.id()));
                rabbit.convertAndSend(RabbitMqConfig.EXCHANGE, "created", event.payload(), confirmation);
                CorrelationData.Confirm confirm = confirmation.getFuture().get(5, TimeUnit.SECONDS);
                if (!confirm.isAck()) throw new IllegalStateException("RabbitMQ NACK: " + confirm.getReason());
                outbox.markPublished(event.id());
            } catch (Exception failed) {
                log.warn("通知 Outbox {} 暂未发出，保留待重试", event.id(), failed);
                break;
            }
        }
    }
}
