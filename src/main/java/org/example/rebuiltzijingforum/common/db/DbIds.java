package org.example.rebuiltzijingforum.common.db;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;

/** JDBC 插入辅助：执行 INSERT，并取得数据库生成的自增 id。 */
public final class DbIds {
    private DbIds() {}

    /** Repository 调用本方法；SQL 的每个 ? 按 params 顺序绑定，返回新行的主键。 */
    public static long insert(JdbcTemplate jdbc, String sql, Object... params) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < params.length; index++) {
                statement.setObject(index + 1, params[index]);
            }
            return statement;
        }, keys);
        if (keys.getKey() == null) {
            throw new IllegalStateException("数据库未返回新行的 id");
        }
        return keys.getKey().longValue();
    }
}
