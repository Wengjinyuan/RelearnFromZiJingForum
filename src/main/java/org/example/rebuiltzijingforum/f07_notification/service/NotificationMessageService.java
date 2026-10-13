package org.example.rebuiltzijingforum.f07_notification.service;

import org.example.rebuiltzijingforum.f07_notification.dto.NotificationEventDTO;

/** 异步事件入口：消息处理者不直接写 SQL，而是调用 NotificationService。 */
public interface NotificationMessageService {
    /** 收到事件后按 eventKey 幂等处理；失败由消息基础设施重试。 */
    void handle(NotificationEventDTO event);
}
