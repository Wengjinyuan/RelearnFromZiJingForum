package org.example.rebuiltzijingforum.f07_notification.mapper;

import org.example.rebuiltzijingforum.common.db.DbIds;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/** Outbox 数据层：业务事务提交后才会被定时发布器读到。 */
@Repository
public class NotificationOutboxMapper {
    private final JdbcTemplate jdbc;

    /** Spring 注入 JDBC；本类只负责 SQL，不决定事件该不该通知自己。 */
    public NotificationOutboxMapper(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 与发帖/评论等业务共用事务写入；event_key 唯一键挡住重复业务事件。 */
    public void insert(String eventKey, String payload, LocalDateTime now) {
        DbIds.insert(jdbc, "INSERT INTO notification_outbox(event_key,payload,created_at) VALUES(?,?,?)", eventKey, payload, now);
    }

    /** 写前先查同一个 eventKey；唯一约束仍是并发重复的最终防线。 */
    public boolean hasEvent(String eventKey) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE event_key=?", Long.class, eventKey) > 0;
    }

    /** 仅挑未发布行；宕机后这些行仍在 MySQL，可在下一轮继续发送。 */
    public List<PendingEvent> pending(int size) {
        return jdbc.query("SELECT id,payload FROM notification_outbox WHERE published=FALSE ORDER BY id LIMIT ?",
                (rs, n) -> new PendingEvent(rs.getLong(1), rs.getString(2)), size);
    }

    /** 收到 broker confirm 后标记；崩溃于确认和标记之间时会重投，消费者必须幂等。 */
    public void markPublished(long id) {
        jdbc.update("UPDATE notification_outbox SET published=TRUE WHERE id=? AND published=FALSE", id);
    }

    public record PendingEvent(long id, String payload) {}
}
