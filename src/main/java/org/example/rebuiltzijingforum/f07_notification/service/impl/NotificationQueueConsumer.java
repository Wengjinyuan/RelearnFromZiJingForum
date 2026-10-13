package org.example.rebuiltzijingforum.f07_notification.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.rebuiltzijingforum.f07_notification.config.RabbitMqConfig;
import org.example.rebuiltzijingforum.f07_notification.dto.NotificationEventDTO;
import org.example.rebuiltzijingforum.f07_notification.service.NotificationMessageService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** MQ 版入口：JSON 事件 -> 消息业务 -> NotificationService.persistEvent -> notification 表。 */
@Service
@ConditionalOnProperty(name = "forum.rabbit.enabled", havingValue = "true")
public class NotificationQueueConsumer {
    private final ObjectMapper json;
    private final NotificationMessageService messages;

    public NotificationQueueConsumer(ObjectMapper json, NotificationMessageService messages) {
        this.json = json; this.messages = messages;
    }

    /** 抛异常时交由容器有限重试，最终进死信队列；成功返回才 ACK。 */
    @RabbitListener(queues = RabbitMqConfig.MAIN_QUEUE)
    public void onMessage(String payload) throws Exception {
        messages.handle(json.readValue(payload, NotificationEventDTO.class));
    }
}
