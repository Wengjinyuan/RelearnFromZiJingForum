package org.example.rebuiltzijingforum.f10_message.mapper;

import org.example.rebuiltzijingforum.common.db.DbIds;
import org.example.rebuiltzijingforum.f10_message.entity.PrivateMessageEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 专门访问 private_message；消息历史只信数据库，不依赖实时连接。 */
@Repository
public class PrivateMessageMapper {
    private final JdbcTemplate db;
    /** Spring 注入 SQL 执行器。 */
    public PrivateMessageMapper(JdbcTemplate db) { this.db = db; }

    /** 网络重试先按发送人 + clientMessageId 查旧消息。 */
    public Optional<PrivateMessageEntity> findByClientId(long senderId, String clientId) {
        return db.query("SELECT id,conversation_id,sender_id,client_message_id,message_type,content,created_at FROM private_message WHERE sender_id=? AND client_message_id=?",
                this::map, senderId, clientId).stream().findFirst();
    }

    /** 第一次发送 INSERT，数据库组合唯一键保证不会出现重复消息。 */
    public long insert(long conversationId, long senderId, String clientId, String type, String content, LocalDateTime now) {
        return DbIds.insert(db, "INSERT INTO private_message(conversation_id,sender_id,client_message_id,message_type,content,created_at) VALUES(?,?,?,?,?,?)",
                conversationId, senderId, clientId, type, content, now);
    }

    /** id<beforeId 只拿更早消息，ORDER BY DESC 保证游标翻页稳定。 */
    public List<PrivateMessageEntity> history(long conversationId, long beforeId, int size) {
        return db.query("SELECT id,conversation_id,sender_id,client_message_id,message_type,content,created_at FROM private_message " +
                "WHERE conversation_id=? AND id<? ORDER BY id DESC LIMIT ?", this::map, conversationId, beforeId, size);
    }

    /** ResultSet 的一行映射为 Entity，不在这里决定用户是否有权限读。 */
    private PrivateMessageEntity map(ResultSet rs, int ignored) throws SQLException {
        return new PrivateMessageEntity(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getTimestamp(7).toLocalDateTime());
    }
}
