package org.example.rebuiltzijingforum.f10_message.mapper;

import org.example.rebuiltzijingforum.common.db.DbIds;
import org.example.rebuiltzijingforum.f10_message.entity.ConversationEntity;
import org.example.rebuiltzijingforum.f10_message.vo.ConversationVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 专门访问 conversation；一个 pairKey 只对应一条双人会话。 */
@Repository
public class ConversationMapper {
    private final JdbcTemplate db;
    /** Spring 注入 SQL 执行器。 */
    public ConversationMapper(JdbcTemplate db) { this.db = db; }

    /** 发信前确认收件人存在且未封禁。 */
    public boolean activeUser(long id) {
        return db.queryForObject("SELECT COUNT(*) FROM user_account WHERE id=? AND status<>'BANNED'", Long.class, id) > 0;
    }

    /** SELECT pair_key：无论 A 发 B 还是 B 发 A，Service 算出相同的键。 */
    public Optional<ConversationEntity> findByPair(String pairKey) {
        return db.query("SELECT id,pair_key,last_message_id,last_message_at,created_at FROM conversation WHERE pair_key=?",
                (rs, i) -> new ConversationEntity(rs.getLong(1), rs.getString(2), rs.getObject(3, Long.class),
                        rs.getTimestamp(4) == null ? null : rs.getTimestamp(4).toLocalDateTime(),
                        rs.getTimestamp(5).toLocalDateTime()), pairKey).stream().findFirst();
    }

    /** 没有旧会话时 INSERT；唯一约束阻止两条相同 pairKey。 */
    public long insert(String pairKey, LocalDateTime now) {
        return DbIds.insert(db, "INSERT INTO conversation(pair_key,created_at) VALUES(?,?)", pairKey, now);
    }

    /** 新消息写成功后，更新会话列表排序依据和最后消息 ID。 */
    public void touch(long conversationId, long messageId, LocalDateTime now) {
        db.update("""
                UPDATE conversation SET
                  last_message_at=CASE WHEN last_message_id IS NULL OR last_message_id<? THEN ? ELSE last_message_at END,
                  last_message_id=CASE WHEN last_message_id IS NULL OR last_message_id<? THEN ? ELSE last_message_id END
                WHERE id=?
                """, messageId, now, messageId, messageId, conversationId);
    }

    /** 查询当前用户参与的会话；JOIN 成员/对方账号/最后消息组成卡片。 */
    public List<ConversationVO> listFor(long userId) {
        return db.query("""
                SELECT c.id,other.user_id,u.account,mine.unread_count,c.last_message_id,c.last_message_at,m.content AS last_content
                FROM conversation_member mine
                JOIN conversation c ON c.id=mine.conversation_id
                JOIN conversation_member other ON other.conversation_id=c.id AND other.user_id<>mine.user_id
                JOIN user_account u ON u.id=other.user_id
                LEFT JOIN private_message m ON m.id=c.last_message_id
                WHERE mine.user_id=? ORDER BY c.last_message_at DESC,c.id DESC
                """, (rs, i) -> new ConversationVO(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getInt(4),
                rs.getObject(5, Long.class), rs.getString(7), rs.getTimestamp(6) == null ? null : rs.getTimestamp(6).toLocalDateTime()), userId);
    }
}
