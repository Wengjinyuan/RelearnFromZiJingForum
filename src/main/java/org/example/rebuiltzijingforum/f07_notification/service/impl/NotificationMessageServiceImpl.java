package org.example.rebuiltzijingforum.f07_notification.service.impl;

import org.example.rebuiltzijingforum.f07_notification.dto.NotificationEventDTO;
import org.example.rebuiltzijingforum.f07_notification.service.NotificationMessageService;
import org.example.rebuiltzijingforum.f07_notification.service.NotificationService;
import org.springframework.stereotype.Service;

/** F07 事件处理实现：无论同步调用还是将来 MQ 重投，都复用同一去重逻辑。 */
@Service
public class NotificationMessageServiceImpl implements NotificationMessageService {
    private final NotificationService notifications;

    /** 注入核心通知业务接口，不绕过业务层直接写 Mapper。 */
    public NotificationMessageServiceImpl(NotificationService notifications) { this.notifications = notifications; }

    /** 收到事件 → NotificationService.persistEvent 幂等写通知；不能再调用 emit 形成 MQ 循环。 */
    @Override
    public void handle(NotificationEventDTO event) {
        notifications.persistEvent(event);
    }
}
