package org.example.rebuiltzijingforum.f07_notification.mapper;

import org.example.rebuiltzijingforum.common.db.DbIds;
import org.example.rebuiltzijingforum.f07_notification.entity.NotificationEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/** 通知数据层：user_id 必须出现在读、已读和未读计数的 WHERE 中。 */
@Repository
public class NotificationMapper {
    private final JdbcTemplate jdbc;

    /** Spring 注入 JdbcTemplate。 */
    public NotificationMapper(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** event_key 唯一约束是重复事件的最后防线；返回数据库生成的通知 id。 */
    public long insert(long recipient, long actor, String kind, Long target, String eventKey, String content, LocalDateTime now) {
        return DbIds.insert(jdbc, "INSERT INTO notification(user_id,actor_id,kind,target_id,event_key,content,created_at) VALUES(?,?,?,?,?,?,?)",
                recipient, actor, kind, target, eventKey, content, now);
    }

    /** 查询事件是否已经落库，Service 用于避免重复创建。 */
    public boolean hasEvent(String eventKey) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE event_key=?", Long.class, eventKey) > 0;
    }

    /** COUNT 只统计当前 user_id 的未读行；不会泄露其他账号的红点数量。 */
    public long unread(long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE user_id=? AND is_read=FALSE", Long.class, userId);
    }

    /** 列表同时按 user_id、可选 kind 过滤；ORDER BY id DESC 保证新通知先展示。 */
    public List<NotificationEntity> list(long userId, String kind, int size, int offset) {
        if (kind == null || kind.isBlank())
            return jdbc.query("SELECT * FROM notification WHERE user_id=? ORDER BY id DESC LIMIT ? OFFSET ?", this::map, userId, size, offset);
        return jdbc.query("SELECT * FROM notification WHERE user_id=? AND kind=? ORDER BY id DESC LIMIT ? OFFSET ?", this::map, userId, kind, size, offset);
    }

    /** 分页总数必须与列表的 kind 条件一致。 */
    public long count(long userId, String kind) {
        if (kind == null || kind.isBlank()) return jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE user_id=?", Long.class, userId);
        return jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE user_id=? AND kind=?", Long.class, userId, kind);
    }

    /** WHERE id=? AND user_id=? 同时锁定通知和主人；猜中别人 id 也改不了。 */
    public int markRead(long id, long userId) {
        return jdbc.update("UPDATE notification SET is_read=TRUE WHERE id=? AND user_id=?", id, userId);
    }

    /** 只批量修改当前用户的通知，其他用户的未读状态保持不变。 */
    public int readAll(long userId) {
        return jdbc.update("UPDATE notification SET is_read=TRUE WHERE user_id=? AND is_read=FALSE", userId);
    }

    /** target_id 指向 private_message.id，据此只清当前会话对应的私信通知。 */
    public int readMessagesForConversation(long userId, long conversationId) {
        return jdbc.update("""
                UPDATE notification SET is_read=TRUE
                WHERE user_id=? AND kind='MESSAGE' AND is_read=FALSE
                  AND target_id IN (SELECT id FROM private_message WHERE conversation_id=?)
                """, userId, conversationId);
    }

    /** ResultSet 映射为数据库行。 */
    private NotificationEntity map(ResultSet rs, int number) throws SQLException {
        return new NotificationEntity(rs.getLong("id"), rs.getLong("user_id"), rs.getLong("actor_id"), rs.getString("kind"),
                rs.getObject("target_id", Long.class), rs.getString("content"), rs.getBoolean("is_read"),
                rs.getTimestamp("created_at").toLocalDateTime());
    }

}
