package org.example.rebuiltzijingforum.f10_message.service;

import org.example.rebuiltzijingforum.f10_message.dto.SendMessageDTO;
import org.example.rebuiltzijingforum.f10_message.dto.MessagePageDTO;
import org.example.rebuiltzijingforum.f10_message.vo.ConversationVO;
import org.example.rebuiltzijingforum.f10_message.vo.MessageVO;

import java.util.List;

/** F10 业务契约：接口约定输入输出；具体数据库事务在 MessageServiceImpl。 */
public interface MessageService {
    /** 发信：找/建会话、写消息、更新未读。 */
    MessageVO send(long sender, long recipient, SendMessageDTO input);
    /** 只列出当前用户参与的会话。 */
    List<ConversationVO> conversations(long userId);
    /** 先检查成员资格，再按游标查消息。 */
    List<MessageVO> history(long userId, long conversationId, MessagePageDTO query);
    /** 当前用户打开会话后清自己的未读。 */
    void read(long userId, long conversationId);
}
