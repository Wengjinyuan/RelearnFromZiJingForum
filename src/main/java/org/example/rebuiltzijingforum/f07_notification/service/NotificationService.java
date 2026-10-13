package org.example.rebuiltzijingforum.f07_notification.service;

import org.example.rebuiltzijingforum.common.api.PageResponse;
import org.example.rebuiltzijingforum.f07_notification.dto.NotificationPageDTO;
import org.example.rebuiltzijingforum.f07_notification.vo.NotificationVO;
import org.example.rebuiltzijingforum.f07_notification.vo.UnreadCountVO;

/** F07 业务接口；F04/F05/F06/F10 只依赖此契约生成通知。 */
public interface NotificationService {
    /** 业务动作产生通知；自己给自己不通知，相同 eventKey 只插一次。 */
    void emit(long recipient, long actor, String kind, Long target, String eventKey, String content);
    /** MQ 消费者落库入口：只做幂等保存，不能再次发 MQ，否则会形成无限循环。 */
    void persistEvent(org.example.rebuiltzijingforum.f07_notification.dto.NotificationEventDTO event);
    /** 查询当前用户的未读数。 */
    UnreadCountVO unread(long userId);
    /** 按类别和页码读取当前用户通知。 */
    PageResponse<NotificationVO> list(long userId, NotificationPageDTO query);
    /** 只标记当前用户拥有的一条通知。 */
    void read(long userId, long id);
    /** 当前用户全部标已读，返回更新行数。 */
    int readAll(long userId);
}
