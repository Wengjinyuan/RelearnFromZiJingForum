package org.example.rebuiltzijingforum.f05_interaction.mapper;

import org.example.rebuiltzijingforum.f05_interaction.entity.FavoriteEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** 专门操作 favorite；与点赞表分开，读 GUIDE 时能一眼定位 SQL。 */
@Repository
public class FavoriteMapper {
    private final JdbcTemplate db;
    /** Spring 注入 JDBC。 */
    public FavoriteMapper(JdbcTemplate db) { this.db = db; }
    /** 按组合唯一键查当前收藏关系。 */
    public Optional<FavoriteEntity> find(long userId, long postId) {
        return db.query("SELECT user_id,post_id FROM favorite WHERE user_id=? AND post_id=?",
                (rs, i) -> new FavoriteEntity(rs.getLong(1), rs.getLong(2)), userId, postId).stream().findFirst();
    }
    /** 第一次收藏 INSERT，数据库唯一键防止并发重复。 */
    public int insert(FavoriteEntity row) {
        return db.update("INSERT INTO favorite(user_id,post_id) VALUES(?,?)", row.userId(), row.postId());
    }
    /** 删除当前用户对当前帖的关系；不存在时返回 0。 */
    public int delete(long userId, long postId) {
        return db.update("DELETE FROM favorite WHERE user_id=? AND post_id=?", userId, postId);
    }
}
