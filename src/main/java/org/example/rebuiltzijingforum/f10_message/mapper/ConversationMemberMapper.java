package org.example.rebuiltzijingforum.f10_message.mapper;

import org.example.rebuiltzijingforum.f10_message.entity.ConversationMemberEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** 专门访问 conversation_member；授权与未读数都按用户分开。 */
@Repository
public class ConversationMemberMapper {
    private final JdbcTemplate db;
    /** Spring 注入 SQL 执行器。 */
    public ConversationMemberMapper(JdbcTemplate db) { this.db = db; }

    /** 创建会话后给双方各插一行，未读从 0 开始。 */
    public void insert(long conversationId, long userId) {
        db.update("INSERT INTO conversation_member(conversation_id,user_id,unread_count) VALUES(?,?,0)", conversationId, userId);
    }

    /** 查成员行；Service.findMember 用它挡住猜 ID 访问他人会话。 */
    public Optional<ConversationMemberEntity> find(long conversationId, long userId) {
        return db.query("SELECT conversation_id,user_id,unread_count,last_read_message_id FROM conversation_member WHERE conversation_id=? AND user_id=?",
                (rs, i) -> new ConversationMemberEntity(rs.getLong(1), rs.getLong(2), rs.getInt(3), rs.getObject(4, Long.class)),
                conversationId, userId).stream().findFirst();
    }

    /** 只给收件人未读 +1，发件人行不变。 */
    public void incrementUnread(long conversationId, long recipient) {
        db.update("UPDATE conversation_member SET unread_count=unread_count+1 WHERE conversation_id=? AND user_id=?", conversationId, recipient);
    }

    /** 当前用户打开会话：清自己的未读并记最后已读消息，绝不修改对方行。 */
    public void markRead(long conversationId, long userId) {
        db.update("""
                UPDATE conversation_member SET unread_count=0,
                    last_read_message_id=(SELECT last_message_id FROM conversation WHERE id=?)
                WHERE conversation_id=? AND user_id=?
                """, conversationId, conversationId, userId);
    }
}
